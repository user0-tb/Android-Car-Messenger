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

package com.android.car.messenger.impl.datamodels;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import androidx.core.app.Person;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.Observer;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.impl.AppFactoryTestImpl;
import com.android.car.messenger.impl.datamodels.util.ConversationFetchUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ConversationListLiveDataTest {

    private ConversationListLiveData mConversationListLiveData;
    private AppFactoryTestImpl mAppFactory;

    /** Used to execute livedata.postValue() synchronously */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    private LifecycleRegistry mLifecycleRegistry;
    @Mock
    private UserAccount mMockUserAccount;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private Observer<Collection<Conversation>> mMockObserver;
    private Context mContext;
    @Mock
    private Cursor mMockCursor;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    private SharedPreferences mMockSharedPreferences;
    @Captor
    private ArgumentCaptor<Uri> mCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mAppFactory = new AppFactoryTestImpl(mContext, null, mMockSharedPreferences, null);

        when(mMockUserAccount.getId()).thenReturn(0);
        mConversationListLiveData = new ConversationListLiveData(mMockUserAccount);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);
    }

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    @Test
    public void testUri() {
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        doNothing().when(mMockContentResolver).registerContentObserver(any(), eq(true), any());

        mConversationListLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        verify(mMockContentResolver).registerContentObserver(
                mCaptor.capture(), eq(true), any());
        assertThat(mCaptor.getValue()).isEqualTo(Telephony.MmsSms.CONTENT_URI);
    }

    @Test
    @UiThreadTest
    public void testOnDataChange() {
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockCursor.getColumnIndex(any())).thenReturn(0);
        when(mMockCursor.getString(anyInt())).thenReturn("0");

        // allow fetchConversation to return mock conversations below, then exit loop
        when(mMockCursor.moveToNext()).thenReturn(true, true, true, false);

        MockitoSession session = mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(ConversationFetchUtil.class)
                .spyStatic(ConversationsPerDeviceFetchManager.class)
                .startMocking();
        try {
            doReturn(mMockCursor).when(() ->
                    ConversationsPerDeviceFetchManager.getCursor(anyInt()));

            Conversation conv1 = buildConversation(/* id= */ "1", /* timestamp */ 300);
            Conversation conv2 = buildConversation(/* id= */ "2", /* timestamp */ 100);
            Conversation conv3 = buildConversation(/* id= */ "3", /* timestamp */ 200);

            doReturn(conv1, conv2, conv3).when(
                    () -> ConversationFetchUtil.fetchConversation(any()));

            mConversationListLiveData.observe(mMockLifecycleOwner,
                    (value) -> mMockObserver.onChanged(value));
            assertThat(mConversationListLiveData.getValue()).isNull();
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);

            // verify results are sorted by timestamp desc
            assertThat(mConversationListLiveData.getValue())
                    .containsExactly(conv1, conv3, conv2).inOrder();
        } finally {
            session.finishMocking();
        }
    }

    private Conversation buildConversation(String id, long timestamp) {
        Person person = mock(Person.class);
        Conversation.Message message = new Conversation.Message("", timestamp, person);
        List<Conversation.Message> messages = Arrays.asList(message);

        return new Conversation.Builder(person, id)
                .setMessages(messages)
                .build();
    }
}
