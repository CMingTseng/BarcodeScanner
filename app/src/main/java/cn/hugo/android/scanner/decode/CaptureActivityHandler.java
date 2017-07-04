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
import com.google.zxing.Result;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.Collection;
import java.util.Map;

import cn.hugo.android.scanner.R;
import cn.hugo.android.scanner.camera.CameraManager;
import cn.hugo.android.scanner.view.ViewfinderResultPointCallback;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureActivityHandler extends Handler {
    private static final String TAG = CaptureActivityHandler.class.getSimpleName();
    private DecodeInterface activity;
    /**
     * 真正負責掃描任務的核心線程
     */
    private final DecodeThread mDecodethread;

    private State mState;

    private final CameraManager mCameraManager;

    /**
     * 當前掃描的狀態
     */
    private enum State {
        /**
         * 預覽
         */
        PREVIEW,
        /**
         * 掃描成功
         */
        SUCCESS,
        /**
         * 結束掃描
         */
        DONE
    }

    public CaptureActivityHandler(DecodeInterface activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType, ?> baseHints, String characterSet, CameraManager cameraManager) {
        this.activity = activity;
        // 啟動掃描線程
        mDecodethread = new DecodeThread(activity, decodeFormats, baseHints, characterSet, new ViewfinderResultPointCallback(activity.getViewfinderView()));
        mDecodethread.start();
        mState = State.SUCCESS;
        // Start ourselves capturing previews and decoding.
        this.mCameraManager = cameraManager;
        // 開啟相機預覽介面
        cameraManager.startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.restart_preview: // 準備進行下一次掃描
                Log.d(TAG, "Got restart preview message");
                restartPreviewAndDecode();
                break;
            case R.id.decode_succeeded:
                Log.d(TAG, "Got decode succeeded message");
                mState = State.SUCCESS;
                Bundle bundle = message.getData();
                Bitmap barcode = null;
                float scaleFactor = 1.0f;
                if (bundle != null) {
                    byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
                    if (compressedBitmap != null) {
                        barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
                        // Mutable copy:
                        barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);
                    }
                    scaleFactor = bundle.getFloat(DecodeThread.BARCODE_SCALED_FACTOR);
                }

                //FIXME
                activity.handleDecode((Result) message.obj, barcode,
                        scaleFactor);
                break;
            case R.id.decode_failed:
                // We're decoding as fast as possible, so when one decode fails,
                // start another.
                mState = State.PREVIEW;
                mCameraManager.requestPreviewFrame(mDecodethread.getHandler(), R.id.decode);
                break;
            case R.id.return_scan_result: //FIXME
                Log.d(TAG, "Got return scan result message");
//          activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
//          activity.finish();
                break;
            case R.id.launch_product_query:
                Log.d(TAG, "Got product query message");
                String url = (String) message.obj;
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                intent.setData(Uri.parse(url));

//          /**
//           * 這段代碼是zxing項目組想要用chrome打開流覽器流覽url
//           */
//          ResolveInfo resolveInfo = activity.getPackageManager()
//                .resolveActivity(intent,
//                      PackageManager.MATCH_DEFAULT_ONLY);
//          String browserPackageName = null;
//          if (resolveInfo != null && resolveInfo.activityInfo != null) {
//             browserPackageName = resolveInfo.activityInfo.packageName;
//             Log.d(TAG, "Using browser in package " + browserPackageName);
//          }
//
//          // Needed for default Android browser / Chrome only apparently
//          if ("com.android.browser".equals(browserPackageName)
//                || "com.android.chrome".equals(browserPackageName)) {
//             intent.setPackage(browserPackageName);
//             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//             intent.putExtra(Browser.EXTRA_APPLICATION_ID,
//                   browserPackageName);
//          }
//
//          try {
//             activity.startActivity(intent);
//          } catch (ActivityNotFoundException ignored) {
//             Log.w(TAG, "Can't find anything to handle VIEW of URI "
//                   + url);
//          }
                break;
        }
    }

    public void quitSynchronously() {
        mState = State.DONE;
        mCameraManager.stopPreview();
        Message quit = Message.obtain(mDecodethread.getHandler(), R.id.quit);
        quit.sendToTarget();

        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            mDecodethread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.decode_succeeded);
        removeMessages(R.id.decode_failed);
    }

    /**
     * 完成一次掃描後，只需要再調用此方法即可
     */
    private void restartPreviewAndDecode() {
        if (mState == State.SUCCESS) {
            mState = State.PREVIEW;
            // 向decodeThread綁定的handler（DecodeHandler)發送解碼消息
            mCameraManager.requestPreviewFrame(mDecodethread.getHandler(), R.id.decode);
            //FIXME
            activity.drawViewfinder();
        }
	}
}

