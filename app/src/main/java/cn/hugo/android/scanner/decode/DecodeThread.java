/*
 * Copyright (C) 2008 ZXing authors
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

package cn.hugo.android.scanner.decode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import cn.hugo.android.scanner.config.Config;

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class DecodeThread extends Thread {
    private static final String TAG = DecodeThread.class.getSimpleName();
    public static final String BARCODE_BITMAP = "barcode_bitmap";

    public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

    private final DecodeInterface activity;

    private final Map<DecodeHintType, Object> mHints;

    private Handler mHandler;

    private final CountDownLatch mHandlerInitLatch;

    public DecodeThread(DecodeInterface activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType, ?> baseHints, String characterSet, ResultPointCallback resultPointCallback) {
        this.activity = activity;
        mHandlerInitLatch = new CountDownLatch(1);
        mHints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        if (baseHints != null) {
            mHints.putAll(baseHints);
        }

        // The prefs can't change while the thread is running, so pick them up
        // once here.
        if (decodeFormats == null || decodeFormats.isEmpty()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getContext());
            decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
            if (prefs.getBoolean(Config.KEY_DECODE_1D, false)) {
                decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS);
            }
            if (prefs.getBoolean(Config.KEY_DECODE_QR, false)) {
                decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            }
            if (prefs.getBoolean(Config.KEY_DECODE_DATA_MATRIX, false)) {
                decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
            }
        }
        mHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        if (characterSet != null) {
            mHints.put(DecodeHintType.CHARACTER_SET, characterSet);
        }
        mHints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
        Log.i("DecodeThread", "Hints: " + mHints);
    }

    Handler getHandler() {
        try {
            mHandlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return mHandler;
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new DecodeHandler(activity, mHints);
        mHandlerInitLatch.countDown();
        Looper.loop();
    }
}
