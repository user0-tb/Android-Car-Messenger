package com.android.car.messenger;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothAdapter;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowNotificationManager.class}, sdk = {
        VERSION_CODES.O})
public class MessengerDelegateTest {

    private static final String BLUETOOTH_ADDRESS_ONE = "FA:F8:14:CA:32:39";
    private static final String BLUETOOTH_ADDRESS_TWO = "FA:F8:33:44:32:39";

    @Mock
    private BluetoothDevice mMockBluetoothDeviceOne;
    @Mock
    private BluetoothDevice mMockBluetoothDeviceTwo;
    @Mock
    AppOpsManager mMockAppOpsManager;

    private Context mContext = RuntimeEnvironment.application;
    private MessengerDelegate mMessengerDelegate;
    private ShadowBluetoothAdapter mShadowBluetoothAdapter;
    private Intent mMessageOneIntent;
    private MapMessage mMessageOne;
    private MessengerDelegate.MessageKey mMessageOneKey;
    private MessengerDelegate.SenderKey mSenderKey;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Add AppOps permissions required to write to Telephony.SMS database.
        when(mMockAppOpsManager.checkOpNoThrow(anyInt(), anyInt(), anyString())).thenReturn(
                AppOpsManager.MODE_DEFAULT);
        Shadows.shadowOf(RuntimeEnvironment.application)
                .setSystemService(Context.APP_OPS_SERVICE, mMockAppOpsManager);

        when(mMockBluetoothDeviceOne.getAddress()).thenReturn(BLUETOOTH_ADDRESS_ONE);
        when(mMockBluetoothDeviceTwo.getAddress()).thenReturn(BLUETOOTH_ADDRESS_TWO);
        mShadowBluetoothAdapter = Shadow.extract(BluetoothAdapter.getDefaultAdapter());
        mShadowBluetoothAdapter.setBondedDevices(
                new HashSet<>(Arrays.asList(mMockBluetoothDeviceOne)));

        mMessageOneIntent = createMessageIntent(mMockBluetoothDeviceOne, "mockHandle",
                "510-111-2222", "testSender",
                "Hello");
        mMessageOne = MapMessage.parseFrom(mMessageOneIntent);
        mMessageOneKey = new MessengerDelegate.MessageKey(mMessageOne);
        mSenderKey = new MessengerDelegate.SenderKey(mMessageOne);

