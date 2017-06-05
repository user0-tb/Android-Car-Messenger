/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.car.messenger;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

/**
 * Controls the TTS of the message received.
 */
public class PlayMessageActivity extends Activity {
    public static final String MESSAGE_KEY = "car.messenger.MESSAGE_KEY";
    private TextView mPlayPauseBtn;
    private MessengerService mMessengerService;
    private MessengerServiceBroadcastReceiver mMessengerServiceBroadcastReceiver =
            new MessengerServiceBroadcastReceiver();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play_message_layout);
        mPlayPauseBtn = (TextView) findViewById(R.id.play_pause_btn);
        TextView exitBtn = (TextView) findViewById(R.id.exit_btn);
        exitBtn.setOnClickListener(v -> finish());
        mPlayPauseBtn.setText(getString(R.string.action_play));
        mPlayPauseBtn.setOnClickListener(v -> playMessage());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, MessengerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mMessengerServiceBroadcastReceiver.start();
        playMessage();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMessengerServiceBroadcastReceiver.cleanup();
    }

    private void playMessage() {
        MapMessageMonitor.SenderKey senderKey = getIntent().getParcelableExtra(MESSAGE_KEY);
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_PLAY_MESSAGES)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, senderKey);
        startService(intent);
    }

    private class MessengerServiceBroadcastReceiver extends BroadcastReceiver {
        private final IntentFilter mIntentFilter;
        MessengerServiceBroadcastReceiver() {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(MessengerService.ACTION_PLAY_MESSAGES_STARTED);
            mIntentFilter.addAction(MessengerService.ACTION_PLAY_MESSAGES_STOPPED);
        }

        void start() {
            registerReceiver(this, mIntentFilter);
        }

        void cleanup() {
            unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MessengerService.ACTION_PLAY_MESSAGES_STARTED:
                    mPlayPauseBtn.setText(getString(R.string.action_stop));
                    break;
                case MessengerService.ACTION_PLAY_MESSAGES_STOPPED:
                    mPlayPauseBtn.setText(getString(R.string.action_play));
                    break;
                default:
                    break;
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MessengerService.LocalBinder binder = (MessengerService.LocalBinder) service;
            mMessengerService = binder.getService();
            if (mMessengerService.isPlaying()) {
                mPlayPauseBtn.setText(getString(R.string.action_stop));
            } else {
                mPlayPauseBtn.setText(getString(R.string.action_play));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mMessengerService = null;
        }
    };
}
