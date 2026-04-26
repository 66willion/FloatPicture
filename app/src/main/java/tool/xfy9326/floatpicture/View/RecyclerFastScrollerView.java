package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import tool.xfy9326.floatpicture.R;

public class RecyclerFastScrollerView extends FrameLayout {
    private static final int MIN_FAST_SCROLL_ITEM_COUNT = 1;
    private static final float PAGE_JUMP_RATIO = 0.85f;
    private static final float MIN_PAGE_JUMP_DP = 64f;
    private static final float SCROLLER_TRANSLATION_Z_DP = 18f;
    private static final int DIRECT_JUMP_DISTANCE_THRESHOLD = 12;
    private static final long AUTO_HIDE_DELAY_MS = 1500L;
    private static final long FADE_DURATION_MS = 160L;

    private final RecyclerView.AdapterDataObserver adapterDataObserver = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            refreshScrollerState();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            refreshScrollerState();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            refreshScrollerState();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            refreshScrollerState();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            refreshScrollerState();
        }
    };
    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            showTransientScroller();
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                scheduleAutoHide();
            } else {
                showTransientScroller();
            }
        }
    };
    private final View.OnLayoutChangeListener layoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> refreshScrollerState();
    private final Runnable hideRunnable = this::hideScroller;

    private RecyclerView recyclerView;
    private RecyclerView.Adapter<?> observedAdapter;
    private AppCompatImageButton scrollToTopButton;
    private AppCompatImageButton scrollToBottomButton;
    private RecyclerFastScrollTrackView trackView;
    private boolean trackInteracting = false;

    public RecyclerFastScrollerView(Context context) {
        super(context);
        init(context);
    }

    public RecyclerFastScrollerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RecyclerFastScrollerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_recycler_fast_scroller, this, true);
        scrollToTopButton = findViewById(R.id.fast_scroller_button_top);
        scrollToBottomButton = findViewById(R.id.fast_scroller_button_bottom);
        trackView = findViewById(R.id.fast_scroller_track);
        scrollToTopButton.setOnClickListener(view -> scrollToTop());
        scrollToBottomButton.setOnClickListener(view -> scrollToBottom());
        trackView.setPageJumpListener(this::pageJumpByDirection);
        trackView.setInteractionListener(new RecyclerFastScrollTrackView.InteractionListener() {
            @Override
            public void onTrackMoved() {
                trackInteracting = true;
                trackView.refreshState();
                showScroller();
            }

            @Override
            public void onTrackReleased() {
                trackInteracting = false;
                if (recyclerView == null) {
                    refreshScrollerState();
                    return;
                }
                recyclerView.post(RecyclerFastScrollerView.this::refreshScrollerState);
                scheduleAutoHide();
            }
        });
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setAlpha(0f);
        setVisibility(GONE);
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (this.recyclerView == recyclerView) {
            refreshScrollerState();
            return;
        }
        detachRecyclerView();
        this.recyclerView = recyclerView;
        trackView.attachToRecyclerView(recyclerView);
        if (recyclerView == null) {
            hideImmediately();
            return;
        }
        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.addOnLayoutChangeListener(layoutChangeListener);
        observeAdapter(recyclerView.getAdapter());
        recyclerView.post(this::refreshScrollerState);
    }

    public void refreshScrollerState() {
        if (recyclerView == null) {
            setVisibility(GONE);
            return;
        }
        observeAdapter(recyclerView.getAdapter());
        recyclerView.post(() -> {
            if (recyclerView == null) {
                return;
            }
            if (!shouldShowScroller()) {
                hideImmediately();
                return;
            }
            if (getVisibility() == VISIBLE) {
                updateControlVisibility();
                bringToFront();
                setTranslationZ(dpToPx(SCROLLER_TRANSLATION_Z_DP));
            }
            trackView.refreshState();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        detachRecyclerView();
        super.onDetachedFromWindow();
    }

    private void scrollToTop() {
        if (recyclerView == null) {
            return;
        }
        recyclerView.stopScroll();
        scrollToPosition(0);
        showScroller();
        recyclerView.post(this::refreshScrollerState);
        scheduleAutoHide();
    }

    private void scrollToBottom() {
        if (recyclerView == null || recyclerView.getAdapter() == null) {
            return;
        }
        int itemCount = recyclerView.getAdapter().getItemCount();
        if (itemCount <= 0) {
            return;
        }
        recyclerView.stopScroll();
        scrollToPosition(itemCount - 1);
        showScroller();
        recyclerView.post(this::refreshScrollerState);
        scheduleAutoHide();
    }

    private void pageJumpByDirection(int direction) {
        if (recyclerView == null) {
            return;
        }
        int extent = recyclerView.computeVerticalScrollExtent();
        int pageDistance = Math.max(Math.round(extent * PAGE_JUMP_RATIO), dpToPx(MIN_PAGE_JUMP_DP));
        recyclerView.stopScroll();
        recyclerView.smoothScrollBy(0, direction * pageDistance);
        showScroller();
        recyclerView.post(this::refreshScrollerState);
        scheduleAutoHide();
    }

    private boolean shouldShowScroller() {
        if (recyclerView == null || recyclerView.getAdapter() == null) {
            return false;
        }
        return recyclerView.getAdapter().getItemCount() >= MIN_FAST_SCROLL_ITEM_COUNT
                && recyclerView.computeVerticalScrollRange() > recyclerView.computeVerticalScrollExtent() + 1;
    }

    private void showTransientScroller() {
        if (!shouldShowScroller()) {
            hideImmediately();
            return;
        }
        showScroller();
        if (recyclerView == null || recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
            scheduleAutoHide();
        } else {
            removeCallbacks(hideRunnable);
        }
    }

    private void showScroller() {
        if (!shouldShowScroller()) {
            hideImmediately();
            return;
        }
        removeCallbacks(hideRunnable);
        updateControlVisibility();
        bringToFront();
        setTranslationZ(dpToPx(SCROLLER_TRANSLATION_Z_DP));
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setAlpha(0f);
        }
        animate().cancel();
        animate().alpha(1f).setDuration(FADE_DURATION_MS).start();
        trackView.refreshState();
    }

    private void hideScroller() {
        if (trackInteracting) {
            scheduleAutoHide();
            return;
        }
        animate().cancel();
        animate()
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .withEndAction(() -> {
                    if (getAlpha() == 0f) {
                        setVisibility(GONE);
                    }
                })
                .start();
    }

    private void hideImmediately() {
        removeCallbacks(hideRunnable);
        animate().cancel();
        setAlpha(0f);
        setVisibility(GONE);
        scrollToTopButton.setVisibility(GONE);
        scrollToBottomButton.setVisibility(GONE);
    }

    private void scheduleAutoHide() {
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void updateControlVisibility() {
        if (recyclerView == null || !shouldShowScroller()) {
            scrollToTopButton.setVisibility(GONE);
            scrollToBottomButton.setVisibility(GONE);
            trackView.setVisibility(GONE);
            return;
        }
        boolean canScrollUp = recyclerView.canScrollVertically(-1);
        boolean canScrollDown = recyclerView.canScrollVertically(1);
        scrollToTopButton.setVisibility(canScrollUp ? VISIBLE : GONE);
        scrollToBottomButton.setVisibility(canScrollDown ? VISIBLE : GONE);
        scrollToTopButton.setEnabled(canScrollUp);
        scrollToBottomButton.setEnabled(canScrollDown);
        trackView.setVisibility(VISIBLE);
    }

    private void scrollToPosition(int targetPosition) {
        if (recyclerView == null) {
            return;
        }
        int currentAnchorPosition = resolveCurrentAnchorPosition(targetPosition);
        if (currentAnchorPosition == RecyclerView.NO_POSITION
                || Math.abs(targetPosition - currentAnchorPosition) <= DIRECT_JUMP_DISTANCE_THRESHOLD) {
            recyclerView.smoothScrollToPosition(targetPosition);
            return;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(targetPosition, 0);
        } else {
            recyclerView.scrollToPosition(targetPosition);
        }
        recyclerView.post(this::refreshScrollerState);
    }

    private int resolveCurrentAnchorPosition(int targetPosition) {
        if (recyclerView == null) {
            return RecyclerView.NO_POSITION;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
            return targetPosition <= 0
                    ? linearLayoutManager.findFirstVisibleItemPosition()
                    : linearLayoutManager.findLastVisibleItemPosition();
        }
        return RecyclerView.NO_POSITION;
    }

    private void observeAdapter(@Nullable RecyclerView.Adapter<?> adapter) {
        if (observedAdapter == adapter) {
            return;
        }
        if (observedAdapter != null) {
            observedAdapter.unregisterAdapterDataObserver(adapterDataObserver);
        }
        observedAdapter = adapter;
        if (observedAdapter != null) {
            observedAdapter.registerAdapterDataObserver(adapterDataObserver);
        }
    }

    private void detachRecyclerView() {
        if (recyclerView != null) {
            recyclerView.removeOnScrollListener(scrollListener);
            recyclerView.removeOnLayoutChangeListener(layoutChangeListener);
        }
        observeAdapter(null);
        recyclerView = null;
        trackView.attachToRecyclerView(null);
        hideImmediately();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
