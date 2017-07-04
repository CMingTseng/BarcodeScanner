package cn.hugo.android.scanner.utils;

import com.google.zxing.Result;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;

import cn.hugo.android.scanner.camera.CameraManager;
import cn.hugo.android.scanner.view.ViewfinderView;

/**
 * Created by Neo on 2017/7/3.
 */

public interface Decodeinterface {
    Context getContext();

    CameraManager getCameraManager();

    ViewfinderView getViewfinderView();

    Handler getHandler();

    void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor);

    void drawViewfinder();
}
