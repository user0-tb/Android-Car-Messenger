/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.messenger;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Receiver that listens for Telephony broadcasts when an SMS is received.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "CM#SmsReceiver";
    private static final Uri SMS_URI = Telephony.Sms.CONTENT_URI;
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "MMM dd,yyyy HH:mm");

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startIntent = new Intent(context, MessengerService.class)
                .setAction(MessengerService.ACTION_RECEIVED_SMS);
        context.startForegroundService(startIntent);

        // If we are the default SMS app, we only care about the
        // Telephony.Sms.Intents.SMS_DELIVER_ACTION
        if (isDefaultSmsApp(context) && Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(
                intent.getAction())) {
            return;
        }

        if (isDefaultSmsApp(context)) parseSmsMessage(context, intent);
        readDatabase(context);
    }

    /**
     * Parses the {@link SmsMessage} from the intent to be inputted into the Telephony Database.
     */
    private void parseSmsMessage(Context context, Intent intent) {
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null) {
            Log.w(TAG, "No sms messages found in the intent");
            return;
        }

        for (SmsMessage sms : messages) {
            insertMessageToTelephonyDb(sms, context, intent);
        }
    }

    private void insertMessageToTelephonyDb(SmsMessage sms, Context context, Intent intent) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, 0);
        values.put(Telephony.Sms.ADDRESS, sms.getDisplayOriginatingAddress());
        values.put(Telephony.Sms.BODY, sms.getMessageBody());
        values.put(Telephony.Sms.DATE, sms.getTimestampMillis());
        values.put(Telephony.Sms.SUBSCRIPTION_ID, sms.getSubId());
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Telephony.Sms.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Telephony.Sms.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Telephony.Sms.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Telephony.Sms.SERVICE_CENTER, sms.getServiceCenterAddress());
        values.put(Telephony.Sms.ERROR_CODE, intent.getIntExtra("errorCode", 0));
        values.put(Telephony.Sms.STATUS, sms.getStatus());

        Uri uri = context.getApplicationContext().getContentResolver().insert(SMS_URI, values);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Finished inserting latest SMS: " + uri);
        }
    }

    /**
     * Reads the Telephony SMS Database, and logs all of the SMS messages that have been received
     * in the last five minutes.
     * @param context
     */
    private void readDatabase(Context context) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) {
            return;
        }

        Long beginningTimeStamp = System.currentTimeMillis() - 300000;
        String timeStamp = DATE_FORMATTER.format(new Date(beginningTimeStamp));
        Log.d(TAG,
                " ------ printing SMSs received after " + timeStamp + "-------- ");

        String smsSelection = Telephony.Sms.DATE + ">=?";
        String[] smsSelectionArgs = {Long.toString(beginningTimeStamp)};
        Cursor cursor = context.getContentResolver().query(SMS_URI, null,
                smsSelection,
                smsSelectionArgs, null /* sortOrder */);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String body = cursor.getString(12);

                Date date = new Date(cursor.getLong(4));
                Log.d(TAG,
                        "_id " + cursor.getInt(0) + " person: " + cursor.getInt(3) + " body: "
                                + body.substring(0, Math.min(body.length(), 17)) + " address: "
                                + cursor.getString(2) + " date: " + DATE_FORMATTER.format(
                                date)
                                + " read: "
                                + cursor.getInt(7));
            }
        }
        Log.d(TAG, " ------ end read table --------");
    }

    private boolean isDefaultSmsApp(Context context) {
        return Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
    }
}
