package tool.xfy9326.floatpicture.Activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import tool.xfy9326.floatpicture.View.AdvancedRecyclerView;
import tool.xfy9326.floatpicture.View.ManageListAdapter;
import tool.xfy9326.floatpicture.View.RecyclerFastScrollerView;

final class ManageListPreviewController {
    private static final long PREVIEW_FALLBACK_FIRST_DELAY_MS = 40L;
    private static final long PREVIEW_FALLBACK_SECOND_DELAY_MS = 140L;

    private final ManageListAdapter adapter;
    private final AdvancedRecyclerView recyclerView;
    @Nullable
    private final RecyclerFastScrollerView fastScrollerView;
    private final Runnable firstFallbackRunnable = this::runFirstFallback;
    private final Runnable secondFallbackRunnable = this::runSecondFallback;
    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateViewport(false);
            handleFallbackScheduling();
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            updateViewport(newState == RecyclerView.SCROLL_STATE_IDLE);
            handleFallbackScheduling();
        }
    };

    ManageListPreviewController(@NonNull ManageListAdapter adapter,
                                @NonNull AdvancedRecyclerView recyclerView,
                                @Nullable RecyclerFastScrollerView fastScrollerView) {
        this.adapter = adapter;
        this.recyclerView = recyclerView;
        this.fastScrollerView = fastScrollerView;
    }

    void attach() {
        recyclerView.addOnScrollListener(scrollListener);
        refreshViewportWhenReady();
    }

    void detach() {
        cancelFallbacks();
        recyclerView.removeOnScrollListener(scrollListener);
    }

    void refreshViewportWhenReady() {
        recyclerView.post(() -> updateViewport(true));
    }

    private void updateViewport(boolean idle) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager linearLayoutManager)) {
            return;
        }
        int firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition();
        adapter.updatePreviewViewport(firstVisiblePosition, lastVisiblePosition, idle);
    }

    private void handleFallbackScheduling() {
        if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
            scheduleFallbacks();
        } else {
            cancelFallbacks();
        }
    }

    private void scheduleFallbacks() {
        cancelFallbacks();
        recyclerView.postDelayed(firstFallbackRunnable, PREVIEW_FALLBACK_FIRST_DELAY_MS);
    }

    private void cancelFallbacks() {
        recyclerView.removeCallbacks(firstFallbackRunnable);
        recyclerView.removeCallbacks(secondFallbackRunnable);
    }

    private void runFirstFallback() {
        if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        int remainingBlankCount = adapter.refreshVisiblePreviewFallback(recyclerView);
        refreshFastScroller();
        recyclerView.removeCallbacks(secondFallbackRunnable);
        if (remainingBlankCount > 0) {
            recyclerView.postDelayed(secondFallbackRunnable, PREVIEW_FALLBACK_SECOND_DELAY_MS);
        }
    }

    private void runSecondFallback() {
        if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        adapter.refreshVisiblePreviewFallback(recyclerView);
        refreshFastScroller();
    }

    private void refreshFastScroller() {
        if (fastScrollerView != null) {
            fastScrollerView.refreshScrollerState();
        }
    }
}
