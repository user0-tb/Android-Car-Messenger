/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.interfaces.DataModel;
import com.android.car.messenger.impl.datamodels.TelephonyDataModel;

/* App Factory Implementation */
class AppFactoryImpl extends AppFactory {
    @NonNull private Context mApplicationContext;
    @NonNull private DataModel mDataModel;
    @NonNull private SharedPreferences mSharedPreferences;

    private AppFactoryImpl() {}

    public static void register(@NonNull final CarMessengerApp application) {
        if (sRegistered && sInitialized) {
            return;
        }

        final AppFactoryImpl factory = new AppFactoryImpl();
        AppFactory.setInstance(factory);
        sRegistered = true;

        // At this point Factory is published. Services can now get initialized and depend on
        // Factory.get().
        factory.mApplicationContext = application.getApplicationContext();
        factory.mDataModel = new TelephonyDataModel();
        factory.mSharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(factory.mApplicationContext);
    }

    @Override
    @NonNull
    public Context getContext() {
        return mApplicationContext;
    }

    @Override
    @NonNull
    public DataModel getDataModel() {
        return mDataModel;
    }

    @Override
    @NonNull
    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }
}
