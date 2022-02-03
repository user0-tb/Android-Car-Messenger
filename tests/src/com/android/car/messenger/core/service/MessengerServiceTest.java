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

package com.android.car.messenger.core.service;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.shared.MessageConstants;
import com.android.car.messenger.core.util.VoiceUtil;
import com.android.car.messenger.impl.AppFactoryTestImpl;
import com.android.car.messenger.impl.datamodels.TelephonyDataModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class MessengerServiceTest {

    private MessengerService mMessengerService;
    private AppFactoryTestImpl mAppFactory;
    private Context mContext;
    @Rule
    public final ServiceTestRule mServiceTestRule = new ServiceTestRule();
    @Captor
    private ArgumentCaptor<Intent> mCaptor;
    @Mock
    private TelephonyDataModel mDataModel;

    @Before
    public void setup() throws TimeoutException {
        MockitoAnnotations.initMocks(this);

        mAppFactory = new AppFactoryTestImpl(null, mDataModel, null, null);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent intent = new Intent(mContext, MessengerService.class);
        IBinder binder = mServiceTestRule.bindService(intent);
        mMessengerService = ((MessengerService.LocalBinder) binder).getService();
    }

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    @Test
    public void testSubscribeToNotifications() {
        MutableLiveData<Conversation> unreadLiveData = mock(MutableLiveData.class);
        MutableLiveData<String> removedLiveData = mock(MutableLiveData.class);

        when(mDataModel.getUnreadMessages()).thenReturn(unreadLiveData);
        when(mDataModel.onConversationRemoved()).thenReturn(removedLiveData);

        // These calls are delayed as defined by MessengerService#DELAY_FETCH_DURATION
        verify(unreadLiveData, timeout(5000)).observeForever(any());
        // TODO: b/216550137 Make delay configurable to speed up test
        verify(removedLiveData, timeout(5000)).observeForever(any());
    }

    @Test
    public void testOnStartCommand_nullAction() {
        int result = mMessengerService.onStartCommand(null, 0, 0);
        assertThat(result).isEqualTo(Service.START_STICKY);

        result = mMessengerService.onStartCommand(new Intent(), 0, 0);
        assertThat(result).isEqualTo(Service.START_STICKY);
    }

    @Test
    public void testOnStartCommand_actionReply() {
        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(VoiceUtil.class)
                .startMocking();
        try {
            Intent intent = new Intent();
            intent.setAction(MessageConstants.ACTION_REPLY);
            int result = mMessengerService.onStartCommand(intent, 0, 0);

            assertThat(result).isEqualTo(Service.START_STICKY);
            verify(() -> VoiceUtil.voiceReply(mCaptor.capture()));
            assertThat(mCaptor.getValue()).isEqualTo(intent);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStartCommand_actionMute() {
        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(VoiceUtil.class)
                .startMocking();
        try {
            Intent intent = new Intent();
            intent.setAction(MessageConstants.ACTION_MUTE);
            int result = mMessengerService.onStartCommand(intent, 0, 0);

            assertThat(result).isEqualTo(Service.START_STICKY);
            verify(() -> VoiceUtil.mute(mCaptor.capture()));
            assertThat(mCaptor.getValue()).isEqualTo(intent);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStartCommand_actionMarkRead() {
        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(VoiceUtil.class)
                .startMocking();
        try {
            Intent intent = new Intent();
            intent.setAction(MessageConstants.ACTION_MARK_AS_READ);
            int result = mMessengerService.onStartCommand(intent, 0, 0);

            assertThat(result).isEqualTo(Service.START_STICKY);
            verify(() -> VoiceUtil.markAsRead(mCaptor.capture()));
            assertThat(mCaptor.getValue()).isEqualTo(intent);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testOnStartCommand_actionDirectSend() {
        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(VoiceUtil.class)
                .startMocking();
        try {
            Intent intent = new Intent();
            intent.setAction(MessageConstants.ACTION_DIRECT_SEND);
            int result = mMessengerService.onStartCommand(intent, 0, 0);

            assertThat(result).isEqualTo(Service.START_STICKY);
            verify(() -> VoiceUtil.directSend(mCaptor.capture()));
            assertThat(mCaptor.getValue()).isEqualTo(intent);
        } finally {
            session.finishMocking();
        }
    }
}
