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

package com.android.car.messenger.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.util.CarStateListener;
import com.android.car.messenger.impl.datamodels.TelephonyDataModel;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppFactoryImplTest {

    private AppFactoryTestImpl mAppFactory;

    @After
    public void teardown() {
        mAppFactory.teardown();
    }

    @Test
    public void testGetInstance() {
        mAppFactory = new AppFactoryTestImpl(/* context= */ null,
                /* dataModel= */ null, /* sharedPreferences= */ null, /* listener= */ null);
        assertThat(AppFactory.get()).isEqualTo(mAppFactory);

        // Tests that existing instance cannot be overridden.
        mAppFactory = new AppFactoryTestImpl(/* context= */ null,
                /* dataModel= */ null, /* sharedPreferences= */ null, /* listener= */ null);
        assertThat(AppFactory.get()).isEqualTo(mAppFactory);
    }

    @Test
    public void testGetContext() {
        Context context = ApplicationProvider.getApplicationContext();
        mAppFactory = new AppFactoryTestImpl(context,
                /* dataModel= */ null, /* sharedPreferences= */ null, /* listener= */ null);
        assertThat(AppFactory.get().getContext()).isEqualTo(context);
    }

    @Test
    public void testGetDataModel() {
        TelephonyDataModel telephonyDataModel = new TelephonyDataModel();
        mAppFactory = new AppFactoryTestImpl(/* context= */ null,
                telephonyDataModel, /* sharedPreferences= */ null, /* listener= */ null);
        assertThat(AppFactory.get().getDataModel()).isEqualTo(telephonyDataModel);
    }

    @Test
    public void testGetSharedPreferences() {
        SharedPreferences sharedPreferences = mock(SharedPreferences.class);
        mAppFactory = new AppFactoryTestImpl(/* context= */ null,
                /* dataModel= */ null, sharedPreferences, /* listener= */ null);
        assertThat(AppFactory.get().getSharedPreferences()).isEqualTo(sharedPreferences);
    }

    @Test
    public void testGetCarStateListener() {
        mAppFactory = new AppFactoryTestImpl(/* context= */ null,
                /* dataModel= */ null, /* sharedPreferences= */ null, /* listener= */ null);
        assertThat(AppFactory.get().getCarStateListener()).isInstanceOf(CarStateListener.class);
    }

    @Test
    public void testGetCarStateListener_null() {
        Context context = ApplicationProvider.getApplicationContext();
        CarStateListener carStateListener = new CarStateListener(context);
        mAppFactory = new AppFactoryTestImpl(context,
                /* dataModel= */ null, /* sharedPreferences= */ null, carStateListener);
        assertThat(AppFactory.get().getCarStateListener()).isEqualTo(carStateListener);
    }
}
