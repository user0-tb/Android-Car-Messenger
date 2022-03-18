/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.messenger.core.ui.conversationlist;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Bundle;

import androidx.core.app.Person;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.R;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.common.Conversation.Message.MessageStatus;
import com.android.car.messenger.core.shared.MessageConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class UIConversationItemConverterTest {

    private static final String CONVERSATION_TITLE = "TITLE";
    private static final String CONVERSATION_ID = "ID_123";
    private static final long MSG_TIMESTAMP = 2;
    private static final long REPLY_TIMESTAMP = 3;
    private static final String MSG_STRING = "MSG";
    private static final String REPLY_STRING = "REPLY";

    private Context mContext;
    @Mock
    private CarUxRestrictions mMockCarUxRestrictions;
    private Person mPerson;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
        mPerson = new Person.Builder().build();
    }

    @Test
    public void testConvertToUiConversationItem() {
        // UIConversationItemConverter.convertToUIConversationItem() is influenced by 3 things:
        // 1. is there a reply? ConversationUtil.isReplied()
        //  -> controlled by #createConversation() isReplied parameter
        // 2. are ux restrictions active? UXRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE
        //  -> controlled by #setShowTextPreview()
        // 3. are there unread messages? Conversation.getUnreadCount() > 0
        //  -> controlled by #createConversation() isUnread parameter
        // Note: 1 and 3 cannot both be true at the same time

        setShowTextPreview(true);
        Conversation conv = createConversation(/* isReplied= */ true, /* isUnread= */ false);
        assertUIConversationItem(
                conv,
                REPLY_STRING,
                /* unreadCountText= */ "0",
                REPLY_TIMESTAMP);

        setShowTextPreview(true);
        conv = createConversation(/* isReplied= */ false, /* isUnread= */ true);
        assertUIConversationItem(
                conv,
                MSG_STRING,
                /* unreadCountText= */ "1",
                MSG_TIMESTAMP);

        setShowTextPreview(false);
        conv = createConversation(/* isReplied= */ true, /* isUnread= */ false);
        assertUIConversationItem(
                conv,
                mContext.getString(R.string.replied),
                /* unreadCountText= */ "0",
                REPLY_TIMESTAMP);

        setShowTextPreview(false);
        conv = createConversation(/* isReplied= */ false, /* isUnread= */ true);
        assertUIConversationItem(
                conv,
                mContext.getString(R.string.tap_to_read_aloud),
                /* unreadCountText= */ "1",
                MSG_TIMESTAMP);
    }

    private void assertUIConversationItem(
            Conversation conversation,
            String textPreview,
            String unreadCountText,
            long timestamp) {
        UIConversationItem item = UIConversationItemConverter.convertToUIConversationItem(
                conversation, mMockCarUxRestrictions);
        assertThat(item.getConversationId()).isEqualTo(conversation.getId());
        assertThat(item.getTitle()).isEqualTo(conversation.getConversationTitle());
        assertThat(item.getTextPreview()).isEqualTo(textPreview);
        assertThat(item.getUnreadCountText()).isEqualTo(unreadCountText);
        assertThat(item.getLastMessageTimestamp()).isEqualTo(timestamp);
        assertThat(item.isMuted()).isEqualTo(conversation.isMuted());
        assertThat(item.getConversation()).isEqualTo(conversation);
    }

    private Conversation createConversation(boolean isReplied, boolean isUnread) {
        Conversation.Builder builder = new Conversation.Builder(mPerson, CONVERSATION_ID);
        builder.setConversationTitle(CONVERSATION_TITLE);

        if (isReplied) {
            Bundle extras = new Bundle();
            extras.putLong(MessageConstants.LAST_REPLY_TIMESTAMP_EXTRA, REPLY_TIMESTAMP);
            extras.putString(MessageConstants.LAST_REPLY_TEXT_EXTRA, REPLY_STRING);
            builder.setExtras(extras);
        }

        Conversation.Message msg = new Conversation.Message(MSG_STRING, MSG_TIMESTAMP, mPerson);
        if (isUnread) {
            msg.setMessageStatus(MessageStatus.MESSAGE_STATUS_UNREAD);
            builder.setUnreadCount(1);
        } else {
            msg.setMessageStatus(MessageStatus.MESSAGE_STATUS_READ);
            builder.setUnreadCount(0);
        }
        builder.setMessages(Arrays.asList(msg));

        return builder.build();
    }

    private void setShowTextPreview(boolean showTextPreview) {
        if (showTextPreview) {
            when(mMockCarUxRestrictions.getActiveRestrictions())
                    .thenReturn(CarUxRestrictions.UX_RESTRICTIONS_BASELINE);
        } else {
            when(mMockCarUxRestrictions.getActiveRestrictions())
                    .thenReturn(CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE);
        }
    }
}
