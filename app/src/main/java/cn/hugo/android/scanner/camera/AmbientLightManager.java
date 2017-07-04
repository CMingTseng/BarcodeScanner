/*
 * Copyright (C) 2012 ZXing authors
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

package cn.hugo.android.scanner.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;

/**
 * Detects ambient light and switches on the front light when very dark, and off
 * again when sufficiently light.
 *
 * @author Sean Owen
 * @author Nikolaus Huber
 */
public class AmbientLightManager implements SensorEventListener {
    private static final String TAG = AmbientLightManager.class.getSimpleName();
    private static final float TOO_DARK_LUX = 45.0f;
    private static final float BRIGHT_ENOUGH_LUX = 450.0f;
    private final Context mContext;
    private CameraManager mCameraManager;

    /**
     * 光感測器
     */
    private Sensor mLightSensor;

    public AmbientLightManager(Context context) {
        this.mContext = context;
    }

    public void start(CameraManager cameraManager) {
        this.mCameraManager = cameraManager;
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (FrontLightMode.readPref(sharedPrefs) == FrontLightMode.AUTO) {
            SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mLightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (mLightSensor != null) {
                sensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    public void stop() {
        if (mLightSensor != null) {
            SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(this);
            mCameraManager = null;
            mLightSensor = null;
        }
    }

    /**
     * 該方法會在周圍環境改變後回調，然後根據設置好的臨界值決定是否打開閃光燈
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float ambientLightLux = sensorEvent.values[0];
        if (mCameraManager != null) {
            if (ambientLightLux <= TOO_DARK_LUX) {
                mCameraManager.setTorch(true);
            } else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
                mCameraManager.setTorch(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }
}

