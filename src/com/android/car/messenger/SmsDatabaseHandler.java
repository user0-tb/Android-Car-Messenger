package com.android.car.messenger;


import static com.android.car.messenger.MessengerDelegate.getContactId;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import com.android.car.messenger.log.L;

import java.util.Locale;

/**
 * Reads and writes SMS Messages into the Telephony.SMS Database.
 */
class SmsDatabaseHandler {
    private static final String TAG = "CM#SmsDatabaseHandler";
    private static final int MESSAGE_NOT_FOUND = -1;
    private static final int DUPLICATE_MESSAGES_FOUND = -2;
    private static final Uri SMS_URI = Telephony.Sms.CONTENT_URI;

    private final ContentResolver mContentResolver;

    protected SmsDatabaseHandler(ContentResolver contentResolver) {
        // TODO: check that we have permissions. 
        mContentResolver = contentResolver;
    }

    protected void addOrUpdate(MapMessage message) {
        int messageIndex = findMessageIndex(message);

        if (messageIndex == DUPLICATE_MESSAGES_FOUND) {
            merge(message);
            L.d(TAG, "Message has more than one duplicate in Telephony Database: %s",
                    message.toString());
        } else if (messageIndex == MESSAGE_NOT_FOUND) {
            mContentResolver.insert(SMS_URI, buildMessageContentValues(message));
        } else {
            update(messageIndex, buildMessageContentValues(message));
        }
    }

    protected void removeMessagesForDevice(String address) {
        String smsSelection =
                String.format(Locale.US,
                        "%s=\'%s\'",
                        Telephony.Sms.ADDRESS,
                        address);
        mContentResolver.delete(SMS_URI, smsSelection, null /* selectionsArgs */);
    }

    /** Removes multiple previous copies, and inserts the new message. **/
    private void merge(MapMessage message) {
        String smsSelection =
                String.format(Locale.US,
                        "%s='%s' AND %s LIKE \'%s\'",
                        Telephony.Sms.ADDRESS,
                        message.getDeviceAddress(),
                        Telephony.Sms.BODY,
                        message.getMessageText());
        mContentResolver.delete(SMS_URI, smsSelection, null /* selectionArgs */);
        mContentResolver.insert(SMS_URI, buildMessageContentValues(message));
    }

    private int findMessageIndex(MapMessage message) {
        String smsSelection =
                String.format(Locale.US,
                        "%s=\'%s\' AND %s LIKE \'%s\'",
                        Telephony.Sms.ADDRESS,
                        message.getDeviceAddress(),
                        Telephony.Sms.BODY,
                        message.getMessageText());
        String[] projection = {Telephony.TextBasedSmsChangesColumns.ID};
        Cursor cursor = mContentResolver.query(SMS_URI, projection, smsSelection,
                null /* selectionArgs */, null /* sortOrder */);
        if (cursor != null && cursor.getCount() != 0) {
            if (cursor.getCount() == 1) {
                return cursor.getInt(
                    cursor.getColumnIndex(Telephony.TextBasedSmsChangesColumns.ID));
            } else {
                return DUPLICATE_MESSAGES_FOUND;
            }
        } else {
            return MESSAGE_NOT_FOUND;
        }
    }

    private void update(int messageIndex, ContentValues value) {
        final String smsSelection =
                String.format(
                        Locale.US,
                        "%s=%d",
                        Telephony.TextBasedSmsChangesColumns.ID,
                        messageIndex);

        mContentResolver.update(SMS_URI, value, smsSelection, null /*selectionArgs*/);
    }

    /** Create the ContentValues object using message info, following SMS columns **/
    private ContentValues buildMessageContentValues(MapMessage message) {
        ContentValues newMessage = new ContentValues();
        newMessage.put(Telephony.Sms.BODY, message.getMessageText());
        newMessage.put(Telephony.Sms.DATE, message.getReceiveTime());
        newMessage.put(Telephony.Sms.ADDRESS, message.getDeviceAddress());
        // TODO: if contactId is null, add it.
        newMessage.put(Telephony.Sms.PERSON,
                getContactId(mContentResolver,
                        message.getSenderContactUri()));
        return newMessage;
    }
}
