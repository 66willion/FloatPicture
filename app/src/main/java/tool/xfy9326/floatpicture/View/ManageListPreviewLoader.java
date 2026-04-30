package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.R;

final class ManageListPreviewLoader {
    private static final int PREVIEW_LOADER_THREAD_COUNT = 2;
    private static final int PREVIEW_PREFETCH_ITEM_COUNT = 2;
    private static final int PREVIEW_PRIORITY_VISIBLE = 0;
    private static final int PREVIEW_PRIORITY_PREFETCH = 1;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicLong PREVIEW_TASK_SEQUENCE = new AtomicLong();

    private final Context previewContext;
    private final ThreadPoolExecutor previewLoadExecutor = createPreviewLoadExecutor();
    private final Object previewRequestLock = new Object();
    private final ConcurrentHashMap<String, WeakReference<ManageListViewHolder>> attachedPreviewHolders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PreviewRequestSpec> previewRequestSpecs = new ConcurrentHashMap<>();
    private final AtomicLong previewGeneration = new AtomicLong();
    private volatile int visibleStartPosition = RecyclerView.NO_POSITION;
    private volatile int visibleEndPosition = RecyclerView.NO_POSITION;
    private volatile int visibleCenterPosition = RecyclerView.NO_POSITION;
    private volatile boolean released = false;

    ManageListPreviewLoader(@NonNull Context previewContext) {
        this.previewContext = previewContext;
    }

    void clearViewport() {
        visibleStartPosition = RecyclerView.NO_POSITION;
        visibleEndPosition = RecyclerView.NO_POSITION;
        visibleCenterPosition = RecyclerView.NO_POSITION;
        invalidateQueuedPreviewRequests();
    }

    boolean updateViewport(int firstVisiblePosition, int lastVisiblePosition) {
        boolean viewportChanged = firstVisiblePosition != visibleStartPosition || lastVisiblePosition != visibleEndPosition;
        visibleStartPosition = firstVisiblePosition;
        visibleEndPosition = lastVisiblePosition;
        visibleCenterPosition = firstVisiblePosition + ((lastVisiblePosition - firstVisiblePosition) / 2);
        return viewportChanged;
    }

