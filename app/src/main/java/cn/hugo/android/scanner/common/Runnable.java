package cn.hugo.android.scanner.common;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

/**
 * 相容低版本的子線程開啟任務
 *
 * @author hugo
 *
 */
public class Runnable {
    private static final String TAG = Runnable.class.getSimpleName();
    @SuppressLint("NewApi")
    @SuppressWarnings("unchecked")
    public static void execAsync(AsyncTask<?, ?, ?> task) {
        if (Build.VERSION.SDK_INT >= 11) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }
}

