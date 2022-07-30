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

package com.android.car.messenger.core.util;


import static androidx.core.app.RemoteInput.EXTRA_RESULTS_DATA;
import static androidx.core.app.RemoteInput.RESULTS_CLIP_LABEL;

import static com.android.car.assist.CarVoiceInteractionSession.KEY_ACTION;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_CONVERSATION;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_DEVICE_ADDRESS;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_NOTIFICATION;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_PHONE_NUMBER;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_READ_CONVERSATION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_READ_NOTIFICATION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_REPLY_CONVERSATION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_REPLY_NOTIFICATION;
import static com.android.car.messenger.core.shared.MessageConstants.EXTRA_ACCOUNT_ID;
import static com.android.car.messenger.core.shared.MessageConstants.EXTRA_CONVERSATION_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import androidx.core.app.Person;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.R;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.impl.AppFactoryTestImpl;
import com.android.car.messenger.impl.datamodels.TelephonyDataModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class VoiceUtilTest {

    @Mock
    private Activity mActivity;
    @Mock
    private UserAccount mUserAccount;
    @Mock
    private Resources mResources;

    @Mock
    private TelephonyDataModel mMockDataModel;

    @Captor
    private ArgumentCaptor<Bundle> mCaptor;

    private AppFactoryTestImpl mAppFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mActivity.getResources()).thenReturn(mResources);

        Context context = ApplicationProvider.getApplicationContext();
        mAppFactory = new AppFactoryTestImpl(context, mMockDataModel, null, null);
    }

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    private Conversation createConversation(String id) {
        Person person = new Person.Builder()
                .setName("name")
                .build();
        Conversation.Message message = new Conversation.Message("msg", 1, person);
        return new Conversation(
                id,
                person,
                "title",
                null,
                Arrays.asList(message),
                Arrays.asList(person),
                null,
                0,
                false,
                null
        );
    }

    @Test
    public void testVoiceRequestReadConversation_TTR() {
        when(mResources.getBoolean(R.bool.ttr_conversation_supported)).thenReturn(true);
        VoiceUtil.voiceRequestReadConversation(mActivity, mUserAccount, createConversation("1"));

        verify(mActivity).showAssist(mCaptor.capture());

        Bundle args = mCaptor.getValue();
        assertThat(args.getString(KEY_ACTION)).isEqualTo(VOICE_ACTION_READ_CONVERSATION);
        assertThat(args.getBundle(KEY_CONVERSATION)).isNotNull();
    }

    @Test
    public void testVoiceRequestReadConversation_SBN() {
        when(mResources.getBoolean(R.bool.ttr_conversation_supported)).thenReturn(false);
        VoiceUtil.voiceRequestReadConversation(mActivity, mUserAccount, createConversation("1"));

        verify(mActivity).showAssist(mCaptor.capture());

        Bundle args = mCaptor.getValue();
        assertThat(args.getString(KEY_ACTION)).isEqualTo(VOICE_ACTION_READ_NOTIFICATION);
        Parcelable sbn = args.getParcelable(KEY_NOTIFICATION);
        assertThat(sbn).isInstanceOf(StatusBarNotification.class);
    }

    @Test
    public void testVoiceRequestReplyConversation_TTR() {
        when(mResources.getBoolean(R.bool.ttr_conversation_supported)).thenReturn(true);
        VoiceUtil.voiceRequestReplyConversation(mActivity, mUserAccount, createConversation("2"));

        verify(mActivity).showAssist(mCaptor.capture());

        Bundle args = mCaptor.getValue();
        assertThat(args.getString(KEY_ACTION)).isEqualTo(VOICE_ACTION_REPLY_CONVERSATION);
        assertThat(args.getBundle(KEY_CONVERSATION)).isNotNull();
    }

    @Test
    public void testVoiceRequestReplyConversation_SBN() {
        when(mResources.getBoolean(R.bool.ttr_conversation_supported)).thenReturn(false);
        VoiceUtil.voiceRequestReplyConversation(mActivity, mUserAccount, createConversation("2"));

        verify(mActivity).showAssist(mCaptor.capture());

        Bundle args = mCaptor.getValue();
        assertThat(args.getString(KEY_ACTION)).isEqualTo(VOICE_ACTION_REPLY_NOTIFICATION);
        Parcelable sbn = args.getParcelable(KEY_NOTIFICATION);
        assertThat(sbn).isInstanceOf(StatusBarNotification.class);
    }

    @Test
    public void testDirectSend() {
        String phoneNumber = "pn";
        String iccId = "id";
        String message = "msg";
        Intent intent = new Intent();
        intent.putExtra(KEY_PHONE_NUMBER, phoneNumber);
        intent.putExtra(KEY_DEVICE_ADDRESS, iccId);
        intent.putExtra(Intent.EXTRA_TEXT, message);

        VoiceUtil.directSend(intent);
        verify(mMockDataModel).sendMessage(iccId, phoneNumber, message);
    }

    @Test
    public void testVoiceReply() {
        String message = "msg";
        Intent remoteIntent = new Intent();
        Bundle extras = new Bundle();
        extras.putString(Intent.EXTRA_TEXT, message);
        remoteIntent.putExtra(EXTRA_RESULTS_DATA, extras);

        String key = "key";
        int id = 1;
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CONVERSATION_KEY, key);
        intent.putExtra(EXTRA_ACCOUNT_ID, id);
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, remoteIntent));

        VoiceUtil.voiceReply(intent);
        verify(mMockDataModel).replyConversation(id, key, message);
    }

    @Test
    public void testMute() {
        String key = "key";
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CONVERSATION_KEY, key);

        VoiceUtil.mute(intent);
        verify(mMockDataModel).muteConversation(key, true);
    }

    @Test
    public void testMarkAsRead() {
        String key = "key";
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CONVERSATION_KEY, key);

        VoiceUtil.markAsRead(intent);
        verify(mMockDataModel).markAsRead(key);
    }
}
