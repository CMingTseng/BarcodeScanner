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

package cn.hugo.android.scanner.camera;

import com.google.zxing.PlanarYUVLuminanceSource;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding. <br/>
 * <br/>
 *
 * 該類封裝了相機的所有服務並且是該app中唯一與相機打交道的類
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class CameraManager {
    private static final String TAG = CameraManager.class.getSimpleName();
    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920
    private final Context mContext;
    private final CameraConfigurationManager mCameraConfigurationManager;
    private Camera mCamera;
    private AutoFocusManager mAutoFocusManager;
    private Rect mFramingRect;
    private Rect mFramingRectInPreview;
    private boolean mInitialized;
    private boolean mPreviewing;
    private int mRequestedFramingRectWidth;
    private int mRequestedFramingRectHeight;

    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private final PreviewCallback mPreviewCallback;

    public CameraManager(Context context) {
        this.mContext = context;
        this.mCameraConfigurationManager = new CameraConfigurationManager(context);
        mPreviewCallback = new PreviewCallback(mCameraConfigurationManager);
    }

    /**
     * Opens the mCamera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the mCamera will draw preview frames into.
     * @throws IOException Indicates the mCamera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder) throws IOException {
        Camera camera = mCamera;
        if (camera == null) {
            // 獲取手機背面的攝像頭
            camera = OpenCameraInterface.open();
            if (camera == null) {
                throw new IOException();
            }
            mCamera = camera;
        }

        // 設置攝像頭預覽view
        camera.setPreviewDisplay(holder);

        if (!mInitialized) {
            mInitialized = true;
            mCameraConfigurationManager.initFromCameraParameters(camera);
            if (mRequestedFramingRectWidth > 0 && mRequestedFramingRectHeight > 0) {
                setManualFramingRect(mRequestedFramingRectWidth, mRequestedFramingRectHeight);
                mRequestedFramingRectWidth = 0;
                mRequestedFramingRectHeight = 0;
            }
        }

        Camera.Parameters parameters = camera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these temporarily
        try {
            mCameraConfigurationManager.setDesiredCameraParameters(camera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved mCamera params: " + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = camera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    camera.setParameters(parameters);
                    mCameraConfigurationManager.setDesiredCameraParameters(camera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }
    }

    public synchronized void openDriver(SurfaceTexture surfaceTexture) throws IOException {
        Camera camera = mCamera;
        if (camera == null) {
            // 獲取手機背面的攝像頭
            camera = OpenCameraInterface.open();
            if (camera == null) {
                throw new IOException();
            }
            mCamera = camera;
        }

        // 設置攝像頭預覽view
        camera.setPreviewTexture(surfaceTexture);
        if (!mInitialized) {
            mInitialized = true;
            mCameraConfigurationManager.initFromCameraParameters(camera);
            if (mRequestedFramingRectWidth > 0 && mRequestedFramingRectHeight > 0) {
                setManualFramingRect(mRequestedFramingRectWidth, mRequestedFramingRectHeight);
                mRequestedFramingRectWidth = 0;
                mRequestedFramingRectHeight = 0;
            }
        }

        Camera.Parameters parameters = camera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these temporarily
        try {
            mCameraConfigurationManager.setDesiredCameraParameters(camera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved mCamera params: " + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = camera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    camera.setParameters(parameters);
                    mCameraConfigurationManager.setDesiredCameraParameters(camera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }

    }

    public synchronized boolean isOpen() {
        return mCamera != null;
    }

    /**
     * Closes the mCamera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            // Make sure to clear these each time we close the mCamera, so that
            // any scanning rect
            // requested by intent is forgotten.
            mFramingRect = null;
            mFramingRectInPreview = null;
        }
    }

    /**
     * Asks the mCamera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        Camera theCamera = mCamera;
        if (theCamera != null && !mPreviewing) {
            // Starts capturing and drawing preview frames to the screen
            // Preview will not actually start until a surface is supplied with
            // setPreviewDisplay(SurfaceHolder) or
            // setPreviewTexture(SurfaceTexture).
            theCamera.startPreview();
            mPreviewing = true;
            mAutoFocusManager = new AutoFocusManager(mContext, mCamera);
        }
    }

    /**
     * Tells the mCamera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (mAutoFocusManager != null) {
            mAutoFocusManager.stop();
            mAutoFocusManager = null;
        }
        if (mCamera != null && mPreviewing) {
            mCamera.stopPreview();
            mPreviewCallback.setHandler(null, 0);
            mPreviewing = false;
        }
    }

    /**
     * Convenience method for
     * {@link }
     */
    public synchronized void setTorch(boolean newSetting) {
        if (newSetting != mCameraConfigurationManager.getTorchState(mCamera)) {
            if (mCamera != null) {
                if (mAutoFocusManager != null) {
                    mAutoFocusManager.stop();
                }
                mCameraConfigurationManager.setTorch(mCamera, newSetting);
                if (mAutoFocusManager != null) {
                    mAutoFocusManager.start();
                }
            }
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively. <br/>
     *
     * 兩個綁定操作：<br/>
     * 1：將handler與回調函數綁定；<br/>
     * 2：將相機與回調函數綁定<br/>
     * 綜上，該函數的作用是當相機的預覽介面準備就緒後就會調用hander向其發送傳入的message
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public synchronized void requestPreviewFrame(Handler handler, int message) {
        Camera theCamera = mCamera;
        if (theCamera != null && mPreviewing) {
            mPreviewCallback.setHandler(handler, message);
            // 綁定相機回調函數，當預覽介面準備就緒後會回調Camera.PreviewCallback.onPreviewFrame
            theCamera.setOneShotPreviewCallback(mPreviewCallback);
        }
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user
     * where to place the barcode. This target helps with alignment as well as
     * forces the user to hold the device far enough away to ensure the image
     * will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public synchronized Rect getFramingRect() {
        if (mFramingRect == null) {
            if (mCamera == null) {
                return null;
            }
            Point screenResolution = mCameraConfigurationManager.getScreenResolution();
            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
            // 將掃描框設置成一個正方形
            int height = width;
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated framing rect: " + mFramingRect);
        }

        return mFramingRect;
    }

    /**
     * Target 5/8 of each dimension<br/>
     * 計算結果在hardMin~hardMax之間
     */
    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        int dim = 5 * resolution / 8; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        if (dim > hardMax) {
            return hardMax;
        }
        return dim;
    }

    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview
     * frame, not UI / screen.
     */
    public synchronized Rect getFramingRectInPreview() {
        if (mFramingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            Rect rect = new Rect(framingRect);
            Point cameraResolution = mCameraConfigurationManager.getCameraResolution();
            Point screenResolution = mCameraConfigurationManager.getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            rect.left = rect.left * cameraResolution.y / screenResolution.x;
            rect.right = rect.right * cameraResolution.y / screenResolution.x;
            rect.top = rect.top * cameraResolution.x / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
            mFramingRectInPreview = rect;
            Log.d(TAG, "Calculated mFramingRectInPreview rect: " + mFramingRectInPreview);
            Log.d(TAG, "cameraResolution: " + cameraResolution);
            Log.d(TAG, "screenResolution: " + screenResolution);
        }
        return mFramingRectInPreview;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (mInitialized) {
            Point screenResolution = mCameraConfigurationManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + mFramingRect);
            mFramingRectInPreview = null;
        } else {
            mRequestedFramingRectWidth = width;
            mRequestedFramingRectHeight = height;
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = getFramingRectInPreview();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
    }

    /**
     * 焦點放小
     */
    public void zoomOut() {
        if (mCamera != null && mCamera.getParameters().isZoomSupported()) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getZoom() <= 0) {
                return;
            }
            parameters.setZoom(parameters.getZoom() - 1);
            mCamera.setParameters(parameters);
        }
    }

    /**
     * 焦點放大
     */
    public void zoomIn() {
        if (mCamera != null && mCamera.getParameters().isZoomSupported()) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getZoom() >= parameters.getMaxZoom()) {
                return;
            }
            parameters.setZoom(parameters.getZoom() + 1);
            mCamera.setParameters(parameters);
        }
    }

    /*
     * 縮放
     *
     * @param scale
     */
    public void setCameraZoom(int scale) {
        if (mCamera != null &&
                mCamera.getParameters().isZoomSupported() &&
                scale <= mCamera.getParameters().getMaxZoom() && scale >= 0) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setZoom(scale);
            mCamera.setParameters(parameters);
        }
    }
}

