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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.apps.common.log.L;
import com.android.car.apps.common.util.LiveDataFunctions;
import com.android.car.messenger.R;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.interfaces.BluetoothState;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.core.ui.base.MessageListBaseFragment;
import com.android.car.messenger.core.util.VoiceUtil;
import com.android.car.ui.toolbar.MenuItem;

import java.util.ArrayList;
import java.util.List;

/** Fragment for Message History/Conversation Metadata List */
public class ConversationListFragment extends MessageListBaseFragment
        implements ConversationItemAdapter.OnConversationItemClickListener {
    private static final String TAG = "CM.ConversationListFragment";

    @NonNull
    private static final String BLUETOOTH_SETTING_ACTION = "android.settings.BLUETOOTH_SETTINGS";

    @NonNull
    private static final String BLUETOOTH_SETTING_CATEGORY = "android.intent.category.DEFAULT";

    @NonNull private static final String KEY_USER_ACCOUNT = "KEY_USER_ACCOUNT";
    @Nullable private ConversationItemAdapter mConversationItemAdapter;
    @Nullable private UserAccount mUserAccount;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            mUserAccount = getArguments().getParcelable(KEY_USER_ACCOUNT);
        }

        if (mUserAccount == null) {
            handleBluetoothDisconnected();
            return;
        }

        // Don't recreate the adapter if we already have one, so that the list items
        // will display immediately upon the view being recreated.
        L.d(TAG, "In View Created, about to load message data");
        if (mConversationItemAdapter == null) {
            mConversationItemAdapter =
                    new ConversationItemAdapter(/* onConversationItemClickListener= */ this);
        }
        getRecyclerView().setAdapter(mConversationItemAdapter);
        ConversationListViewModel viewModel =
                new ViewModelProvider(getActivity()).get(ConversationListViewModel.class);
        LiveData<Integer> bluetoothStateLiveData = viewModel.getBluetoothStateLiveData();
        LiveData<List<UIConversationItem>> conversationLiveData =
                viewModel.getConversations(mUserAccount);

        LiveDataFunctions.pair(bluetoothStateLiveData, conversationLiveData)
                        .observe(this, pair -> {
                            int bluetoothState = pair.first;
                            List<UIConversationItem> conversationLog = pair.second;
                            if (bluetoothState != BluetoothState.ENABLED) {
                                handleBluetoothDisconnected();
                            } else if (conversationLog == null || conversationLog.isEmpty()) {
                                mLoadingFrameLayout.showEmpty(R.string.no_messages);
                                setMenuItems();
                            } else {
                                mConversationItemAdapter.setConversationLogItems(conversationLog);
                                mLoadingFrameLayout.showContent();
                                setMenuItems();
                            }
                        });

    }

    private void handleBluetoothDisconnected() {
        Intent launchIntent = new Intent();
        launchIntent.setAction(BLUETOOTH_SETTING_ACTION);
        launchIntent.addCategory(BLUETOOTH_SETTING_CATEGORY);
        mLoadingFrameLayout.showError(
                R.string.bluetooth_disconnected,
                R.string.connect_bluetooth_button_text,
                v -> startActivity(launchIntent),
                true);
        removeMenuItems();
    }

    private void removeMenuItems() {
        Activity activity = getActivity();
        if (activity == null || mToolbar == null) {
            return;
        }
        mToolbar.setMenuItems(new ArrayList<>());
    }

    private void setMenuItems() {
        Activity activity = getActivity();
        if (activity == null || mUserAccount == null || mToolbar == null) {
            return;
        }
        if (!mToolbar.getMenuItems().isEmpty()) {
            return;
        }
        if (!getResources().getBoolean(R.bool.direct_send_supported)) {
            return;
        }
        MenuItem newMessageButton =
                new MenuItem.Builder(activity)
                        .setIcon(R.drawable.ui_icon_edit)
                        .setTinted(false)
                        .setShowIconAndTitle(true)
                        .setTitle(R.string.new_message)
                        .setPrimary(true)
                        .setOnClickListener(
                                item ->
                                        VoiceUtil.voiceRequestGenericCompose(
                                                activity, mUserAccount))
                        .build();
        ArrayList<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(newMessageButton);
        mToolbar.setMenuItems(menuItems);
    }

    @Override
    public void onConversationItemClicked(@NonNull Conversation conversation) {
        if (mUserAccount == null) {
            return;
        }
        VoiceUtil.voiceRequestReadConversation(requireActivity(), mUserAccount, conversation);
    }

    @Override
    public void onReplyIconClicked(@NonNull Conversation conversation) {
        if (mUserAccount == null) {
            return;
        }
        VoiceUtil.voiceRequestReplyConversation(requireActivity(), mUserAccount, conversation);
    }

    @Override
    public void onPlayIconClicked(@NonNull Conversation conversation) {
        if (mUserAccount == null) {
            return;
        }
        VoiceUtil.voiceRequestReadConversation(requireActivity(), mUserAccount, conversation);
    }

    /**
     * Get instance of Conversation Log fragment
     *
     * @param userAccount the user device info data will be retrieved for. If null, this fragment
     *     shows a disconnect page
     * @return ConversationLogFragment instance
     */
    public static ConversationListFragment newInstance(@Nullable UserAccount userAccount) {
        Bundle args = new Bundle();
        args.putParcelable(KEY_USER_ACCOUNT, userAccount);
        ConversationListFragment fragment = new ConversationListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Get unique fragment tag for fragment loading data for user device
     *
     * @param userAccount the user device info data will be retrieved for.
     * @return unique fragment tag
     */
    public static String getFragmentTag(@Nullable UserAccount userAccount) {
        int id = userAccount == null ? -1 : userAccount.getId();
        return ConversationListFragment.class.getName() + id;
    }
}
