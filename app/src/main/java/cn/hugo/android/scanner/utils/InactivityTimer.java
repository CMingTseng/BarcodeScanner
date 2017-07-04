/*
 * Copyright (C) 2010 ZXing authors
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

package cn.hugo.android.scanner.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.util.Log;

import cn.hugo.android.scanner.common.Runnable;

/**
 * Finishes an activity after a period of inactivity if the device is on battery
 * power. <br/>
 * <br/>
 * <p>
 * 該活動監控器全程監控掃描活躍狀態，與CaptureActivity生命週期相同
 */
public class InactivityTimer {
    private static final String TAG = InactivityTimer.class.getSimpleName();

    /**
     * 如果在5min內掃描器沒有被使用過，則自動finish掉activity
     */
    private static final long INACTIVITY_DELAY_MS = 5 * 60 * 1000L;

    /**
     * 在本app中，此activity即為CaptureActivity
     */
    private final Activity mActivity;
    /**
     * 接受系統廣播：手機是否連通電源
     */
    private final BroadcastReceiver mPowerStatusReceiver;
    private boolean mRegistered;
    private AsyncTask<?, ?, ?> mInactivityTask;

    public InactivityTimer(Activity activity) {
        this.mActivity = activity;
        mPowerStatusReceiver = new PowerStatusReceiver();
        mRegistered = false;
        onActivity();
    }

    public InactivityTimer(Context context) {
        this.mActivity = (Activity) context;
        mPowerStatusReceiver = new PowerStatusReceiver();
        mRegistered = false;
        onActivity();
    }

    /**
     * 首先終止之前的監控任務，然後新起一個監控任務
     */
    public synchronized void onActivity() {
        cancel();
        mInactivityTask = new InactivityAsyncTask();
        Runnable.execAsync(mInactivityTask);
    }

    public synchronized void onPause() {
        cancel();
        if (mRegistered) {
            mActivity.unregisterReceiver(mPowerStatusReceiver);
            mRegistered = false;
        } else {
            Log.w(TAG, "PowerStatusReceiver was never mRegistered?");
        }
    }

    public synchronized void onResume() {
        if (mRegistered) {
            Log.w(TAG, "PowerStatusReceiver was already mRegistered?");
        } else {
            mActivity.registerReceiver(mPowerStatusReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            mRegistered = true;
        }
        onActivity();
    }

    /**
     * 取消監控任務
     */
    private synchronized void cancel() {
        AsyncTask<?, ?, ?> task = mInactivityTask;
        if (task != null) {
            task.cancel(true);
            mInactivityTask = null;
        }
    }

    public void shutdown() {
        cancel();
    }

    /**
     * 監聽是否連通電源的系統廣播。如果連通電源，則停止監控任務，否則重啟監控任務
     */
    private final class PowerStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                // 0 indicates that we're on battery
                boolean onBatteryNow = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) <= 0;
                if (onBatteryNow) {
                    InactivityTimer.this.onActivity();
                } else {
                    InactivityTimer.this.cancel();
                }
            }
        }
    }

    /**
     * 該任務很簡單，就是在INACTIVITY_DELAY_MS時間後終結activity
     */
    private final class InactivityAsyncTask extends AsyncTask<Object, Object, Object> {
        @Override
        protected Object doInBackground(Object... objects) {
            try {
                Thread.sleep(INACTIVITY_DELAY_MS);
                Log.i(TAG, "Finishing activity due to inactivity");
                mActivity.finish();
            } catch (InterruptedException e) {
                // continue without killing
            }
            return null;
        }
    }
}

