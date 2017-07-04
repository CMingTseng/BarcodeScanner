package cn.hugo.android.scanner.decode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.Hashtable;
import java.util.Vector;

/**
 * 從bitmap解碼
 *
 * @author hugo
 *
 */
public class BitmapDecoder {

    MultiFormatReader multiFormatReader;

    public BitmapDecoder(Context context) {

        multiFormatReader = new MultiFormatReader();

        // 解碼的參數
        Hashtable<DecodeHintType, Object> hints = new Hashtable<DecodeHintType, Object>(
                2);
        // 可以解析的編碼類型
        Vector<BarcodeFormat> decodeFormats = new Vector<BarcodeFormat>();
        if (decodeFormats == null || decodeFormats.isEmpty()) {
            decodeFormats = new Vector<BarcodeFormat>();

            // 這裏設置可掃描的類型，我這裏選擇了都支持
            decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
        }
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        // 設置繼續的字元編碼格式為UTF8
        hints.put(DecodeHintType.CHARACTER_SET, "UTF8");

        // 設置解析配置參數
        multiFormatReader.setHints(hints);

    }

    /**
     * 獲取解碼結果
     */
    public Result getRawResult(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

		try {
            return multiFormatReader.decodeWithState(new BinaryBitmap(
                    new HybridBinarizer(new BitmapLuminanceSource(bitmap))));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }
}

