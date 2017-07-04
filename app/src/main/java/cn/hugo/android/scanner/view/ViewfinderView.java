package cn.hugo.android.scanner.view;

import com.google.zxing.ResultPoint;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cn.hugo.android.scanner.R;
import cn.hugo.android.scanner.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 *
 * <br/>
 * <br/>
 * 該視圖是覆蓋在相機的預覽視圖之上的一層視圖。掃描區構成原理，其實是在預覽視圖上畫四塊遮罩層，
 * 中間留下的部分保持透明，並畫上一條鐳射線，實際上該線條就是展示而已，與掃描功能沒有任何關係。
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {
    private static final String TAG = ViewfinderView.class.getSimpleName();
    /**
     * 刷新介面的時間
     */
    private static final long ANIMATION_DELAY = 10L;
    private static final int OPAQUE = 0xFF;

    private int CORNER_PADDING;

    /**
     * 掃描框中的中間線的寬度
     */
    private static int MIDDLE_LINE_WIDTH;

    /**
     * 掃描框中的中間線的與掃描框左右的間隙
     */
    private static int MIDDLE_LINE_PADDING;

    /**
     * 中間那條線每次刷新移動的距離
     */
    private static final int SPEEN_DISTANCE = 10;

    /**
     * 畫筆對象的引用
     */
    private Paint mPaint;

    /**
     * 中間滑動線的最頂端位置
     */
    private int mSlideTop;

    /**
     * 中間滑動線的最底端位置
     */
    private int mSlideBottom;

    private static final int MAX_RESULT_POINTS = 20;

    private Bitmap mResultBitmap;

    /**
     * 遮掩層的顏色
     */
    private final int mMaskColor;
    private final int mResultColor;

    private final int mResultPointColor;
    private List<ResultPoint> mResultPointList;

    private List<ResultPoint> mLastPossibleResultPoints;

    /**
     * 第一次繪製控制項
     */
    boolean isFirst = true;

    private CameraManager mCameraManager;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        CORNER_PADDING = dip2px(context, 0.0F);
        MIDDLE_LINE_PADDING = dip2px(context, 20.0F);
        MIDDLE_LINE_WIDTH = dip2px(context, 3.0F);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 開啟反鋸齒
        Resources resources = getResources();
        mMaskColor = resources.getColor(R.color.viewfinder_mask); // 遮掩層顏色
        mResultColor = resources.getColor(R.color.result_view);
        mResultPointColor = resources.getColor(R.color.possible_result_points);
        mResultPointList = new ArrayList<ResultPoint>(5);
        mLastPossibleResultPoints = null;
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.mCameraManager = cameraManager;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mCameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = mCameraManager.getFramingRect();
        if (frame == null) {
            return;
        }
        // 繪製遮掩層
        drawCover(canvas, frame);
        if (mResultBitmap != null) { // 繪製掃描結果的圖
            // Draw the opaque result bitmap over the scanning rectangle
            mPaint.setAlpha(0xA0);
            canvas.drawBitmap(mResultBitmap, null, frame, mPaint);
        } else {
            // 畫掃描框邊上的角
            drawRectEdges(canvas, frame);
            // 繪製掃描線
            drawScanningLine(canvas, frame);
            List<ResultPoint> currentPossible = mResultPointList;
            Collection<ResultPoint> currentLast = mLastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                mLastPossibleResultPoints = null;
            } else {
                mResultPointList = new ArrayList<ResultPoint>(5);
                mLastPossibleResultPoints = currentPossible;
                mPaint.setAlpha(OPAQUE);
                mPaint.setColor(mResultPointColor);
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, mPaint);
                }
            }
            if (currentLast != null) {
                mPaint.setAlpha(OPAQUE / 2);
                mPaint.setColor(mResultPointColor);
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, mPaint);
                }
            }

            // 只刷新掃描框的內容，其他地方不刷新
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);

        }
    }

    /**
     * 繪製掃描線
     *
     * @param frame 掃描框
     */
    private void drawScanningLine(Canvas canvas, Rect frame) {

        // 初始化中間線滑動的最上邊和最下邊
        if (isFirst) {
            isFirst = false;
            mSlideTop = frame.top;
            mSlideBottom = frame.bottom;
        }

        // 繪製中間的線,每次刷新介面，中間的線往下移動SPEEN_DISTANCE
        mSlideTop += SPEEN_DISTANCE;
        if (mSlideTop >= mSlideBottom) {
            mSlideTop = frame.top;
        }

        // 從圖片資源畫掃描線
        Rect lineRect = new Rect();
        lineRect.left = frame.left + MIDDLE_LINE_PADDING;
        lineRect.right = frame.right - MIDDLE_LINE_PADDING;
        lineRect.top = mSlideTop;
        lineRect.bottom = (mSlideTop + MIDDLE_LINE_WIDTH);
        canvas.drawBitmap(((BitmapDrawable) (BitmapDrawable) getResources().getDrawable(R.drawable.scan_laser)).getBitmap(), null, lineRect, mPaint);

    }

    /**
     * 繪製遮掩層
     */
    private void drawCover(Canvas canvas, Rect frame) {
		// 獲取螢幕的寬和高
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        // Draw the exterior (i.e. outside the framing rect) darkened
        mPaint.setColor(mResultBitmap != null ? mResultColor : mMaskColor);

        // 畫出掃描框外面的陰影部分，共四個部分，掃描框的上面到螢幕上面，掃描框的下面到螢幕下面
        // 掃描框的左邊面到螢幕左邊，掃描框的右邊到螢幕右邊
        canvas.drawRect(0, 0, width, frame.top, mPaint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, mPaint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, mPaint);
        canvas.drawRect(0, frame.bottom + 1, width, height, mPaint);
    }

    /**
     * 描繪方形的四個角
     *
     * @param canvas
     * @param frame
     */
    private void drawRectEdges(Canvas canvas, Rect frame) {
        mPaint.setColor(Color.WHITE);
        mPaint.setAlpha(OPAQUE);

        Resources resources = getResources();
        /**
         * 這些資源可以用緩存進行管理，不需要每次刷新都新建
         */
        Bitmap bitmapCornerTopleft = BitmapFactory.decodeResource(resources, R.drawable.scan_corner_top_left);
        Bitmap bitmapCornerTopright = BitmapFactory.decodeResource(resources, R.drawable.scan_corner_top_right);
        Bitmap bitmapCornerBottomLeft = BitmapFactory.decodeResource(resources, R.drawable.scan_corner_bottom_left);
        Bitmap bitmapCornerBottomRight = BitmapFactory.decodeResource(resources, R.drawable.scan_corner_bottom_right);
        canvas.drawBitmap(bitmapCornerTopleft, frame.left + CORNER_PADDING, frame.top + CORNER_PADDING, mPaint);
        canvas.drawBitmap(bitmapCornerTopright, frame.right - CORNER_PADDING - bitmapCornerTopright.getWidth(), frame.top + CORNER_PADDING, mPaint);
        canvas.drawBitmap(bitmapCornerBottomLeft, frame.left + CORNER_PADDING, 2 + (frame.bottom - CORNER_PADDING - bitmapCornerBottomLeft.getHeight()), mPaint);
        canvas.drawBitmap(bitmapCornerBottomRight, frame.right - CORNER_PADDING - bitmapCornerBottomRight.getWidth(), 2 + (frame.bottom - CORNER_PADDING - bitmapCornerBottomRight.getHeight()), mPaint);
        bitmapCornerTopleft.recycle();
        bitmapCornerTopleft = null;
        bitmapCornerTopright.recycle();
        bitmapCornerTopright = null;
        bitmapCornerBottomLeft.recycle();
        bitmapCornerBottomLeft = null;
        bitmapCornerBottomRight.recycle();
        bitmapCornerBottomRight = null;
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.mResultBitmap;
        this.mResultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode
     *            An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        mResultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = mResultPointList;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

    /**
     * dp轉px
     *
     * @param context
     * @param dipValue
     * @return
     */
    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }
}

