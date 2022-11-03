/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.app.Application;
import android.car.drivingstate.CarUxRestrictions;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.android.car.apps.common.log.L;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.interfaces.DataModel;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.core.ui.livedata.BluetoothStateLiveData;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** View model for ConversationLogFragment which provides message history live data. */
public class ConversationListViewModel extends AndroidViewModel {
    private static final String TAG = "CM.ConversationListViewModel";

    @SuppressLint("StaticFieldLeak")
    @NonNull
    private final DataModel mDataModel;

    @Nullable private UserAccount mUserAccount;
    @Nullable private LiveData<List<UIConversationItem>> mUIConversationLogLiveData;
    private LiveData<Integer> mBluetoothStateLiveData;
    private Observer mBluetoothStateObserver;

    public ConversationListViewModel(@NonNull Application application) {
        super(application);
        mDataModel = AppFactory.get().getDataModel();
        mBluetoothStateLiveData = new BluetoothStateLiveData(application.getApplicationContext());
        mBluetoothStateObserver = o -> L.i(TAG, "BluetoothState changed");
        mBluetoothStateLiveData.observeForever(mBluetoothStateObserver);
    }

    public LiveData<Integer> getBluetoothStateLiveData() {
        return mBluetoothStateLiveData;
    }

    /**
     * Gets an observable {@link UIConversationItem} list for the connected account
     */
    @NonNull
    public LiveData<List<UIConversationItem>> getConversations(@NonNull UserAccount userAccount) {
        if (mUserAccount != null
                && mUserAccount.getId() == userAccount.getId()
                && mUIConversationLogLiveData != null) {
            return mUIConversationLogLiveData;
        }
        mUserAccount = userAccount;
        mUIConversationLogLiveData = createUIConversationLog(mUserAccount);
        return mUIConversationLogLiveData;
    }

    private LiveData<List<UIConversationItem>> createUIConversationLog(
            @NonNull UserAccount userAccount) {
        MediatorLiveData<List<UIConversationItem>> mutableLiveData = new MediatorLiveData<>();
        mutableLiveData.addSource(
                subscribeToConversations(userAccount),
                pair -> {
                    CarUxRestrictions uxRestrictions = pair.first;
                    Collection<Conversation> list = pair.second;
                    List<UIConversationItem> data =
                            list.stream()
                                    .map(
                                            conversation ->
                                                    UIConversationItemConverter
                                                            .convertToUIConversationItem(
                                                                    conversation, uxRestrictions))
                                    .collect(Collectors.toList());
                    mutableLiveData.postValue(data);
                });
        return mutableLiveData;
    }

    private LiveData<Pair<CarUxRestrictions, Collection<Conversation>>> subscribeToConversations(
            @NonNull UserAccount userAccount) {
        final LiveData<Collection<Conversation>> liveData =
                mDataModel.getConversations(userAccount);
        return Transformations.switchMap(
                AppFactory.get().getCarStateListener().getUxrRestrictions(),
                uxRestrictions -> {
                    L.d(TAG, "Got new ux restrictions: " + uxRestrictions);
                    return Transformations.map(
                            liveData, conversations -> new Pair<>(uxRestrictions, conversations));
                });
    }
}
