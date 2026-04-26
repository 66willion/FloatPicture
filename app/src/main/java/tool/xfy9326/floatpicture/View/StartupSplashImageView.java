package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class StartupSplashImageView extends AppCompatImageView {
    private final Matrix splashMatrix = new Matrix();

    public StartupSplashImageView(Context context) {
        super(context);
        init();
    }

    public StartupSplashImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StartupSplashImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateSplashMatrix();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        updateSplashMatrix();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        updateSplashMatrix();
    }

    private void updateSplashMatrix() {
        Drawable drawable = getDrawable();
        int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (drawable == null || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        float scale = Math.max(viewWidth / (float) drawableWidth, viewHeight / (float) drawableHeight);
        float scaledWidth = drawableWidth * scale;
        float scaledHeight = drawableHeight * scale;
        float dx = (viewWidth - scaledWidth) * 0.5f;
        float dy = viewWidth > viewHeight ? 0f : (viewHeight - scaledHeight) * 0.5f;

        splashMatrix.reset();
        splashMatrix.setScale(scale, scale);
        splashMatrix.postTranslate(Math.round(dx) + getPaddingLeft(), Math.round(dy) + getPaddingTop());
        setImageMatrix(splashMatrix);
    }
}
