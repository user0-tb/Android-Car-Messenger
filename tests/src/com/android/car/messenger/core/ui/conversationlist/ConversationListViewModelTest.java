/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.core.util.CarStateListener;
import com.android.car.messenger.impl.AppFactoryTestImpl;
import com.android.car.messenger.impl.datamodels.TelephonyDataModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ConversationListViewModelTest {

    private static final int ID_1 = 123;
    private static final int ID_2 = 456;

    private ConversationListViewModel mConversationListViewModel;
    private AppFactoryTestImpl mAppFactory;

    /** Used to execute livedata.postValue() synchronously */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    @Mock
    private Application mMockApplication;
    @Mock
    private UserAccount mMockUserAccount;
    @Mock
    private UserAccount mMockUserAccount2;

    private LifecycleRegistry mLifecycleRegistry;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private Observer<List<UIConversationItem>> mMockObserver;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);

        Context context = ApplicationProvider.getApplicationContext();
        CarStateListener carStateListener = new CarStateListener(context);
        TelephonyDataModel telephonyDataModel = new TelephonyDataModel();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mAppFactory = new AppFactoryTestImpl(context, telephonyDataModel,
                sharedPrefs, carStateListener);

        when(mMockApplication.getApplicationContext()).thenReturn(context);
        mConversationListViewModel = new ConversationListViewModel(mMockApplication);

        when(mMockUserAccount.getId()).thenReturn(ID_1);
        when(mMockUserAccount2.getId()).thenReturn(ID_2);
    }

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    @Test
    public void testConversations_existingAccount() {
        when(mMockUserAccount2.getId()).thenReturn(ID_1);

        // first call creates new conversation log
        LiveData<List<UIConversationItem>> liveData1 =
                mConversationListViewModel.getConversations(mMockUserAccount);
        liveData1.observeForever(mMockObserver);

        // run this method again to return existing conversation log
        LiveData<List<UIConversationItem>> liveData2 =
                mConversationListViewModel.getConversations(mMockUserAccount2);
        liveData2.observeForever(mMockObserver);

        assertThat(liveData1).isEqualTo(liveData2);
    }

    @Test
    public void testConversations_newAccount() {
        LiveData<List<UIConversationItem>> liveData =
                mConversationListViewModel.getConversations(mMockUserAccount);

        liveData.observeForever(mMockObserver);
        assertThat(liveData.getValue()).isNotNull();
    }
}
