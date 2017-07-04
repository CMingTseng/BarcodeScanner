package cn.hugo.android.scanner;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.client.result.ResultParser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;

import cn.hugo.android.scanner.camera.CameraManager;
import cn.hugo.android.scanner.decode.CaptureActivityHandler;
import cn.hugo.android.scanner.view.ViewfinderView;

/**
 * Created by Neo on 2016/9/17 017.
 */
public class MainFragment extends Fragment implements DecodeInterface, TextureView.SurfaceTextureListener, View.OnClickListener {
    private static final String TAG = MainFragment.class.getSimpleName();
    private Context mContext;
    private static final int REQUEST_CODE = 100;

    private static final int PARSE_BARCODE_FAIL = 300;
    private static final int PARSE_BARCODE_SUC = 200;

    /**
     * 是否有預覽
     */
    private boolean hasSurface;

    /**
     * 活動監控器。如果手機沒有連接電源線，那麼當相機開啟後如果一直處於不被使用狀態則該服務會將當前activity關閉。
     * 活動監控器全程監控掃描活躍狀態，與CaptureActivity生命週期相同.每一次掃描過後都會重置該監控，即重新倒計時。
     */
    private InactivityTimer inactivityTimer;

    /**
     * 聲音震動管理器。如果掃描成功後可以播放一段音頻，也可以震動提醒，可以通過配置來決定掃描成功後的行為。
     */
    private BeepManager beepManager;

    /**
     * 閃光燈調節器。自動檢測環境光線強弱並決定是否開啟閃光燈
     */
    private AmbientLightManager ambientLightManager;

    private CameraManager cameraManager;
    /**
     * 掃描區域
     */
    private ViewfinderView viewfinderView;

    private CaptureActivityHandler handler;

    private Result lastResult;

    private boolean isFlashlightOpen;

    /**
     * 【輔助解碼的參數(用作MultiFormatReader的參數)】 編碼類型，該參數告訴掃描器採用何種編碼方式解碼，即EAN-13，QR
     * Code等等 對應於DecodeHintType.POSSIBLE_FORMATS類型
     * 參考DecodeThread構造函數中如下代碼：hints.put(DecodeHintType.POSSIBLE_FORMATS,
     * decodeFormats);
     */
    private Collection<BarcodeFormat> decodeFormats;

    /**
     * 【輔助解碼的參數(用作MultiFormatReader的參數)】 該參數最終會傳入MultiFormatReader，
     * 上面的decodeFormats和characterSet最終會先加入到decodeHints中 最終被設置到MultiFormatReader中
     * 參考DecodeHandler構造器中如下代碼：multiFormatReader.setHints(hints);
     */
    private Map<DecodeHintType, ?> decodeHints;

    /**
     * 【輔助解碼的參數(用作MultiFormatReader的參數)】 字元集，告訴掃描器該以何種字元集進行解碼
     * 對應於DecodeHintType.CHARACTER_SET類型
     * 參考DecodeThread構造器如下代碼：hints.put(DecodeHintType.CHARACTER_SET,
     * characterSet);
     */
    private String characterSet;

    private Result savedResultToShow;

    private IntentSource source;

    /**
     * 圖片的路徑
     */
    private String photoPath;

    private Handler mHandler = new MyHandler(getActivity());

    static class MyHandler extends Handler {

        private WeakReference<Activity> activityReference;

        public MyHandler(Activity activity) {
            activityReference = new WeakReference<Activity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case PARSE_BARCODE_SUC: // 解析圖片成功
                    Toast.makeText(activityReference.get(),
                            "解析成功，結果為：" + msg.obj, Toast.LENGTH_SHORT).show();
                    break;

                case PARSE_BARCODE_FAIL:// 解析圖片失敗

                    Toast.makeText(activityReference.get(), "解析圖片失敗",
                            Toast.LENGTH_SHORT).show();
                    break;

                default:
                    break;
            }

            super.handleMessage(msg);
        }

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//    Window window =getWindow();
//    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View root = inflater.inflate(R.layout.capture, container, false);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(getActivity());
        beepManager = new BeepManager(getActivity());
        ambientLightManager = new AmbientLightManager(getActivity());

        // 監聽圖片識別按鈕
        root.findViewById(R.id.capture_scan_photo).setOnClickListener(this);

        root.findViewById(R.id.capture_flashlight).setOnClickListener(this);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.

        // 相機初始化的動作需要開啟相機並測量螢幕大小，這些操作
        // 不建議放到onCreate中，因為如果在onCreate中加上首次啟動展示幫助資訊的代碼的 話，
        // 會導致掃描視窗的尺寸計算有誤的bug
        cameraManager = new CameraManager(getActivity().getApplication());

