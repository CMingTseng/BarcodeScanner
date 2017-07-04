package cn.hugo.android.scanner.decode;

import com.google.zxing.LuminanceSource;

import android.graphics.Bitmap;

public class BitmapLuminanceSource extends LuminanceSource {
    private static final String TAG = BitmapLuminanceSource.class.getSimpleName();
    private byte mBitmapPixels[];

    protected BitmapLuminanceSource(Bitmap bitmap) {
        super(bitmap.getWidth(), bitmap.getHeight());
        // 首先，要取得該圖片的圖元陣列內容
        int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
        this.mBitmapPixels = new byte[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(data, 0, getWidth(), 0, 0, getWidth(), getHeight());
        // 將int陣列轉換為byte陣列，也就是取圖元值中藍色值部分作為辨析內容
        for (int i = 0; i < data.length; i++) {
            this.mBitmapPixels[i] = (byte) data[i];
        }
    }

    @Override
    public byte[] getMatrix() {
        // 返回我們生成好的圖元資料
        return mBitmapPixels;
    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        // 這裏要得到指定行的圖元資料
        System.arraycopy(mBitmapPixels, y * getWidth(), row, 0, getWidth());
        return row;
    }
}

