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

package com.android.car.messenger.impl.datamodels.util;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.common.Conversation.Message;
import com.android.car.messenger.common.Conversation.Message.MessageStatus;
import com.android.car.messenger.common.Conversation.Message.MessageType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MessageUtilsTest {

    private static final int MESSAGE_LIMIT = 10;

    @Test
    public void testGetMessages() {
        MmsSmsMessage msg1 = createMessage(
                /* id= */ "1",
                /* timestamp= */ 1,
                /* body= */ "",
                /* type= */ MessageType.MESSAGE_TYPE_SENT,
                /* isRead= */ true);
        MmsSmsMessage msg2 = createMessage(
                /* id= */ "2",
                /* timestamp= */ 2,
                /* body= */ "text2",
                /* type= */ MessageType.MESSAGE_TYPE_ALL,
                /* isRead= */ true);
        MmsSmsMessage msg3 = createMessage(
                /* id= */ "3",
                /* timestamp= */ 3,
                /* body= */ "text3",
                /* type= */ MessageType.MESSAGE_TYPE_INBOX,
                /* isRead= */ false);


        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(MmsUtils.class)
                .spyStatic(SmsUtils.class)
                .startMocking();

        try {
            Cursor smsCursor = mock(Cursor.class);
            Cursor mmsCursor = mock(Cursor.class);

            // Mocks smsCursor to return a single message, and mmsCursor to return two messages.
            doReturn(true).when(() -> MmsUtils.isMms(mmsCursor));
            doReturn(false).when(() -> MmsUtils.isMms(smsCursor));
            doReturn(msg2, msg1).when(() -> MmsUtils.parseMms(any(), eq(mmsCursor)));
            doReturn(msg3).when(() -> SmsUtils.parseSms(smsCursor));
            when(smsCursor.moveToFirst()).thenReturn(true);
            when(smsCursor.moveToNext()).thenReturn(false);
            when(mmsCursor.moveToFirst()).thenReturn(true);
            when(mmsCursor.moveToNext()).thenReturn(true, false);

            // Tests that empty messages are skipped and returned messages are in descending order
            List<Message> messages = MessageUtils.getMessages(MESSAGE_LIMIT, mmsCursor, smsCursor);
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getText()).isEqualTo("text3");
            assertThat(messages.get(1).getText()).isEqualTo("text2");
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testGetUnreadMessages() {
        Message msg = new Message("text1", /* timestamp= */ 1, /* person= */ null)
                .setMessageStatus(MessageStatus.MESSAGE_STATUS_READ);
        Message msg2 = new Message("text2", /* timestamp= */ 2, /* person= */ null)
                .setMessageStatus(MessageStatus.MESSAGE_STATUS_UNREAD);
        Message msg3 = new Message("text3", /* timestamp= */ 3, /* person= */ null)
                .setMessageStatus(MessageStatus.MESSAGE_STATUS_UNREAD);

        List<Message> messages = MessageUtils.getUnreadMessages(Arrays.asList(msg3, msg2, msg));

        assertThat(messages).containsExactly(msg2, msg3).inOrder();
    }

    @Test
    public void testGetReadMessagesAndReplyTimestamp() {
        Message msg = new Message("text1", /* timestamp= */ 1, /* person= */ null)
                .setMessageType(MessageType.MESSAGE_TYPE_SENT);
        Message msg2 = new Message("text2", /* timestamp= */ 2, /* person= */ null)
                .setMessageStatus(MessageStatus.MESSAGE_STATUS_READ);
        Message msg3 = new Message("text3", /* timestamp= */ 3, /* person= */ null)
                .setMessageStatus(MessageStatus.MESSAGE_STATUS_READ);
        Message msg4 = new Message("text4", /* timestamp= */ 4, /* person= */ null)
                .setMessageType(MessageType.MESSAGE_TYPE_SENT);

        Pair<List<Message>, Message> pair =
                MessageUtils.getReadMessagesAndReplyTimestamp(Arrays.asList(msg4, msg3, msg2, msg));
        List<Message> messages = pair.first;
        Message reply = pair.second;

        assertThat(messages).containsExactly(msg2, msg3).inOrder();
        assertThat(reply).isEqualTo(msg4);
    }

    private MmsSmsMessage createMessage(
            String id, long timestamp, String body, int type, boolean isRead) {
        MmsSmsMessage message = new MmsSmsMessage();
        message.mId = id;
        message.mThreadId = Integer.parseInt(id);
        message.mType = type;
        message.mRead = isRead;
        message.mDate = Instant.ofEpochMilli(timestamp);
        message.mBody = body;
        return message;
    }
}
