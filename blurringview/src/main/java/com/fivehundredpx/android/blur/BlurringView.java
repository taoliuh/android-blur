package com.fivehundredpx.android.blur;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import com.enrique.stackblur.StackBlurManager;

import java.lang.reflect.Field;

/**
 * A custom view for presenting a dynamically blurred version of another view's content.
 * <p/>
 * Use {@link #setBlurredView(android.view.View)} to set up the reference to the view to be blurred.
 * After that, call {@link #invalidate()} to trigger blurring whenever necessary.
 */
public class BlurringView extends View {

    public BlurringView(Context context) {
        this(context, null);
    }

    public BlurringView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();
        final int defaultBlurRadius = res.getInteger(R.integer.default_blur_radius);
        final int defaultDownsampleFactor = res.getInteger(R.integer.default_downsample_factor);
        final int defaultOverlayColor = res.getColor(R.color.default_overlay_color);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PxBlurringView);
        mBlurRadius = a.getInt(R.styleable.PxBlurringView_blurRadius, defaultBlurRadius);
        setDownSampleFactor(a.getInt(R.styleable.PxBlurringView_downsampleFactor,
                defaultDownsampleFactor));
        setOverlayColor(a.getColor(R.styleable.PxBlurringView_overlayColor, defaultOverlayColor));
        a.recycle();
    }

    public void setBlurredView(View blurredView) {
        mBlurredView = blurredView;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBlurredView != null) {
            if (prepare()) {
                // If the background of the blurred view is a color drawable, we use it to clear
                // the blurring canvas, which ensures that edges of the child views are blurred
                // as well; otherwise we clear the blurring canvas with a transparent color.
                if (mBlurredView.getBackground() != null && mBlurredView.getBackground() instanceof ColorDrawable){
                    mBitmapToBlur.eraseColor(getBackgroundColor(mBlurredView));
                }else {
                    mBitmapToBlur.eraseColor(Color.TRANSPARENT);
                }

                mBlurredView.draw(mBlurringCanvas);
                blur();

                canvas.save();
                canvas.translate(mBlurredView.getLeft() - getLeft(), mBlurredView.getTop() - getTop());
                canvas.scale(mDownSampleFactor, mDownSampleFactor);
                canvas.drawBitmap(mBlurredBitmap, 0, 0, null);
                canvas.restore();
            }
            canvas.drawColor(mOverlayColor);
        }
    }

    public void setDownSampleFactor(int factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Down sample factor must be greater than 0.");
        }

        if (mDownSampleFactor != factor) {
            mDownSampleFactor = factor;
            mDownSampleFactorChanged = true;
        }
    }

    public void setOverlayColor(int color) {
        mOverlayColor = color;
    }

    protected boolean prepare() {
        final int width = mBlurredView.getWidth();
        final int height = mBlurredView.getHeight();

        if (mBlurringCanvas == null || mDownSampleFactorChanged
                || mBlurredViewWidth != width || mBlurredViewHeight != height) {
            mDownSampleFactorChanged = false;

            mBlurredViewWidth = width;
            mBlurredViewHeight = height;

            int scaledWidth = width / mDownSampleFactor;
            int scaledHeight = height / mDownSampleFactor;

            // The following manipulation is to avoid some RenderScript artifacts at the edge.
            scaledWidth = scaledWidth - scaledWidth % 4 + 4;
            scaledHeight = scaledHeight - scaledHeight % 4 + 4;

            if (mBlurredBitmap == null
                    || mBlurredBitmap.getWidth() != scaledWidth
                    || mBlurredBitmap.getHeight() != scaledHeight) {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888);
                if (mBitmapToBlur == null) {
                    return false;
                }

                mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888);
                if (mBlurredBitmap == null) {
                    return false;
                }
            }

            mBlurringCanvas = new Canvas(mBitmapToBlur);
            mBlurringCanvas.scale(1f / mDownSampleFactor, 1f / mDownSampleFactor);
        }
        return true;
    }

    private int getBackgroundColor(View view) {
        ColorDrawable drawable = (ColorDrawable) view.getBackground();
        if (Build.VERSION.SDK_INT >= 11) {
            return drawable.getColor();
        }
        try {
            Field field = drawable.getClass().getDeclaredField("mState");
            field.setAccessible(true);
            Object object = field.get(drawable);
            field = object.getClass().getDeclaredField("mUseColor");
            field.setAccessible(true);
            return field.getInt(object);
        } catch (Exception e) {
        }
        return 0;
    }

    protected void blur() {
        if (mBlurManager == null) {
            mBlurManager = new StackBlurManager(mBitmapToBlur);
        }
        mBlurredBitmap = mBlurManager.processNatively(mBlurRadius);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBitmapToBlur != null && !mBitmapToBlur.isRecycled()) {
            mBitmapToBlur.recycle();
            mBitmapToBlur = null;
        }
        if (mBlurredBitmap != null && !mBlurredBitmap.isRecycled()) {
            mBlurredBitmap.recycle();
            mBlurredBitmap = null;
        }
    }

    private int mDownSampleFactor;
    private int mOverlayColor;

    private View mBlurredView;
    private int mBlurredViewWidth, mBlurredViewHeight;

    private boolean mDownSampleFactorChanged;
    private Bitmap mBitmapToBlur, mBlurredBitmap;
    private Canvas mBlurringCanvas;
    private StackBlurManager mBlurManager;
    private int mBlurRadius = 10;

}
