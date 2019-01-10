package com.android.car.messenger;


import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.android.car.messenger.bluetooth.BluetoothMonitor;
import com.android.car.messenger.log.L;
import com.android.car.messenger.MessengerDelegate.SenderKey;

/** Service responsible for handling SMS messaging events from paired Bluetooth devices. */
public class MessengerService extends Service {
    private final static String TAG = "CM#MessengerService";

    /* ACTIONS */
    /** Used to start this service at boot-complete. Takes no arguments. */
    public static final String ACTION_START = "com.android.car.messenger.ACTION_START";

    /** Used to reply to message with voice input; triggered by an assistant. */
    public static final String ACTION_VOICE_REPLY = "com.android.car.messenger.ACTION_VOICE_REPLY";

    /** Used to stop further audio notifications from the conversation. */
    public static final String ACTION_MUTE_CONVERSATION =
            "com.android.car.messenger.ACTION_MUTE_CONVERSATION";

    /** Used to resume further audio notifications from the conversation. */
    public static final String ACTION_UNMUTE_CONVERSATION =
            "com.android.car.messenger.ACTION_UNMUTE_CONVERSATION";

    /** Used to clear notification state when user dismisses notification. */
    public static final String ACTION_CLEAR_NOTIFICATION_STATE =
            "com.android.car.messenger.ACTION_CLEAR_NOTIFICATION_STATE";

    /** Used to mark a notification as read **/
    public static final String ACTION_MARK_AS_READ =
            "com.android.car.messenger.ACTION_MARK_AS_READ";

    /* EXTRAS */
    /** Key under which the {@link SenderKey} is provided. */
    public static final String EXTRA_SENDER_KEY = "com.android.car.messenger.EXTRA_SENDER_KEY";

    /**
     * The resultKey of the {@link RemoteInput} which is sent in the reply callback {@link Action}.
     */
    public static final String REMOTE_INPUT_KEY = "REMOTE_INPUT_KEY";

    /* NOTIFICATIONS */
    static final String SMS_UNMUTED_CHANNEL_ID = "SMS_UNMUTED_CHANNEL_ID";
    static final String SMS_MUTED_CHANNEL_ID = "SMS_MUTED_CHANNEL_ID";
    private static final String APP_RUNNING_CHANNEL_ID = "APP_RUNNING_CHANNEL_ID";
    private static final int SERVICE_STARTED_NOTIFICATION_ID = Integer.MAX_VALUE;

    /** Delegate class used to handle this services' actions */
    private MessengerDelegate mMessengerDelegate;

    /** Notifies this service of new bluetooth actions */
    private BluetoothMonitor mBluetoothMonitor;

    /* Binding boilerplate */
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        MessengerService getService() {
            return MessengerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.d(TAG, "onCreate");

        mMessengerDelegate = new MessengerDelegate(this);
        mBluetoothMonitor = new BluetoothMonitor(this);
        mBluetoothMonitor.registerListener(mMessengerDelegate);
        sendServiceRunningNotification();
    }



    private void sendServiceRunningNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            L.e(TAG, "Failed to get NotificationManager instance");
            return;
        }

        // Create notification channel for app running notification
        {
            NotificationChannel appRunningNotificationChannel =
                    new NotificationChannel(APP_RUNNING_CHANNEL_ID,
                            getString(R.string.app_running_msg_channel_name),
                            NotificationManager.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(appRunningNotificationChannel);
        }

        // Create a notification channel for unmuted SMS messages
        {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            NotificationChannel unmutedChannel = new NotificationChannel(SMS_UNMUTED_CHANNEL_ID,
                    getString(R.string.sms_unmuted_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            unmutedChannel.setDescription(getString(R.string.sms_unmuted_channel_description));
            unmutedChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes);
            notificationManager.createNotificationChannel(unmutedChannel);
        }

        // Create a notification channel for muted SMS messages
        {
            NotificationChannel mutedChannel = new NotificationChannel(SMS_MUTED_CHANNEL_ID,
                    getString(R.string.sms_muted_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            mutedChannel.setDescription(getString(R.string.sms_muted_channel_description));
            mutedChannel.setSound(null, null);
            notificationManager.createNotificationChannel(mutedChannel);
        }

        final Notification notification =
                new NotificationCompat.Builder(this, APP_RUNNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_voice_out)
                .setContentTitle(getString(R.string.app_running_msg_notification_title))
                .setContentText(getString(R.string.app_running_msg_notification_content))
                .build();

        startForeground(SERVICE_STARTED_NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        L.d(TAG, "onDestroy");

        mBluetoothMonitor.cleanup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int result = START_STICKY;

        if (intent == null || intent.getAction() == null) return result;

        final String action = intent.getAction();

        if (!hasRequiredArgs(intent)) {
            L.e(TAG, "Dropping command: %s. Reason: Missing required argument.", action);
            return result;
        }

        switch (action) {
            case ACTION_START:
                // NO-OP
                break;
            case ACTION_VOICE_REPLY:
                voiceReply(intent);
                break;
            case ACTION_MUTE_CONVERSATION:
                muteConversation(intent);
                break;
            case ACTION_UNMUTE_CONVERSATION:
                unmuteConversation(intent);
                break;
            case ACTION_CLEAR_NOTIFICATION_STATE:
                clearNotificationState(intent);
                break;
            case ACTION_MARK_AS_READ:
                markAsRead(intent);
                break;
            default:
                L.w(TAG, "Unsupported action: %s", action);
        }

        return result;
    }

    /**
     * Checks that the intent has all of the required arguments for its requested action.
     *
     * @param intent the intent to check
     * @return true if the intent has all of the required {@link Bundle} args for its action
     */
    private static boolean hasRequiredArgs(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_VOICE_REPLY:
            case ACTION_MUTE_CONVERSATION:
            case ACTION_UNMUTE_CONVERSATION:
            case ACTION_CLEAR_NOTIFICATION_STATE:
            case ACTION_MARK_AS_READ:
                if (!intent.hasExtra(EXTRA_SENDER_KEY)) {
                    L.w(TAG, "Intent %s missing sender-key extra.", intent.getAction());
                    return false;
                }
                return true;
            default:
                // For unknown actions, default to true. We'll report an error for these later.
                return true;
        }
    }

    /**
     * Sends a reply, meant to be used from a caller originating from voice input.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} and
     *               a {@link RemoteInput} with {@link MessengerService#REMOTE_INPUT_KEY} resultKey
     */
    public void voiceReply(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        final Bundle bundle = RemoteInput.getResultsFromIntent(intent);
        if (bundle == null) {
            L.e(TAG, "Dropping voice reply. Received null RemoteInput result!");
            return;
        }
        final CharSequence message = bundle.getCharSequence(REMOTE_INPUT_KEY);
        L.d(TAG, "voiceReply");
        if (!TextUtils.isEmpty(message)) {
            mMessengerDelegate.sendMessage(senderKey, message.toString());
        }
    }

    /**
     * Mute the conversation associated with a given sender key.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void muteConversation(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "muteConversation");
        mMessengerDelegate.toggleMute(senderKey, true);
    }

    /**
     * Unmute the conversation associated with a given sender key.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void unmuteConversation(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "unmuteConversation");
        mMessengerDelegate.toggleMute(senderKey, false);
    }

    /**
     * Clears notification(s) associated with a given sender key.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void clearNotificationState(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "clearNotificationState");
        mMessengerDelegate.clearNotifications(senderKey);
    }

    /**
     * Mark a conversation associated with a given sender key as read.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void markAsRead(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "markAsRead");
        mMessengerDelegate.markAsRead(senderKey);
    }
}
