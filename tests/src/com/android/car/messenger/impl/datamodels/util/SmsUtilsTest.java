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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.provider.Telephony.Sms;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.common.Conversation.Message.MessageType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class SmsUtilsTest {

    private static final int ID_INDEX = 0;
    private static final int THREAD_ID_INDEX = 1;
    private static final int RECIPIENTS_INDEX = 2;
    private static final int BODY_INDEX = 3;
    private static final int SUBSCRIPTION_ID_INDEX = 4;
    private static final int DATE_INDEX = 5;
    private static final int TYPE_INDEX = 6;
    private static final int READ_INDEX = 7;

    @Mock
    private Cursor mMockCursor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mMockCursor.getColumnIndex(Sms.THREAD_ID)).thenReturn(THREAD_ID_INDEX);
        when(mMockCursor.getColumnIndex(Sms.ADDRESS)).thenReturn(RECIPIENTS_INDEX);
        when(mMockCursor.getColumnIndex(Sms.BODY)).thenReturn(BODY_INDEX);
        when(mMockCursor.getColumnIndex(Sms.SUBSCRIPTION_ID)).thenReturn(SUBSCRIPTION_ID_INDEX);
        when(mMockCursor.getColumnIndex(Sms.DATE)).thenReturn(DATE_INDEX);
        when(mMockCursor.getColumnIndex(Sms.TYPE)).thenReturn(TYPE_INDEX);
        when(mMockCursor.getColumnIndex(Sms.READ)).thenReturn(READ_INDEX);
        when(mMockCursor.getColumnIndex(Sms._ID)).thenReturn(ID_INDEX);
    }

    @Test
    public void parseSmsTest() {
        String id = "0";
        int threadId = 0;
        String phoneNumber = "1234567890";
        String body = "text";
        int subscriptionId = 0;
        int type = MessageType.MESSAGE_TYPE_SENT;
        long timestamp = 123;
        int read = 1;

        when(mMockCursor.getString(ID_INDEX)).thenReturn(id);
        when(mMockCursor.getInt(THREAD_ID_INDEX)).thenReturn(threadId);
        when(mMockCursor.getString(RECIPIENTS_INDEX)).thenReturn(phoneNumber);
        when(mMockCursor.getString(BODY_INDEX)).thenReturn(body);
        when(mMockCursor.getInt(SUBSCRIPTION_ID_INDEX)).thenReturn(subscriptionId);
        when(mMockCursor.getInt(TYPE_INDEX)).thenReturn(type);
        when(mMockCursor.getLong(DATE_INDEX)).thenReturn(timestamp);
        when(mMockCursor.getInt(READ_INDEX)).thenReturn(read);

        MmsSmsMessage message = SmsUtils.parseSms(mMockCursor);

        assertThat(message.mId).isEqualTo(id);
        assertThat(message.mThreadId).isEqualTo(threadId);
        assertThat(message.mPhoneNumber).isEqualTo(phoneNumber);
        assertThat(message.mBody).isEqualTo(body);
        assertThat(message.mSubscriptionId).isEqualTo(subscriptionId);
        assertThat(message.mType).isEqualTo(type);
        assertThat(message.mDate).isEqualTo(Instant.ofEpochMilli(timestamp));
        assertThat(message.mRead).isTrue();
    }
}