    int refreshVisiblePreviewFallback(@NonNull RecyclerView recyclerView, @NonNull PreviewItemProvider itemProvider) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return 0;
        }
        int missingPreviewCount = 0;
        for (int position = visibleStartPosition; position <= visibleEndPosition; position++) {
            String pictureId = itemProvider.getPictureId(position);
            if (pictureId == null) {
                continue;
            }
            RecyclerView.ViewHolder rawHolder = recyclerView.findViewHolderForAdapterPosition(position);
            if (!(rawHolder instanceof ManageListViewHolder)) {
                requestPreviewLoad(position, pictureId, PREVIEW_PRIORITY_VISIBLE, true);
                missingPreviewCount++;
                continue;
            }
            ManageListViewHolder holder = (ManageListViewHolder) rawHolder;
            Object imageTag = holder.imageView_Picture_Preview.getTag();
            boolean tagMatches = imageTag instanceof String && pictureId.equals(imageTag);
            if (!tagMatches) {
                clearAttachedPreviewHolder(holder);
                ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
                holder.imageView_Picture_Preview.setTag(pictureId);
            }
            registerAttachedPreviewHolder(pictureId, holder);
            if (holder.imageView_Picture_Preview.getDrawable() != null) {
                continue;
            }
            Bitmap cachedPreview = ImageMethods.getCachedPreviewBitmap(pictureId);
            if (cachedPreview != null) {
                ImageMethods.setManagePreviewBitmap(holder.imageView_Picture_Preview, cachedPreview);
                continue;
            }
            requestPreviewLoad(position, pictureId, PREVIEW_PRIORITY_VISIBLE, true);
            missingPreviewCount++;
        }
        return missingPreviewCount;
    }

    void bindPreview(@NonNull ManageListViewHolder holder, @NonNull String pictureId, int adapterPosition) {
        clearAttachedPreviewHolder(holder);
        registerAttachedPreviewHolder(pictureId, holder);
        holder.imageView_Picture_Preview.setTag(pictureId);
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
        Bitmap cachedPreview = ImageMethods.getCachedPreviewBitmap(pictureId);
        if (cachedPreview != null) {
            ImageMethods.setManagePreviewBitmap(holder.imageView_Picture_Preview, cachedPreview);
            return;
        }
        requestPreviewLoad(adapterPosition, pictureId, PREVIEW_PRIORITY_VISIBLE);
    }

    void recyclePreviewHolder(@NonNull ManageListViewHolder holder) {
        clearAttachedPreviewHolder(holder);
        holder.imageView_Picture_Preview.setTag(null);
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
    }

    void scheduleCurrentViewportPreviewLoads(boolean includePrefetch, @NonNull PreviewItemProvider itemProvider) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return;
        }
        boolean replaceVisibleRequests = includePrefetch;
        for (int position = visibleStartPosition; position <= visibleEndPosition; position++) {
            requestPreviewLoad(position, itemProvider.getPictureId(position), PREVIEW_PRIORITY_VISIBLE, replaceVisibleRequests);
        }
        if (includePrefetch) {
            scheduleNeighborPrefetchLoads(itemProvider);
        }
    }

    void invalidateQueuedPreviewRequests() {
        previewGeneration.incrementAndGet();
        previewLoadExecutor.getQueue().clear();
        synchronized (previewRequestLock) {
            previewRequestSpecs.clear();
        }
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        clearViewport();
        attachedPreviewHolders.clear();
        previewLoadExecutor.shutdownNow();
    }

    private void scheduleNeighborPrefetchLoads(@NonNull PreviewItemProvider itemProvider) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return;
        }
        for (int offset = 1; offset <= PREVIEW_PREFETCH_ITEM_COUNT; offset++) {
            requestPreviewLoad(visibleStartPosition - offset, itemProvider.getPictureId(visibleStartPosition - offset), PREVIEW_PRIORITY_PREFETCH);
            requestPreviewLoad(visibleEndPosition + offset, itemProvider.getPictureId(visibleEndPosition + offset), PREVIEW_PRIORITY_PREFETCH);
        }
    }

    private void requestPreviewLoad(int adapterPosition, @Nullable String pictureId, int priorityTier) {
        requestPreviewLoad(adapterPosition, pictureId, priorityTier, false);
    }

    private void requestPreviewLoad(int adapterPosition,
                                    @Nullable String pictureId,
                                    int priorityTier,
                                    boolean replaceExistingRequest) {
        if (pictureId == null || ImageMethods.getCachedPreviewBitmap(pictureId) != null) {
            return;
        }
        if (released) {
            return;
        }
        long requestToken = PREVIEW_TASK_SEQUENCE.incrementAndGet();
        PreviewRequestSpec requestSpec = new PreviewRequestSpec(
                requestToken,
                previewGeneration.get(),
                priorityTier,
                resolveDistanceScore(adapterPosition)
        );
        if (!registerPreviewRequest(pictureId, requestSpec, replaceExistingRequest)) {
            return;
        }
        try {
            previewLoadExecutor.execute(new PreviewLoadTask(pictureId, adapterPosition, requestSpec, requestToken));
        } catch (RejectedExecutionException e) {
            clearPreviewRequest(pictureId, requestSpec);
        }
    }

    private boolean registerPreviewRequest(String pictureId,
                                           PreviewRequestSpec requestSpec,
                                           boolean replaceExistingRequest) {
        synchronized (previewRequestLock) {
            PreviewRequestSpec existingRequest = previewRequestSpecs.get(pictureId);
            if (!replaceExistingRequest && existingRequest != null && existingRequest.isSameOrBetterThan(requestSpec)) {
                return false;
            }
            previewRequestSpecs.put(pictureId, requestSpec);
            return true;
        }
    }

    private void clearPreviewRequest(String pictureId, PreviewRequestSpec requestSpec) {
        synchronized (previewRequestLock) {
            previewRequestSpecs.remove(pictureId, requestSpec);
        }
    }

    private int resolveDistanceScore(int adapterPosition) {
        if (visibleCenterPosition == RecyclerView.NO_POSITION) {
            return 0;
        }
        return Math.abs(adapterPosition - visibleCenterPosition);
    }

    private boolean isPreviewRequestStillRelevant(String pictureId, int adapterPosition, PreviewRequestSpec requestSpec) {
        if (released) {
            return false;
        }
        if (requestSpec.generation != previewGeneration.get()) {
            return false;
        }
        PreviewRequestSpec latestRequest = previewRequestSpecs.get(pictureId);
        if (latestRequest == null || !latestRequest.equals(requestSpec)) {
            return false;
        }
        if (requestSpec.priorityTier == PREVIEW_PRIORITY_VISIBLE) {
            return isPositionWithinVisibleViewport(adapterPosition);
        }
        return true;
    }

    private boolean isPositionWithinVisibleViewport(int adapterPosition) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return true;
        }
        return adapterPosition >= visibleStartPosition && adapterPosition <= visibleEndPosition;
    }

    private void registerAttachedPreviewHolder(String pictureId, ManageListViewHolder holder) {
        attachedPreviewHolders.put(pictureId, new WeakReference<>(holder));
    }

    private void clearAttachedPreviewHolder(ManageListViewHolder holder) {
        Object imageTag = holder.imageView_Picture_Preview.getTag();
        if (!(imageTag instanceof String)) {
            return;
        }
        String pictureId = (String) imageTag;
        WeakReference<ManageListViewHolder> holderReference = attachedPreviewHolders.get(pictureId);
        if (holderReference != null && holderReference.get() == holder) {
            attachedPreviewHolders.remove(pictureId, holderReference);
        }
    }

    private void applyPreviewBitmapToAttachedHolder(String pictureId,
                                                    int adapterPosition,
                                                    @Nullable Bitmap previewBitmap,
                                                    PreviewRequestSpec requestSpec) {
        try {
            if (!isPreviewRequestStillRelevant(pictureId, adapterPosition, requestSpec)) {
                return;
            }
            WeakReference<ManageListViewHolder> holderReference = attachedPreviewHolders.get(pictureId);
            if (holderReference == null) {
                return;
            }
            ManageListViewHolder holder = holderReference.get();
            if (holder == null) {
                attachedPreviewHolders.remove(pictureId, holderReference);
                return;
            }
            Object imageTag = holder.imageView_Picture_Preview.getTag();
            if (imageTag instanceof String && pictureId.equals(imageTag)) {
                if (previewBitmap != null && !previewBitmap.isRecycled()) {
                    ImageMethods.setManagePreviewBitmap(holder.imageView_Picture_Preview, previewBitmap);
                    return;
                }
                ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
                holder.textView_Picture_Error.setText(R.string.error_picture_preview_failed);
                holder.textView_Picture_Error.setVisibility(android.view.View.VISIBLE);
            }
        } finally {
            clearPreviewRequest(pictureId, requestSpec);
        }
    }

    private static ThreadPoolExecutor createPreviewLoadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                PREVIEW_LOADER_THREAD_COUNT,
                PREVIEW_LOADER_THREAD_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    interface PreviewItemProvider {
        @Nullable
        String getPictureId(int adapterPosition);
    }

    private static final class PreviewRequestSpec {
        private final long requestToken;
        private final long generation;
        private final int priorityTier;
        private final int distanceScore;

        private PreviewRequestSpec(long requestToken, long generation, int priorityTier, int distanceScore) {
            this.requestToken = requestToken;
            this.generation = generation;
            this.priorityTier = priorityTier;
            this.distanceScore = distanceScore;
        }

        private boolean isSameOrBetterThan(PreviewRequestSpec other) {
            if (generation != other.generation) {
                return generation > other.generation;
            }
            if (priorityTier != other.priorityTier) {
                return priorityTier < other.priorityTier;
            }
            return distanceScore <= other.distanceScore;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PreviewRequestSpec)) {
                return false;
            }
            PreviewRequestSpec other = (PreviewRequestSpec) obj;
            return requestToken == other.requestToken
                    && generation == other.generation
                    && priorityTier == other.priorityTier
                    && distanceScore == other.distanceScore;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(requestToken);
            result = 31 * result + Long.hashCode(generation);
            result = 31 * result + priorityTier;
            result = 31 * result + distanceScore;
            return result;
        }
    }

    private final class PreviewLoadTask implements Runnable, Comparable<PreviewLoadTask> {
        private final String pictureId;
        private final int adapterPosition;
        private final PreviewRequestSpec requestSpec;
        private final long sequence;

        private PreviewLoadTask(String pictureId, int adapterPosition, PreviewRequestSpec requestSpec, long sequence) {
            this.pictureId = pictureId;
            this.adapterPosition = adapterPosition;
            this.requestSpec = requestSpec;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            boolean postedToMainThread = false;
            try {
                if (!isPreviewRequestStillRelevant(pictureId, adapterPosition, requestSpec)) {
                    return;
                }
                Bitmap previewBitmap = ImageMethods.getPreviewBitmap(previewContext, pictureId);
                if (!isPreviewRequestStillRelevant(pictureId, adapterPosition, requestSpec)) {
                    return;
                }
                postedToMainThread = true;
                MAIN_HANDLER.post(() -> applyPreviewBitmapToAttachedHolder(pictureId, adapterPosition, previewBitmap, requestSpec));
            } finally {
                if (!postedToMainThread) {
                    clearPreviewRequest(pictureId, requestSpec);
                }
            }
        }

        @Override
        public int compareTo(@NonNull PreviewLoadTask other) {
            if (requestSpec.priorityTier != other.requestSpec.priorityTier) {
                return Integer.compare(requestSpec.priorityTier, other.requestSpec.priorityTier);
            }
            if (requestSpec.distanceScore != other.requestSpec.distanceScore) {
                return Integer.compare(requestSpec.distanceScore, other.requestSpec.distanceScore);
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
