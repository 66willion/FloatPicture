package tool.xfy9326.floatpicture.View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

public class FloatImageView extends AppCompatImageView {
    private static final String TAG = "FloatImageView";

    private String PictureId = "";
    private boolean moveable = false;
    private boolean overLayout = false;
    private float pictureAlpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
    /** 实际写入 LayoutParams.alpha 的值，由 WindowsMethods 在每次 updateViewLayout 后同步。
     *  拖动时直接复用此值，避免绕开多窗口联合透明度公式。 */
    private float layoutAlpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
    private long displayBitmapVersion = 0L;
    private WindowManager attachedWindowManager;

    private float mTouchStartX = 0;
    private float mTouchStartY = 0;
    private float x = 0;
    private float y = 0;
    private float mNowPositionX = Config.DATA_DEFAULT_PICTURE_POSITION_X;
    private float mNowPositionY = Config.DATA_DEFAULT_PICTURE_POSITION_Y;
    private boolean positionUpdateFailureLogged = false;

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

    public void setAttachedWindowManager(@Nullable WindowManager windowManager) {
        attachedWindowManager = windowManager;
    }

    @Nullable
    public WindowManager getAttachedWindowManager() {
        return attachedWindowManager;
    }

    public long getDisplayBitmapVersion() {
        return displayBitmapVersion;
    }

    public void setDisplayBitmapVersion(long displayBitmapVersion) {
        this.displayBitmapVersion = displayBitmapVersion;
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
                    positionUpdateFailureLogged = false;
                }
                case MotionEvent.ACTION_MOVE -> {
                    getNowPosition();
                    updatePosition();
                }
                case MotionEvent.ACTION_UP -> {
                    getNowPosition();
                    if (updatePosition()) {
                        saveWindowPosition((int) mNowPositionX, (int) mNowPositionY);
                    }
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

    private boolean updatePosition() {
        if (!isAttachedToWindow()) {
            attachedWindowManager = null;
            return false;
        }
        WindowManager.LayoutParams params = WindowsMethods.getDefaultLayout(getContext(), (int) mNowPositionX, (int) mNowPositionY, moveable, overLayout, pictureAlpha);
        // 拖动时复用上次由 WindowsMethods 同步过来的 layoutAlpha，
        // 避免单窗口路径（getDefaultLayout）覆盖掉多窗口联合公式计算的值。
        params.alpha = layoutAlpha;
        WindowManager windowManager = attachedWindowManager != null ? attachedWindowManager : WindowsMethods.getWindowManager(getContext());
        if (windowManager == null) {
            return false;
        }
        try {
            windowManager.updateViewLayout(this, params);
            attachedWindowManager = windowManager;
            positionUpdateFailureLogged = false;
            return true;
        } catch (RuntimeException e) {
            attachedWindowManager = null;
            if (!positionUpdateFailureLogged) {
                Log.w(TAG, "Failed to update floating window position: " + e.getMessage());
                positionUpdateFailureLogged = true;
            }
            return false;
        }
    }

    private void saveWindowPosition(int positionX, int positionY) {
        String pictureId = PictureId;
        Context appContext = getContext().getApplicationContext() != null ? getContext().getApplicationContext() : getContext();
        try {
            OverlayRuntimeStateStore.saveWindowPosition(appContext, pictureId, positionX, positionY);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to save floating window position: " + e.getMessage());
        }
    }

}
