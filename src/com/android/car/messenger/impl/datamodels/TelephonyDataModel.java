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

import static com.android.car.messenger.core.shared.MessageConstants.KEY_MUTED_CONVERSATIONS;

import static java.util.Comparator.comparingLong;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.interfaces.DataModel;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.core.util.ConversationUtil;
import com.android.car.messenger.core.util.L;
import com.android.car.messenger.impl.common.ProjectionStateListener;
import com.android.car.messenger.impl.datamodels.ConversationItemLiveData.ConversationChangeSet;
import com.android.car.messenger.impl.datamodels.ConversationItemLiveData.NotifyLevel;
import com.android.car.messenger.impl.datamodels.ConversationsPerDeviceLiveData.ConversationIdChangeList;
import com.android.car.messenger.impl.datamodels.UserAccountLiveData.UserAccountChangeList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Queries the telephony data model to retrieve the SMS/MMS messages */
public class TelephonyDataModel implements DataModel {

    @NonNull
    private final HashMap<String, LiveData<ConversationChangeSet>> mConvoIdToConversationLiveData =
            new HashMap<>();

    @NonNull
    private final HashMap<Integer, ConversationsPerDeviceLiveData>
            mAccountIdToConversationListLiveData = new HashMap<>();

    @NonNull private final HashMap<String, Conversation> mConversationMap = new HashMap<>();

    @NonNull
    private static final Comparator<Conversation> sConversationComparator =
            comparingLong(ConversationUtil::getConversationTimestamp).reversed();

    @NonNull
    ProjectionStateListener mProjectionStateListener =
            new ProjectionStateListener(AppFactory.get().getContext());

    @NonNull
    @Override
    public LiveData<Collection<UserAccount>> getAccounts() {
        return Transformations.map(
                UserAccountLiveData.getInstance(), UserAccountChangeList::getAccounts);
    }

    @NonNull
    @Override
    public LiveData<Collection<Conversation>> getConversations(@NonNull UserAccount userAccount) {
        MediatorLiveData<Collection<Conversation>> liveData = new MediatorLiveData<>();
        subscribeToConversationItemChanges(
                userAccount,
                liveData,
                /* onConversationItemChanged= */ conversationChangeSet -> {
                    mConversationMap.put(
                            conversationChangeSet.getConversation().getId(),
                            conversationChangeSet.getConversation());
                    liveData.postValue(
                            mConversationMap.values().stream()
                                    .sorted(sConversationComparator)
                                    .collect(Collectors.toList()));
                },
                /* onConversationRemoved= */ conversationId -> {
                    mConversationMap.remove(conversationId);
                    liveData.postValue(
                            mConversationMap.values().stream()
                                    .sorted(sConversationComparator)
                                    .collect(Collectors.toList()));
                },
                /* onEmpty= */ onEmpty -> liveData.postValue(new ArrayList<>()));

        return liveData;
    }

    @NonNull
    @Override
    public LiveData<Conversation> getUnreadMessages() {
        MediatorLiveData<Conversation> liveData = new MediatorLiveData<>();
        subscribeToUserAccountConversationItem(
                liveData,
                conversationChangeSet -> {
                    UserAccount userAccount = conversationChangeSet.first;
                    ConversationChangeSet conversationItemChangeSet = conversationChangeSet.second;
                    Conversation conversation = conversationItemChangeSet.getConversation();
                    if (conversation.getUnreadCount() > 0
                            && conversationItemChangeSet.getChange()
                                    == NotifyLevel.NEW_OR_UPDATED_MESSAGE
                            && userAccount
                                    .getConnectionTime()
                                    .isBefore(
                                            Instant.ofEpochMilli(
                                                    ConversationUtil.getConversationTimestamp(
                                                            conversation)))) {
                        if (hasProjectionInForeground(userAccount)) {
                            L.d("Ignoring new message as projection is in foreground");
                            return;
                        }
                        liveData.postValue(conversation);
                    }
                });
        return liveData;
    }

    @Override
    public void muteConversation(@NonNull String conversationId, boolean mute) {
        SharedPreferences sharedPreferences = AppFactory.get().getSharedPreferences();
        Set<String> mutedConversations =
                sharedPreferences.getStringSet(KEY_MUTED_CONVERSATIONS, new HashSet<>());
        Set<String> finalSet = new HashSet<>(mutedConversations);
        if (mute) {
            finalSet.add(conversationId);
        } else {
            finalSet.remove(conversationId);
        }
        sharedPreferences.edit().putStringSet(KEY_MUTED_CONVERSATIONS, finalSet).apply();
    }

    @Override
    public void markAsRead(@NonNull String conversationId) {
        ConversationItemLiveData.markAsRead(conversationId);
    }

