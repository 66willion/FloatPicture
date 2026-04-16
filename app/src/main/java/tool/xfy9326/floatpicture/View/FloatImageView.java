package tool.xfy9326.floatpicture.View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.appcompat.widget.AppCompatImageView;

import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

public class FloatImageView extends AppCompatImageView {
    private String PictureId = "";
    private boolean moveable = false;
    private boolean overLayout = false;
    private float pictureAlpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
    /** 实际写入 LayoutParams.alpha 的值，由 WindowsMethods 在每次 updateViewLayout 后同步。
     *  拖动时直接复用此值，避免绕开多窗口联合透明度公式。 */
    private float layoutAlpha = Config.DATA_DEFAULT_PICTURE_ALPHA;

    private float mTouchStartX = 0;
    private float mTouchStartY = 0;
    private float x = 0;
    private float y = 0;
    private float mNowPositionX = Config.DATA_DEFAULT_PICTURE_POSITION_X;
    private float mNowPositionY = Config.DATA_DEFAULT_PICTURE_POSITION_Y;

    public FloatImageView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
    }

    @SuppressWarnings("unused")
    public String getPictureId() {
        return PictureId;
    }

    public void setPictureId(String id) {
        PictureId = id;
    }

    public void setMoveable(boolean moveable) {
        this.moveable = moveable;
    }

    public void setOverLayout(boolean overLayout) {
        this.overLayout = overLayout;
    }

    public float getPictureAlpha() {
        return pictureAlpha;
    }

    public void setPictureAlpha(float pictureAlpha) {
        this.pictureAlpha = pictureAlpha;
        // 视觉透明度由 LayoutParams.alpha（Surface 合成层）统一控制，
        // 此处不再调用 View.setAlpha()，避免两者相乘导致实际视觉透明度偏低。
        // LayoutParams.alpha 同时承担 Android 12+ 触摸安全限制（obscuring opacity）职责。
    }

    /** 由 WindowsMethods 在每次 updateViewLayout / addView 后调用，同步实际的 LayoutParams.alpha。
     *  拖动时复用此值，确保不会绕开多窗口联合透明度公式。 */
    public void setLayoutAlpha(float layoutAlpha) {
        this.layoutAlpha = layoutAlpha;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (moveable) {
            x = event.getRawX();
            y = event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    mTouchStartX = event.getX();
                    mTouchStartY = event.getY();
                }
                case MotionEvent.ACTION_MOVE -> {
                    getNowPosition();
                    updatePosition();
                }
                case MotionEvent.ACTION_UP -> {
                    getNowPosition();
                    updatePosition();
                    OverlayRuntimeStateStore.saveWindowPosition(getContext(), PictureId, (int) mNowPositionX, (int) mNowPositionY);
                    mTouchStartX = mTouchStartY = 0;
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    public float getMovedPositionX() {
        return mNowPositionX;
    }

    public float getMovedPositionY() {
        return mNowPositionY;
    }

    private void getNowPosition() {
        mNowPositionX = x - mTouchStartX;
        mNowPositionY = y - mTouchStartY;
    }

    private void updatePosition() {
        WindowManager.LayoutParams params = WindowsMethods.getDefaultLayout(getContext(), (int) mNowPositionX, (int) mNowPositionY, moveable, overLayout, pictureAlpha);
        // 拖动时复用上次由 WindowsMethods 同步过来的 layoutAlpha，
        // 避免单窗口路径（getDefaultLayout）覆盖掉多窗口联合公式计算的值。
        params.alpha = layoutAlpha;
        WindowsMethods.getWindowManager(getContext()).updateViewLayout(this, params);
    }

}
