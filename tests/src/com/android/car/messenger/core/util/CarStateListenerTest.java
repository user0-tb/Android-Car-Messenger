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

package com.android.car.messenger.core.util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.projection.ProjectionStatus;
import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class CarStateListenerTest {

    private CarStateListener mCarStateListener;

    @Mock
    private ProjectionStatus mMockProjectionStatus;
    @Mock
    private ProjectionStatus.MobileDevice mMockDevice;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Context context = ApplicationProvider.getApplicationContext();
        mCarStateListener = new CarStateListener(context);
    }

    @Test
    public void testIsProjectionInActiveForeground_null() {
        mCarStateListener.mProjectionState = ProjectionStatus.PROJECTION_STATE_INACTIVE;
        assertThat(mCarStateListener.isProjectionInActiveForeground(null)).isFalse();
    }

    @Test
    public void testIsProjectionInActiveForeground_active() {
        Bundle extras = new Bundle();
        extras.putInt(CarStateListener.PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND);

        when(mMockDevice.isProjecting()).thenReturn(false);
        when(mMockDevice.getExtras()).thenReturn(extras);

        when(mMockProjectionStatus.isActive()).thenReturn(true);
        when(mMockProjectionStatus.getConnectedMobileDevices())
                .thenReturn(Arrays.asList(mMockDevice));
        mCarStateListener.mProjectionDetails = Arrays.asList(mMockProjectionStatus);
        mCarStateListener.mProjectionState = ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND;

        assertThat(mCarStateListener.isProjectionInActiveForeground("addr")).isFalse();
    }

    @Test
    public void testIsProjectionInActiveForeground_projectingBackground() {
        Bundle extras = new Bundle();
        extras.putInt(CarStateListener.PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                ProjectionStatus.PROJECTION_STATE_ACTIVE_BACKGROUND);

        when(mMockDevice.isProjecting()).thenReturn(true);
        when(mMockDevice.getExtras()).thenReturn(extras);

        when(mMockProjectionStatus.isActive()).thenReturn(true);
        when(mMockProjectionStatus.getConnectedMobileDevices())
                .thenReturn(Arrays.asList(mMockDevice));
        mCarStateListener.mProjectionDetails = Arrays.asList(mMockProjectionStatus);
        mCarStateListener.mProjectionState = ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND;

        assertThat(mCarStateListener.isProjectionInActiveForeground("addr")).isFalse();
    }

    @Test
    public void testIsProjectionInActiveForeground_projectingForeground() {
        Bundle extras = new Bundle();
        extras.putInt(CarStateListener.PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND);

        when(mMockDevice.isProjecting()).thenReturn(true);
        when(mMockDevice.getExtras()).thenReturn(extras);

        when(mMockProjectionStatus.isActive()).thenReturn(true);
        when(mMockProjectionStatus.getConnectedMobileDevices())
                .thenReturn(Arrays.asList(mMockDevice));
        mCarStateListener.mProjectionDetails = Arrays.asList(mMockProjectionStatus);
        mCarStateListener.mProjectionState = ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND;

        assertThat(mCarStateListener.isProjectionInActiveForeground("addr")).isTrue();
    }
}