    @Override
    public void sendMessage(@NonNull String conversationId, @NonNull String message) {
        L.d("Sending a message to a conversation");
        String destination =
                Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, conversationId).toString();
        SmsManager.getDefault()
                .sendTextMessage(
                        destination,
                        /* scAddress= */ null,
                        message,
                        /* sentIntent= */ null,
                        /* deliveryIntent= */ null);
    }

    @Override
    public void sendMessage(int accountId, @NonNull String phoneNumber, @NonNull String message) {
        L.d("Sending a message to a phone number");
        SmsManager.getSmsManagerForSubscriptionId(accountId)
                .sendTextMessage(
                        phoneNumber,
                        /* scAddress= */ null,
                        message,
                        /* sentIntent= */ null,
                        /* deliveryIntent= */ null);
    }

    @Override
    public void sendMessage(
            @NonNull String iccId, @NonNull String phoneNumber, @NonNull String message) {
        UserAccount userAccount = UserAccountLiveData.getUserAccount(iccId);
        if (userAccount == null) {
            L.d("Could not find User Account with specified iccId. Unable to send message");
            return;
        }
        sendMessage(userAccount.getId(), phoneNumber, message);
    }

    /** Avoids crash when adding source */
    private <T, S> void safeAddSource(
            @NonNull MediatorLiveData<T> liveData,
            @NonNull LiveData<S> source,
            @NonNull Observer<? super S> onChanged) {
        try {
            liveData.addSource(source, onChanged);
        } catch (IllegalArgumentException e) {
            L.w("We are already subscribed to the source, ignoring.");
        }
    }

    private <T> void subscribeToUserAccountConversationItem(
            @NonNull MediatorLiveData<T> liveData,
            @NonNull
                    Observer<? super Pair<UserAccount, ConversationChangeSet>>
                            onConversationChanged) {
        safeAddSource(
                liveData,
                UserAccountLiveData.getInstance(),
                userAccountChangeList -> {
                    userAccountChangeList
                            .getAddedAccounts()
                            .forEach(
                                    userAccount ->
                                            subscribeToConversationItemChanges(
                                                    userAccount,
                                                    liveData,
                                                    it ->
                                                            onConversationChanged.onChanged(
                                                                    new Pair<>(userAccount, it)),
                                                    /* onConversationRemoved= */ null,
                                                    /* onEmpty= */ null));

                    userAccountChangeList
                            .getRemovedAccounts()
                            .forEach(
                                    userAccount ->
                                            liveData.removeSource(
                                                    getConversationIds(userAccount.getId())));
                });
    }

    private <T> void subscribeToConversationItemChanges(
            @NonNull UserAccount userAccount,
            @NonNull MediatorLiveData<T> liveData,
            @NonNull Observer<? super ConversationChangeSet> onConversationItemChanged,
            @Nullable Observer<String> onConversationRemoved,
            @Nullable Observer<Boolean> onEmpty) {
        safeAddSource(
                liveData,
                getConversationIds(userAccount.getId()),
                conversationIdChangeList ->
                        subscribeToConversationItemChanges(
                                conversationIdChangeList,
                                liveData,
                                onConversationItemChanged,
                                onConversationRemoved,
                                onEmpty));
    }

    private <T> void subscribeToConversationItemChanges(
            @NonNull ConversationIdChangeList conversationIdChangeList,
            @NonNull MediatorLiveData<T> liveData,
            @NonNull Observer<? super ConversationChangeSet> onConversationItemChanged,
            @Nullable Observer<String> onConversationRemoved,
            @Nullable Observer<Boolean> onEmpty) {
        conversationIdChangeList
                .getAddedConversationIds()
                .forEach(
                        conversationId ->
                                safeAddSource(
                                        liveData,
                                        getConversationItem(conversationId),
                                        it -> {
                                            if (it == null) {
                                                if (onConversationRemoved != null) {
                                                    onConversationRemoved.onChanged(conversationId);
                                                }
                                                return;
                                            }
                                            onConversationItemChanged.onChanged(it);
                                        }));

        conversationIdChangeList
                .getRemovedConversationIds()
                .forEach(
                        conversationId -> {
                            liveData.removeSource(getConversationItem(conversationId));
                            if (onConversationRemoved != null) {
                                onConversationRemoved.onChanged(conversationId);
                            }
                        });

        if (conversationIdChangeList.getAllConversationIds().isEmpty()) {
            L.d("No conversation lists found for user account.");
            if (onEmpty != null) {
                onEmpty.onChanged(true);
            }
        }
    }

    private boolean hasProjectionInForeground(@NonNull UserAccount userAccount) {
        return mProjectionStateListener.isProjectionInActiveForeground(userAccount.getIccId());
    }

    @NonNull
    private ConversationsPerDeviceLiveData getConversationIds(int accountId) {
        return mAccountIdToConversationListLiveData.computeIfAbsent(
                accountId, it -> new ConversationsPerDeviceLiveData(accountId));
    }

    @NonNull
    private LiveData<ConversationChangeSet> getConversationItem(@NonNull String conversationId) {
        return mConvoIdToConversationLiveData.computeIfAbsent(
                conversationId, it -> new ConversationItemLiveData(conversationId));
    }
}
