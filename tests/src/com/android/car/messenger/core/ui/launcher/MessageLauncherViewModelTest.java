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

package com.android.car.messenger.core.ui.launcher;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;
import com.android.car.messenger.core.models.UserAccount;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MessageLauncherViewModelTest {

    private MessageLauncherViewModel mMessageLauncherViewModel;
    private AppFactoryTestImpl mAppFactory;

    /** Used to execute livedata.postValue() synchronously */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    @Mock
    private Application mMockApplication;
    @Mock
    private TelephonyDataModel mMockDataModel;

    private LifecycleRegistry mLifecycleRegistry;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private Observer<List<UserAccount>> mMockObserver;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);

        Context context = ApplicationProvider.getApplicationContext();
        mAppFactory = new AppFactoryTestImpl(context, mMockDataModel, null, null);

        when(mMockApplication.getApplicationContext()).thenReturn(context);
        mMessageLauncherViewModel = new MessageLauncherViewModel(mMockApplication);
    }

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    @Test
    public void testGetAccounts() {
        UserAccount acc1 = new UserAccount(111, "name1", "iccid1", Instant.ofEpochSecond(111));
        UserAccount acc2 = new UserAccount(222, "name2", "iccid2", Instant.ofEpochSecond(222));
        List<UserAccount> userAccounts = Arrays.asList(acc1, acc2);
        when(mMockDataModel.getAccounts()).thenReturn(new MutableLiveData<>(userAccounts));

        LiveData<List<UserAccount>> accounts = mMessageLauncherViewModel.getAccounts();
        accounts.observeForever(mMockObserver);
        assertThat(accounts.getValue()).containsExactly(acc1);
    }
}
