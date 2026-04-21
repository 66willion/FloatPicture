package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import tool.xfy9326.floatpicture.R;

public class RecyclerFastScrollTrackView extends View {
    public interface PageJumpListener {
        void onPageJump(int direction);
    }

    public interface InteractionListener {
        void onTrackMoved();

        void onTrackReleased();
    }

    private static final float TRACK_WIDTH_DP = 8f;
    private static final float THUMB_WIDTH_DP = 20f;
    private static final float THUMB_MIN_HEIGHT_DP = 52f;
    private static final float CONTENT_VERTICAL_PADDING_DP = 10f;
    private static final float THUMB_GRIP_WIDTH_DP = 10f;
    private static final float THUMB_GRIP_HEIGHT_DP = 3f;
    private static final float THUMB_GRIP_GAP_DP = 5f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbGripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();
    private RecyclerView recyclerView;
    private PageJumpListener pageJumpListener;
    private InteractionListener interactionListener;
    private boolean dragging = false;
    private float dragThumbOffsetY = 0f;

    public RecyclerFastScrollTrackView(Context context) {
        super(context);
        init();
    }

    public RecyclerFastScrollTrackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecyclerFastScrollTrackView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorPrimaryContainer));
        thumbPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorPrimary));
        thumbGripPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorSurface));
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        invalidate();
    }

    public void setPageJumpListener(@Nullable PageJumpListener pageJumpListener) {
        this.pageJumpListener = pageJumpListener;
    }

    public void setInteractionListener(@Nullable InteractionListener interactionListener) {
        this.interactionListener = interactionListener;
    }

    public void refreshState() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateGeometry();
        canvas.drawRoundRect(trackRect, trackRect.width() / 2f, trackRect.width() / 2f, trackPaint);
        if (!hasScrollableContent()) {
            return;
        }
        thumbPaint.setAlpha(dragging ? 255 : 230);
        canvas.drawRoundRect(thumbRect, thumbRect.width() / 2f, thumbRect.width() / 2f, thumbPaint);
        drawThumbGrip(canvas);
    }

    private void drawThumbGrip(Canvas canvas) {
        float gripWidth = dpToPx(THUMB_GRIP_WIDTH_DP);
        float gripHeight = dpToPx(THUMB_GRIP_HEIGHT_DP);
        float gripGap = dpToPx(THUMB_GRIP_GAP_DP);
        float left = thumbRect.centerX() - (gripWidth / 2f);
        float right = thumbRect.centerX() + (gripWidth / 2f);
        float centerY = thumbRect.centerY();
        RectF upperGrip = new RectF(left, centerY - gripGap - gripHeight, right, centerY - gripGap);
        RectF lowerGrip = new RectF(left, centerY + gripGap, right, centerY + gripGap + gripHeight);
        float radius = gripHeight / 2f;
        canvas.drawRoundRect(upperGrip, radius, radius, thumbGripPaint);
        canvas.drawRoundRect(lowerGrip, radius, radius, thumbGripPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasScrollableContent()) {
            return super.onTouchEvent(event);
        }
        updateGeometry();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isWithinInteractiveTrack(y)) {
                    return false;
                }
                if (thumbRect.contains(event.getX(), y)) {
                    dragging = true;
                    dragThumbOffsetY = y - thumbRect.top;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                }
                performTrackPageJump(y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return false;
                }
                scrollToThumbPosition(y - dragThumbOffsetY);
                notifyTrackMoved();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    invalidate();
                }
                notifyTrackReleased();
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void performTrackPageJump(float y) {
        if (pageJumpListener == null) {
            return;
        }
        if (y < thumbRect.top) {
            pageJumpListener.onPageJump(-1);
        } else if (y > thumbRect.bottom) {
            pageJumpListener.onPageJump(1);
        }
    }

    private void scrollToThumbPosition(float desiredThumbTop) {
        if (recyclerView == null) {
            return;
        }
        float clampedThumbTop = clamp(desiredThumbTop, trackRect.top, trackRect.bottom - thumbRect.height());
        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        int scrollableRange = Math.max(range - extent, 0);
        if (scrollableRange <= 0) {
            return;
        }
        float maxThumbTravel = Math.max(trackRect.height() - thumbRect.height(), 1f);
        float thumbRatio = (clampedThumbTop - trackRect.top) / maxThumbTravel;
        int targetOffset = Math.round(thumbRatio * scrollableRange);
        int currentOffset = recyclerView.computeVerticalScrollOffset();
        int delta = targetOffset - currentOffset;
        if (delta != 0) {
            recyclerView.stopScroll();
            recyclerView.scrollBy(0, delta);
        }
        invalidate();
    }

    private void notifyTrackMoved() {
        if (interactionListener != null) {
            interactionListener.onTrackMoved();
        }
    }

    private void notifyTrackReleased() {
        if (interactionListener != null) {
            interactionListener.onTrackReleased();
        }
    }

    private boolean hasScrollableContent() {
        if (recyclerView == null) {
            return false;
        }
        return recyclerView.computeVerticalScrollRange() > recyclerView.computeVerticalScrollExtent() + 1;
    }

    private boolean isWithinInteractiveTrack(float y) {
        return y >= trackRect.top && y <= trackRect.bottom;
    }

    private void updateGeometry() {
        float contentTop = dpToPx(CONTENT_VERTICAL_PADDING_DP);
        float contentBottom = getHeight() - dpToPx(CONTENT_VERTICAL_PADDING_DP);
        float trackWidth = dpToPx(TRACK_WIDTH_DP);
        float trackLeft = (getWidth() - trackWidth) / 2f;
        trackRect.set(trackLeft, contentTop, trackLeft + trackWidth, contentBottom);

        if (!hasScrollableContent()) {
            thumbRect.set(trackRect);
            return;
        }

        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        int offset = recyclerView.computeVerticalScrollOffset();
        int scrollableRange = Math.max(range - extent, 0);
        float thumbHeight = Math.max((extent / (float) range) * trackRect.height(), dpToPx(THUMB_MIN_HEIGHT_DP));
        thumbHeight = Math.min(thumbHeight, trackRect.height());
        float maxThumbTravel = Math.max(trackRect.height() - thumbHeight, 0f);
        float thumbTop = trackRect.top;
        if (scrollableRange > 0 && maxThumbTravel > 0f) {
            thumbTop += (offset / (float) scrollableRange) * maxThumbTravel;
        }
        float thumbWidth = dpToPx(THUMB_WIDTH_DP);
        float thumbLeft = (getWidth() - thumbWidth) / 2f;
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
