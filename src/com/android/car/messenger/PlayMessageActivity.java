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
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.messenger.tts.TTSHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls the TTS of the message received.
 */
public class PlayMessageActivity extends Activity {
    private static final String TAG = "PlayMessageActivity";

    public static final String EXTRA_MESSAGE_KEY = "car.messenger.EXTRA_MESSAGE_KEY";
    public static final String EXTRA_SENDER_NAME = "car.messenger.EXTRA_SENDER_NAME";
    public static final String EXTRA_SHOW_REPLY_LIST_FLAG =
            "car.messenger.EXTRA_SHOW_REPLY_LIST_FLAG";
    private View mContainer;
    private View mMessageContainer;
    private View mTextContainer;
    private View mVoicePlate;
    private TextView mLeftButton;
    private TextView mRightButton;
    private ImageView mVoiceIcon;
    private MessengerService mMessengerService;
    private MessengerServiceBroadcastReceiver mMessengerServiceBroadcastReceiver =
            new MessengerServiceBroadcastReceiver();
    private MapMessageMonitor.SenderKey mSenderKey;
    private TTSHelper mTTSHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play_message_layout);
        mContainer = findViewById(R.id.container);
        mMessageContainer = findViewById(R.id.message_container);
        mTextContainer = findViewById(R.id.text_container);
        mVoicePlate = findViewById(R.id.voice_plate);
        mLeftButton = (TextView) findViewById(R.id.left_btn);
        mRightButton = (TextView) findViewById(R.id.right_btn);
        mVoiceIcon = (ImageView) findViewById(R.id.voice_icon);

        mTTSHelper = new TTSHelper(this);
        hideAutoReply();
        setupAutoReply();
        updateViewForMessagePlaying();
    }

    private void setupAutoReply() {
        findViewById(R.id.message1).setOnClickListener(v -> {
            // send auto reply
            Intent intent = new Intent(getBaseContext(), MessengerService.class)
                    .setAction(MessengerService.ACTION_AUTO_REPLY)
                    .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey)
                    .putExtra(
                            MessengerService.EXTRA_REPLY_MESSAGE,
                            getString(R.string.caned_message_driving_right_now));
            startService(intent);

            String messageSent = getString(
                    R.string.message_sent_notice,
                    getIntent().getStringExtra(EXTRA_SENDER_NAME));
            // hide all view and show reply sent notice text
            mContainer.invalidate();
            mMessageContainer.setVisibility(View.GONE);
            mVoicePlate.setVisibility(View.GONE);
            mTextContainer.setVisibility(View.VISIBLE);
            TextView replyNotice = (TextView) findViewById(R.id.reply_notice);
            replyNotice.setText(messageSent);

            // read out the reply sent notice. Finish activity after TTS is done.
            List<CharSequence> ttsMessages = new ArrayList<>();
            ttsMessages.add(messageSent);
            mTTSHelper.requestPlay(ttsMessages,
                    new TTSHelper.Listener() {
                        @Override
                        public void onTTSStarted() {
                        }

                        @Override
                        public void onTTSStopped(boolean error) {
                            if (error) {
                                Log.w(TAG, "TTS error.");
                            }
                            finish();
                        }
                    });
        });
    }

    private void showAutoReply() {
        mContainer.invalidate();
        mMessageContainer.setVisibility(View.VISIBLE);
        mLeftButton.setText(getString(R.string.action_close_messages));
        mLeftButton.setOnClickListener(v -> hideAutoReply());
    }

    private void hideAutoReply() {
        mContainer.invalidate();
        mMessageContainer.setVisibility(View.GONE);
        mLeftButton.setText(getString(R.string.action_reply));
        mLeftButton.setOnClickListener(v -> showAutoReply());
    }

    /**
     * If there's a touch outside the voice plate, exit the activity.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (event.getX() < mContainer.getX()
                    || event.getX() > mContainer.getX() + mContainer.getWidth()
                    || event.getY() < mContainer.getY()
                    || event.getY() > mContainer.getY() + mContainer.getHeight()) {
                finish();
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to LocalService
        Intent intent = new Intent(this, MessengerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mMessengerServiceBroadcastReceiver.start();
        mSenderKey = getIntent().getParcelableExtra(EXTRA_MESSAGE_KEY);
        playMessage();
        if (getIntent().getBooleanExtra(EXTRA_SHOW_REPLY_LIST_FLAG, false)) {
            showAutoReply();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTTSHelper.cleanup();
        mMessengerServiceBroadcastReceiver.cleanup();
    }

    private void playMessage() {
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_PLAY_MESSAGES)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey);
        startService(intent);
    }

    private void stopMessage() {
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_STOP_PLAYOUT)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey);
        startService(intent);
    }

    private void updateViewForMessagePlaying() {
        mRightButton.setText(getString(R.string.action_stop));
        mRightButton.setOnClickListener(v -> stopMessage());
        mVoiceIcon.setImageResource(R.drawable.ic_voice_out);
    }

    private void updateViewFoeMessageStopped() {
        mRightButton.setText(getString(R.string.action_repeat));
        mRightButton.setOnClickListener(v -> playMessage());
        mVoiceIcon.setImageResource(R.drawable.ic_voice_stopped);
    }

    private class MessengerServiceBroadcastReceiver extends BroadcastReceiver {
        private final IntentFilter mIntentFilter;
        MessengerServiceBroadcastReceiver() {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(MapMessageMonitor.ACTION_MESSAGE_PLAY_START);
            mIntentFilter.addAction(MapMessageMonitor.ACTION_MESSAGE_PLAY_STOP);
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
                case MapMessageMonitor.ACTION_MESSAGE_PLAY_START:
                    updateViewForMessagePlaying();
                    break;
                case MapMessageMonitor.ACTION_MESSAGE_PLAY_STOP:
                    updateViewFoeMessageStopped();
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
                updateViewForMessagePlaying();
            } else {
                updateViewFoeMessageStopped();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mMessengerService = null;
        }
    };
}
