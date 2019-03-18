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
 * limitations under the License.
 */

package com.android.car.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Minimal receiver that starts up MessengerService on boot-completion.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Android Automotive has a User 0 always running as a background user, running low level
        // Android processes. Because of this, they need a foreground user to ensure User 0 is
        // always in background.
        // This foreground user is either the last active user, or in the first boot which doesn't
        // have this, User 10.
        // Messenger Service should only be started for the active foreground user, not background
        // system users.
        if (!context.getUser().isSystem()) {
            Intent startIntent = new Intent(context, MessengerService.class)
                    .setAction(MessengerService.ACTION_START);
            context.startForegroundService(startIntent);
        }
    }
}
