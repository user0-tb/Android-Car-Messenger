/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.messenger.impl.datamodels;

import static android.provider.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
import static android.provider.Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID;
import static android.provider.Telephony.TextBasedSmsColumns.THREAD_ID;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SubscriptionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.util.L;
import com.android.car.messenger.impl.datamodels.ConversationsPerDeviceLiveData.ConversationIdChangeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Returns {@link ConversationIdChangeList}, which holds the Conversation Ids Per SIM */
class ConversationsPerDeviceLiveData extends ContentProviderLiveData<ConversationIdChangeList> {
    @NonNull private static final Uri URI = CONTENT_CONVERSATIONS_URI;

    @NonNull
    private static final String[] PROJECTION = {
        SUBSCRIPTION_ID, THREAD_ID,
    };

    @NonNull private final ContentResolver mContentResolver;
    private final int mAccountId;

    ConversationsPerDeviceLiveData(int accountId) {
        super(URI);
        mContentResolver = AppFactory.get().getContext().getContentResolver();
        mAccountId = accountId;
    }

    @Nullable
    private Cursor getCursor() {
        return mContentResolver.query(
                URI,
                PROJECTION,
                /* selection= */ SUBSCRIPTION_ID + "=" + mAccountId,
                /* selectionArgs= */ null,
                /* sortOrder= */ null,
                /* cancellationSignal= */ null);
    }

    @Override
    protected void onActive() {
        super.onActive();
        if (getValue() == null) {
            onDataChange();
        }
    }

    @Override
    public void onDataChange() {
        Cursor cursor = getCursor();
        if (cursor == null || !cursor.moveToFirst()) {
            L.d("No conversation data found. Setting LiveData to null");
            postValue(new ConversationIdChangeList());
            return;
        }

        ArrayList<String> currentConversationIds = new ArrayList<>();
        do {
            String conversationId = cursor.getString(cursor.getColumnIndex(THREAD_ID));
            currentConversationIds.add(conversationId);
        } while (cursor.moveToNext());

        // get updated changes
        Collection<String> prevConversationIds = getValueOrEmpty().getAllConversationIds();
        Set<String> newConversations = getDifference(currentConversationIds, prevConversationIds);
        Set<String> removedConversations =
                getDifference(prevConversationIds, currentConversationIds);

        if (newConversations.isEmpty() && removedConversations.isEmpty()) {
            // Return early if no new conversations were added or removed since last change list.
            // However, if no conversations is found, post an empty changelist to allow
            // the subscriber update the UI with "no new conversations found"
            if (currentConversationIds.isEmpty()) {
                postValue(new ConversationIdChangeList());
            }
            return;
        }

        ConversationIdChangeList changeList = new ConversationIdChangeList();
        changeList.mConversationIds = currentConversationIds;
        changeList.mAddedConversationIds = newConversations;
        changeList.mRemovedConversationIds = removedConversations;

        postValue(changeList);
    }

    @NonNull
    private ConversationIdChangeList getValueOrEmpty() {
        ConversationIdChangeList changeList = getValue();
        if (changeList == null) {
            changeList = new ConversationIdChangeList();
        }
        return changeList;
    }

    /**
     * Returns a set that contains a difference between the two lists - firstList - secondList =
     * result
     *
     * <p>This essentially points out which items or changes are not present in firstList.
     */
    @NonNull
    private static Set<String> getDifference(
            @NonNull Collection<String> firstList, @NonNull Collection<String> secondList) {
        return firstList.stream()
                .filter(it -> !secondList.contains(it))
                .collect(Collectors.toSet());
    }

    /**
     * Holds the list of conversation ids per {@link SubscriptionInfo#getSubscriptionId()}
     * Additional information such as which specific conversation ids have changed is also provided.
     */
    public static class ConversationIdChangeList {
        @NonNull private Collection<String> mConversationIds = new ArrayList<>();
        @NonNull private Collection<String> mRemovedConversationIds = new ArrayList<>();
        @NonNull private Collection<String> mAddedConversationIds = new ArrayList<>();

        private ConversationIdChangeList() {}

        /* Returns the list of added conversation Ids */
        @NonNull
        public Collection<String> getAllConversationIds() {
            return mConversationIds;
        }

        /* Returns the list of added conversation Ids */
        @NonNull
        public Stream<String> getRemovedConversationIds() {
            return mRemovedConversationIds.stream();
        }

        /* Returns the list of removed conversation Ids */
        @NonNull
        public Stream<String> getAddedConversationIds() {
            return mAddedConversationIds.stream();
        }
    }
}
