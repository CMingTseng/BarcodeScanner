package cn.hugo.android.scanner;

import com.google.zxing.Result;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;

import cn.hugo.android.scanner.camera.CameraManager;
import cn.hugo.android.scanner.view.ViewfinderView;

/**
 * Created by Neo on 2016/9/17 017.
 */
public interface DecodeInterface {
	Context getContext();

	CameraManager getCameraManager();

	ViewfinderView getViewfinderView();

	Handler getHandler();

	void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor);

	void drawViewfinder();
}
