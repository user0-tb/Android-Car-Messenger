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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.common.Conversation.Message.MessageType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class MmsUtilsTest {

    private static final int ID_INDEX = 0;
    private static final int THREAD_ID_INDEX = 1;
    private static final int RECIPIENTS_INDEX = 2;
    private static final int BODY_INDEX = 3;
    private static final int SUBSCRIPTION_ID_INDEX = 4;
    private static final int DATE_INDEX = 5;
    private static final int TYPE_INDEX = 6;
    private static final int READ_INDEX = 7;
    private static final int CONTENT_TYPE_INDEX = 8;

    private Context mContext;
    @Mock
    private Cursor mMockCursor;
    @Mock
    private Cursor mMockBodyCursor;
    @Mock
    private Cursor mMockPNCursor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();

        when(mMockCursor.getColumnIndex(Sms._ID)).thenReturn(ID_INDEX);
        when(mMockCursor.getColumnIndex(Sms.THREAD_ID)).thenReturn(THREAD_ID_INDEX);
        when(mMockCursor.getColumnIndex(Mms.MESSAGE_BOX)).thenReturn(TYPE_INDEX);
        when(mMockCursor.getColumnIndex(Sms.SUBSCRIPTION_ID)).thenReturn(SUBSCRIPTION_ID_INDEX);
        when(mMockCursor.getColumnIndex(Sms.DATE)).thenReturn(DATE_INDEX);
        when(mMockCursor.getColumnIndex(Sms.READ)).thenReturn(READ_INDEX);
        when(mMockCursor.getColumnIndex(Telephony.BaseMmsColumns.CONTENT_TYPE))
                .thenReturn(CONTENT_TYPE_INDEX);
    }

    @Test
    public void testParseMms() {
        String id = "0";
        int threadId = 0;
        String phoneNumber = "1234567890";
        String body = "text";
        int subscriptionId = 0;
        int type = MessageType.MESSAGE_TYPE_SENT;
        long timestamp = 123;
        int read = 1;

        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(CursorUtils.class)
                .startMocking();
        try {
            doReturn(mMockBodyCursor).when(() -> CursorUtils.simpleQuery(any(), any()));
            doReturn(mMockPNCursor).when(() ->
                    CursorUtils.simpleQueryWithSelection(any(), any(), any()));

            // Mock pnCursor and bodyCursor to only return one row
            when(mMockBodyCursor.moveToNext()).thenReturn(true, false);
            when(mMockBodyCursor.getColumnIndex(Mms.Part.TEXT)).thenReturn(BODY_INDEX);
            when(mMockBodyCursor.isLast()).thenReturn(true);
            when(mMockPNCursor.moveToFirst()).thenReturn(true);
            when(mMockPNCursor.getColumnIndex(Mms.Addr.ADDRESS)).thenReturn(RECIPIENTS_INDEX);

            when(mMockCursor.getString(ID_INDEX)).thenReturn(id);
            when(mMockCursor.getInt(THREAD_ID_INDEX)).thenReturn(threadId);
            when(mMockPNCursor.getString(RECIPIENTS_INDEX)).thenReturn(phoneNumber);
            when(mMockBodyCursor.getString(BODY_INDEX)).thenReturn(body);
            when(mMockCursor.getInt(SUBSCRIPTION_ID_INDEX)).thenReturn(subscriptionId);
            when(mMockCursor.getInt(TYPE_INDEX)).thenReturn(type);
            when(mMockCursor.getLong(DATE_INDEX)).thenReturn(timestamp);
            when(mMockCursor.getInt(READ_INDEX)).thenReturn(read);

            MmsSmsMessage message = MmsUtils.parseMms(mContext, mMockCursor);

            assertThat(message.mId).isEqualTo(id);
            assertThat(message.mThreadId).isEqualTo(threadId);
            assertThat(message.mPhoneNumber).isEqualTo(phoneNumber);
            assertThat(message.mBody).isEqualTo(body);
            assertThat(message.mSubscriptionId).isEqualTo(subscriptionId);
            assertThat(message.mType).isEqualTo(type);
            assertThat(message.mDate).isEqualTo(Instant.ofEpochSecond(timestamp));
            assertThat(message.mRead).isTrue();

        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsMms() {
        when(mMockCursor.getString(CONTENT_TYPE_INDEX)).thenReturn(MmsUtils.MMS_CONTENT_TYPE);
        assertThat(MmsUtils.isMms(mMockCursor)).isTrue();

        when(mMockCursor.getString(CONTENT_TYPE_INDEX)).thenReturn("");
        assertThat(MmsUtils.isMms(mMockCursor)).isFalse();
    }
}