        viewfinderView = (ViewfinderView) getView().findViewById(R.id.capture_viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;
        lastResult = null;

//    // 攝像頭預覽功能必須借助SurfaceView，因此也需要在一開始對其進行初始化
//    // 如果需要瞭解SurfaceView的原理
//    // 參考:http://blog.csdn.net/luoshengyang/article/details/8661317
//    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview_view); // 預覽
//    SurfaceHolder surfaceHolder = surfaceView.getHolder();
//    if (hasSurface) {
//       // The activity was paused but not stopped, so the surface still
//       // exists. Therefore
//       // surfaceCreated() won't be called, so init the camera here.
//       initCamera(surfaceHolder);
//
//    } else {
//       // 防止sdk8的設備初始化預覽異常
//       surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//
//       // Install the callback and wait for surfaceCreated() to init the
//       // camera.
////         surfaceHolder.addCallback(this);
//    }

        TextureView surfaceView = (TextureView) getView().findViewById(R.id.capture_preview_view); // 預覽
        surfaceView.setSurfaceTextureListener(this);
        // 載入聲音配置，其實在BeemManager的構造器中也會調用該方法，即在onCreate的時候會調用一次
        beepManager.updatePrefs();

        // 啟動閃光燈調節器
        ambientLightManager.start(cameraManager);

        // 恢復活動監控器
        inactivityTimer.onResume();

        source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;
    }

    @Override
    public void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
//    ambientLightManager.stop();
//    beepManager.close();
//
//    // 關閉攝像頭
//    cameraManager.closeDriver();
//    if (!hasSurface) {
//       SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview_view);
//       SurfaceHolder surfaceHolder = surfaceView.getHolder();
////         surfaceHolder.removeCallback(this);
//    }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

// @Override
// public boolean onKeyDown(int keyCode, KeyEvent event) {
//    switch (keyCode) {
//       case KeyEvent.KEYCODE_BACK:
//          if ((source == IntentSource.NONE) && lastResult != null) { // 重新進行掃描
//             restartPreviewAfterDelay(0L);
//             return true;
//          }
//          break;
//       case KeyEvent.KEYCODE_FOCUS:
//       case KeyEvent.KEYCODE_CAMERA:
//          // Handle these events so they don't launch the Camera app
//          return true;
//
//       case KeyEvent.KEYCODE_VOLUME_UP:
//          cameraManager.zoomIn();
//          return true;
//
//       case KeyEvent.KEYCODE_VOLUME_DOWN:
//          cameraManager.zoomOut();
//          return true;
//
//    }
//    return super.onKeyDown(keyCode, event);
// }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
        hasSurface = false;
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult   The contents of the barcode.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param barcode     A greyscale bitmap of the camera data which was decoded.
     */
    @Override
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {

        // 重新計時
        inactivityTimer.onActivity();

        lastResult = rawResult;

        // 把圖片畫到掃描框
        viewfinderView.drawResultBitmap(barcode);

        beepManager.playBeepSoundAndVibrate();

        Toast.makeText(getContext(), "識別結果:" + ResultParser.parseResult(rawResult).toString(), Toast.LENGTH_SHORT).show();

    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
        resetStatusView();
    }

    @Override
    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private void resetStatusView() {
        viewfinderView.setVisibility(View.VISIBLE);
        lastResult = null;
    }

    @Override
    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    private void initCamera(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) {
            throw new IllegalStateException("No SurfaceTexture provided");
        }

        if (cameraManager.isOpen()) {
            Log.w(TAG,
                    "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceTexture);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }
            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 向CaptureActivityHandler中發送消息，並展示掃描到的圖像
     */
    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
        // Bitmap isn't used yet -- will be used soon
        if (handler == null) {
            savedResultToShow = result;
        } else {
            if (result != null) {
                savedResultToShow = result;
            }
            if (savedResultToShow != null) {
                Message message = Message.obtain(handler,
                        R.id.decode_succeeded, savedResultToShow);
                handler.sendMessage(message);
            }
            savedResultToShow = null;
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(getActivity()));
        builder.setOnCancelListener(new FinishListener(getActivity()));
        builder.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.capture_scan_photo: // 圖片識別
                // 打開手機中的相冊
                Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT); // "android.intent.action.GET_CONTENT"
                innerIntent.setType("image/*");
                Intent wrapperIntent = Intent.createChooser(innerIntent,
                        "選擇二維碼圖片");
                this.startActivityForResult(wrapperIntent, REQUEST_CODE);
                break;

            case R.id.capture_flashlight:
                if (isFlashlightOpen) {
                    cameraManager.setTorch(false); // 關閉閃光燈
                    isFlashlightOpen = false;
                } else {
                    cameraManager.setTorch(true); // 打開閃光燈
                    isFlashlightOpen = true;
                }
                break;
            default:
                break;
        }

    }


}

