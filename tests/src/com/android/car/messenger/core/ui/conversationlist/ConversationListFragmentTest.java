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

package com.android.car.messenger.core.ui.conversationlist;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.drawable.Drawable;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.messenger.R;
import com.android.car.messenger.common.Conversation;
import com.android.car.messenger.core.interfaces.BluetoothState;
import com.android.car.messenger.core.models.UserAccount;
import com.android.car.messenger.testing.TestActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ConversationListFragmentTest {

    private static final int USER_ID = 1;

    private ActivityScenario<TestActivity> mActivityScenario;
    private ConversationListFragment mFragment;
    @Mock
    private UserAccount mMockUserAccount;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mMockUserAccount.getId()).thenReturn(USER_ID);
    }

    private void startFragment(UserAccount userAccount, List<UIConversationItem> conversations,
            int state) {
        mActivityScenario = ActivityScenario.launch(TestActivity.class);
        mActivityScenario.onActivity(activity -> {
            ConversationListViewModel viewModel = new ViewModelProvider(activity).get(
                    ConversationListViewModel.class);

            when(viewModel.getConversations(mMockUserAccount)).thenReturn(
                    new MutableLiveData<>(conversations));
            when(viewModel.getBluetoothStateLiveData()).thenReturn(
                    new MutableLiveData<>(state));

            mFragment = ConversationListFragment.newInstance(userAccount);
            activity.getSupportFragmentManager().beginTransaction().add(
                    R.id.test_fragment_container, mFragment).commit();
        });
    }

    @Test
    public void testOnViewCreated_noUserAccount() {
        startFragment(null, null, BluetoothState.ENABLED);
        onView(withId(R.id.error_message))
                .check(matches(withText(R.string.bluetooth_disconnected)));
        onView(withId(R.id.error_action_button))
                .check(matches(withText(R.string.connect_bluetooth_button_text)));
    }

    @Test
    public void testOnViewCreated_noDevice() {
        startFragment(mMockUserAccount, null, BluetoothState.DISABLED);
        onView(withId(R.id.error_message))
                .check(matches(withText(R.string.bluetooth_disconnected)));
        onView(withId(R.id.error_action_button))
                .check(matches(withText(R.string.connect_bluetooth_button_text)));
    }

    @Test
    public void testOnViewCreated_emptyList() {
        startFragment(mMockUserAccount, Collections.EMPTY_LIST, BluetoothState.ENABLED);
        onView(withId(R.id.empty_message)).check(matches(withText(R.string.no_messages)));
    }

    @Test
    public void testOnViewCreated_populatedList() {
        String id = "id";
        String title = "title";
        String preview = "preview";
        String unreadCount = "5";
        long timestamp = 1000;

        List<UIConversationItem> conversations = new ArrayList<>();
        UIConversationItem item = new UIConversationItem(
                id,
                title,
                preview,
                mock(Drawable.class),
                unreadCount,
                timestamp,
                null,
                true,
                true,
                true,
                true,
                false,
                mock(Conversation.class));
        conversations.add(item);

        startFragment(mMockUserAccount, conversations, BluetoothState.ENABLED);

        onView(withId(R.id.list_view))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.title)).check(matches(withText(title)));
        onView(withId(R.id.unread_badge)).check(matches(withText(unreadCount)));
        onView(withId(R.id.preview)).check(matches(withText(preview)));
        onView(withId(R.id.mute_action_button))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.reply_action_button))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.play_action_button))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    @Test
    public void testGetFragmentTag() {
        assertThat(ConversationListFragment.getFragmentTag(null))
                .isEqualTo(ConversationListFragment.class.getName() + "-1");
        assertThat(ConversationListFragment.getFragmentTag(mMockUserAccount))
                .isEqualTo(ConversationListFragment.class.getName() + USER_ID);
    }
}