        mMessengerDelegate = new MessengerDelegate(mContext);
    }

    @Test
    public void testDeviceConnections() {
        assertThat(mMessengerDelegate.mConnectedDevices).contains(BLUETOOTH_ADDRESS_ONE);
        assertThat(mMessengerDelegate.mConnectedDevices).hasSize(1);

        mMessengerDelegate.onDeviceConnected(mMockBluetoothDeviceTwo);
        assertThat(mMessengerDelegate.mConnectedDevices).contains(BLUETOOTH_ADDRESS_TWO);
        assertThat(mMessengerDelegate.mConnectedDevices).hasSize(2);

        mMessengerDelegate.onDeviceConnected(mMockBluetoothDeviceOne);
        assertThat(mMessengerDelegate.mConnectedDevices).hasSize(2);
    }

    @Test
    public void testOnDeviceDisconnected_notConnectedDevice() {
        mMessengerDelegate.onDeviceDisconnected(mMockBluetoothDeviceTwo);

        assertThat(mMessengerDelegate.mConnectedDevices.contains(BLUETOOTH_ADDRESS_ONE)).isTrue();
        assertThat(mMessengerDelegate.mConnectedDevices).hasSize(1);
    }

    @Test
    public void testOnDeviceDisconnected_connectedDevice() {
        mShadowBluetoothAdapter.setBondedDevices(
                new HashSet<>(Arrays.asList(mMockBluetoothDeviceOne, mMockBluetoothDeviceTwo)));
        mMessengerDelegate = new MessengerDelegate(mContext);

        mMessengerDelegate.onDeviceDisconnected(mMockBluetoothDeviceOne);

        assertThat(mMessengerDelegate.mConnectedDevices.contains(BLUETOOTH_ADDRESS_TWO)).isTrue();
        assertThat(mMessengerDelegate.mConnectedDevices).hasSize(1);
    }

    @Test
    public void testOnDeviceDisconnected_connectedDevice_withMessages() {
        // Disconnect a connected device, and ensure its messages are removed.
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        mMessengerDelegate.onDeviceDisconnected(mMockBluetoothDeviceOne);

        assertThat(mMessengerDelegate.mMessages).isEmpty();
        assertThat(mMessengerDelegate.mNotificationInfos).isEmpty();
    }

    @Test
    public void testOnDeviceDisconnected_notConnectedDevice_withMessagesFromAnotherDevice() {
        // Disconnect a not connected device, and ensure device one's messages are still saved.
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        mMessengerDelegate.onDeviceDisconnected(mMockBluetoothDeviceTwo);

        assertThat(mMessengerDelegate.mMessages).hasSize(1);
        assertThat(mMessengerDelegate.mNotificationInfos).hasSize(1);
    }

    @Test
    public void testOnDeviceDisconnected_connectedDevice_withMessagesFromAnotherDevice() {
        mShadowBluetoothAdapter.setBondedDevices(
                new HashSet<>(Arrays.asList(mMockBluetoothDeviceOne, mMockBluetoothDeviceTwo)));
        mMessengerDelegate = new MessengerDelegate(mContext);

        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        mMessengerDelegate.onDeviceDisconnected(mMockBluetoothDeviceTwo);

        assertThat(mMessengerDelegate.mMessages).hasSize(1);
        assertThat(mMessengerDelegate.mNotificationInfos).hasSize(1);
    }

    @Test
    public void testOnMessageReceived_newMessage() {
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);

        assertThat(mapMessageEquals(mMessageOne,
                mMessengerDelegate.mMessages.get(mMessageOneKey))).isTrue();
        assertThat(mMessengerDelegate.mNotificationInfos.containsKey(mSenderKey)).isTrue();
    }

    @Test
    public void testOnMessageReceived_duplicateMessage() {
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        MessengerDelegate.NotificationInfo info = mMessengerDelegate.mNotificationInfos.get(
                mSenderKey);
        assertThat(info.mMessageKeys).hasSize(1);
    }

    @Test
    public void testClearNotification_keepsNotificationData() {
        mMessengerDelegate.onMessageReceived(mMessageOneIntent);
        mMessengerDelegate.clearNotifications(key -> key.equals(mSenderKey));
        MessengerDelegate.NotificationInfo info = mMessengerDelegate.mNotificationInfos.get(
                mSenderKey);
        assertThat(info.mMessageKeys).hasSize(1);

        assertThat(mMessengerDelegate.mMessages.containsKey(mMessageOneKey)).isTrue();
    }

    private Intent createMessageIntent(BluetoothDevice device, String handle, String senderUri,
            String senderName, String messageText) {
        Intent intent = new Intent();
        intent.setAction(BluetoothMapClient.ACTION_MESSAGE_RECEIVED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE, handle);
        intent.putExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_URI, senderUri);
        intent.putExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_NAME, senderName);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, messageText);
        return intent;
    }

    /**
     * Checks to see if all properties, besides the timestamp, of two {@link MapMessage}s are equal.
     **/
    private boolean mapMessageEquals(MapMessage expected, MapMessage observed) {
        return expected.getDeviceAddress().equals(observed.getDeviceAddress())
                && expected.getHandle().equals(observed.getHandle())
                && expected.getSenderName().equals(observed.getSenderName())
                && expected.getSenderContactUri().equals(observed.getSenderContactUri())
                && expected.getMessageText().equals(observed.getMessageText());
    }
}
