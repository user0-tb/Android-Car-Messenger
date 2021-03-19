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

import static android.provider.Telephony.ThreadsColumns.READ;

import static java.util.stream.Collectors.joining;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import androidx.core.graphics.drawable.IconCompat;
import android.util.Pair;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.app.Person;

import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.common.Conversation.Message;
import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.shared.MessageConstants;
import com.android.car.messenger.core.util.ConversationUtil;
import com.android.car.messenger.core.util.L;
import com.android.car.messenger.impl.datamodels.ConversationItemLiveData.ConversationChangeSet;
import com.android.car.messenger.impl.datamodels.util.AvatarUtil;
import com.android.car.messenger.impl.datamodels.util.ContactUtils;
import com.android.car.messenger.impl.datamodels.util.CursorUtils;
import com.android.car.messenger.impl.datamodels.util.MessageUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Conversation Item Observer for listening for changes for the Conversation Item Metadata
 *
 * <p>Returns a Conversation object holding the most relevant metadata for the Conversation.
 *
 * <p>{@link Conversation#getMessages() holds the relevant messages to read out to the user,
 * in this priority:
 *     <ul>
 *         <li>Unread messages
 *         <li>Read messages after last reply
 * </ul>
 * <p>Given that the Content observer notifies for each cahgne to the telephony database,
 * for optimization reasons, database search is limited number to
 * the {@link ConversationItemLiveData#ITEM_LIMIT}.
 */
class ConversationItemLiveData extends ContentProviderLiveData<ConversationChangeSet> {
    @NonNull private final String mConversationId;
    private static final int ITEM_LIMIT = 10;
    @NonNull private static final String COMMA_SEPARATOR = ", ";

    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            (sharedPreferences, key) -> onSharedPreferenceChangedInternal(key);

    /** Constructor that takes in a conversation id */
    ConversationItemLiveData(@NonNull String conversationId) {
        super(Sms.CONTENT_URI, Mms.CONTENT_URI, MmsSms.CONTENT_URI);
        mConversationId = conversationId;
    }

    @Override
    protected void onActive() {
        super.onActive();
        SharedPreferences sharedPrefs = AppFactory.get().getSharedPreferences();
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        if (getValue() == null) {
            onDataChange();
        }
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        SharedPreferences sharedPrefs = AppFactory.get().getSharedPreferences();
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    // Handles on Data Change
    @Override
    public void onDataChange() {
        try {
            onDataChangeInternal();
        } catch (CursorIndexOutOfBoundsException e) {
            L.w(
                    "Error fetching conversation item details. Posting null to trigger caller "
                            + "to remove conversation");
            postValue(null);
        }
    }

    private void onDataChangeInternal() {
        if (!verifyChangeOccurred()) {
            return;
        }
        Conversation conversation = getValue() == null ? null : getValue().mConversation;
        Conversation.Builder conversationBuilder =
                conversation == null
                        ? initConversationBuilder(mConversationId)
                        : conversation.toBuilder();
        Cursor messagesCursor =
                CursorUtils.getMessagesCursor(mConversationId, ITEM_LIMIT, /* offset= */ 0);
        // messages to read: first get unread messages
        List<Message> messagesToRead = MessageUtils.getUnreadMessages(messagesCursor);
        int unreadCount = messagesToRead.size();
        long lastReplyTimestamp = 0L;

        // if no unread messages and notify level is all, get read messages
        if (messagesToRead.isEmpty()) {
            Pair<List<Message>, Long> readMessagesAndReplyTimestamp =
                    MessageUtils.getReadMessagesAndReplyTimestamp(messagesCursor);
            messagesToRead = readMessagesAndReplyTimestamp.first;
            lastReplyTimestamp = readMessagesAndReplyTimestamp.second;
        }
        updateConversation(conversationBuilder, messagesToRead, unreadCount, lastReplyTimestamp);
    }

    private boolean verifyChangeOccurred() {
        // get the last message after offset to see if any change exists
        // check the last message with only the connection time offset
        Cursor messagesCursor =
                CursorUtils.getMessagesCursor(mConversationId, /* limit= */ 1, /* offset= */ 0);
        if (messagesCursor == null || !messagesCursor.moveToFirst()) {
            return false;
        }
        Conversation conversation = getValue() == null ? null : getValue().mConversation;
        Message prevLastMessage = ConversationUtil.getLastMessage(conversation);
        Message message = MessageUtils.parseCurrentMessage(messagesCursor);
        // checks to see if any change has been made to the last previous message,
        // if status changes to read, message status will be different
        // if status changes to replied messages, note: only timestamp will change,
        // as we don't add reply messages to the message list
        return message != null
                && (prevLastMessage == null
                        || prevLastMessage.getMessageStatus() != message.getMessageStatus()
                        || ConversationUtil.getConversationTimestamp(conversation)
                                != message.getTimestamp());
    }

    @NonNull
    private Conversation.Builder initConversationBuilder(@NonNull String conversationId) {
        String userName = ContactUtils.DRIVER_NAME;
        Conversation.Builder builder =
                new Conversation.Builder(
                        new Person.Builder().setName(userName).build(), conversationId);
        setConversationIconTitleAndParticipants(conversationId, builder);
        builder.setMuted(getMutedList().contains(conversationId));
        return builder;
    }

    private void setConversationIconTitleAndParticipants(
            @NonNull String conversationId, Conversation.Builder builder) {
        List<CharSequence> participantNames = new ArrayList<>();
        List<Bitmap> participantIcons = new ArrayList<>();
        List<Person> participants =
                ContactUtils.getRecipients(
                        conversationId,
                        (name, bitmap) -> {
                            participantNames.add(name);
                            participantIcons.add(bitmap);
                        });
        builder.setParticipants(participants);
        builder.setConversationTitle(participantNames.stream().collect(joining(COMMA_SEPARATOR)));
        Bitmap bitmap = AvatarUtil.createGroupAvatar(getContext(), participantIcons);
        if (bitmap != null) {
            builder.setConversationIcon(IconCompat.createWithBitmap(bitmap));
        }
    }

    private void updateConversation(
            @NonNull Conversation.Builder conversationBuilder,
            @NonNull List<Message> messages,
            int unreadCount,
            long lastReplyTimestamp) {
        conversationBuilder.setMessages(messages).setUnreadCount(unreadCount);
        ConversationUtil.setReplyTimestampAsAnExtra(
                conversationBuilder, /* extras= */ null, lastReplyTimestamp);
        Conversation conversation = conversationBuilder.build();
        postValue(new ConversationChangeSet(conversation, NotifyLevel.NEW_OR_UPDATED_MESSAGE));
    }

    @NonNull
    private static Set<String> getMutedList() {
        SharedPreferences sharedPreferences = AppFactory.get().getSharedPreferences();
        return sharedPreferences.getStringSet(
                MessageConstants.KEY_MUTED_CONVERSATIONS, new HashSet<>());
    }

    private void onSharedPreferenceChangedInternal(@NonNull String key) {
        Conversation item = getValue() == null ? null : getValue().mConversation;
        if (!MessageConstants.KEY_MUTED_CONVERSATIONS.equals(key) || item == null) {
            return;
        }
        Set<String> list = getMutedList();
        boolean isMuted = list.contains(mConversationId);
        boolean wasPreviouslyMuted = item.isMuted();
        if (isMuted == wasPreviouslyMuted) {
            return;
        }
        Conversation.Builder builder = item.toBuilder();
        builder.setMuted(isMuted);
        postValue(
                new ConversationChangeSet(
                        /* conversation= */ builder.build(), NotifyLevel.METADATA));
    }

    /** Mark conversation as Read */
    public static void markAsRead(@NonNull String conversationId) {
        L.d("markAsRead for conversationId: " + conversationId);
        Context context = AppFactory.get().getContext();
        ContentValues values = new ContentValues();
        values.put(READ, 1);
        context.getContentResolver()
                .update(CursorUtils.getConversationUri(conversationId), values, /* extras= */ null);
    }

    public static class ConversationChangeSet {

        @NonNull private final Conversation mConversation;
        @NotifyLevel private final int mChange;

        private ConversationChangeSet(@NonNull Conversation conversation, @NotifyLevel int change) {
            mConversation = conversation;
            mChange = change;
        }

        @NonNull
        public Conversation getConversation() {
            return mConversation;
        }

        @NotifyLevel
        public int getChange() {
            return mChange;
        }
    }

    /** Indicates the granularity of changes the observer wishes to observe */
    @IntDef(
            value = {
                NotifyLevel.METADATA,
                NotifyLevel.NEW_OR_UPDATED_MESSAGE,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotifyLevel {
        /**
         * When set, this indicates that the change included the metadata of the Conversation, such
         * as the title, avatar or mute changes
         */
        int METADATA = 0;

        /**
         * When set, this indicates that the change included new or updated messages as part of the
         * change set.
         */
        int NEW_OR_UPDATED_MESSAGE = 1;
    }
}
