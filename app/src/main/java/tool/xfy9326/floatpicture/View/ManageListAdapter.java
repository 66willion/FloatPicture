package tool.xfy9326.floatpicture.View;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ManageListAdapter extends AdvancedRecyclerView.Adapter<ManageListViewHolder> {
    private static final int PREVIEW_LOADER_THREAD_COUNT = 2;
    private static final int PREVIEW_PREFETCH_ITEM_COUNT = 2;
    private static final int PREVIEW_PRIORITY_VISIBLE = 0;
    private static final int PREVIEW_PRIORITY_PREFETCH = 1;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicLong PREVIEW_TASK_SEQUENCE = new AtomicLong();
    private static final ThreadPoolExecutor PREVIEW_LOAD_EXECUTOR = createPreviewLoadExecutor();

    public interface EditPictureLauncher {
        void launch(Intent intent);
    }

    private final Activity mActivity;
    private final Context previewContext;
    private final EditPictureLauncher editPictureLauncher;
    private final PictureData pictureData;
    private final Object previewRequestLock = new Object();
    private final ConcurrentHashMap<String, WeakReference<ManageListViewHolder>> attachedPreviewHolders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PreviewRequestSpec> previewRequestSpecs = new ConcurrentHashMap<>();
    private final AtomicLong previewGeneration = new AtomicLong();
    private volatile int visibleStartPosition = RecyclerView.NO_POSITION;
    private volatile int visibleEndPosition = RecyclerView.NO_POSITION;
    private volatile int visibleCenterPosition = RecyclerView.NO_POSITION;
    private ArrayList<ManageListItem> items = new ArrayList<>();

    public ManageListAdapter(Activity mActivity, EditPictureLauncher editPictureLauncher) {
        this.mActivity = mActivity;
        previewContext = mActivity.getApplicationContext() != null ? mActivity.getApplicationContext() : mActivity;
        this.editPictureLauncher = editPictureLauncher;
        pictureData = new PictureData();
        setHasStableIds(true);
        items = buildItems();
    }

    public void refreshData() {
        invalidateQueuedPreviewRequests();
        submitItems(buildItems());
        scheduleCurrentViewportPreviewLoads(false);
    }

    public void updatePreviewViewport(int firstVisiblePosition, int lastVisiblePosition, boolean idle) {
        if (items.isEmpty() || firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) {
            visibleStartPosition = RecyclerView.NO_POSITION;
            visibleEndPosition = RecyclerView.NO_POSITION;
            visibleCenterPosition = RecyclerView.NO_POSITION;
            invalidateQueuedPreviewRequests();
            return;
        }

        int normalizedFirst = Math.max(Math.min(firstVisiblePosition, lastVisiblePosition), 0);
        int normalizedLast = Math.min(Math.max(firstVisiblePosition, lastVisiblePosition), items.size() - 1);
        boolean viewportChanged = normalizedFirst != visibleStartPosition || normalizedLast != visibleEndPosition;

        visibleStartPosition = normalizedFirst;
        visibleEndPosition = normalizedLast;
        visibleCenterPosition = normalizedFirst + ((normalizedLast - normalizedFirst) / 2);

        if (viewportChanged) {
            invalidateQueuedPreviewRequests();
        }
        scheduleCurrentViewportPreviewLoads(idle);
    }

    public int refreshVisiblePreviewFallback(@NonNull RecyclerView recyclerView) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return 0;
        }
        int missingPreviewCount = 0;
        for (int position = visibleStartPosition; position <= visibleEndPosition; position++) {
            ManageListItem item = getItem(position);
            if (item == null) {
                continue;
            }
            RecyclerView.ViewHolder rawHolder = recyclerView.findViewHolderForAdapterPosition(position);
            if (!(rawHolder instanceof ManageListViewHolder)) {
                requestPreviewLoad(position, PREVIEW_PRIORITY_VISIBLE, true);
                missingPreviewCount++;
                continue;
            }
            ManageListViewHolder holder = (ManageListViewHolder) rawHolder;
            registerAttachedPreviewHolder(item.id, holder);
            Object imageTag = holder.imageView_Picture_Preview.getTag();
            boolean tagMatches = imageTag instanceof String && item.id.equals(imageTag);
            if (!tagMatches) {
                ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
                holder.imageView_Picture_Preview.setTag(item.id);
            }
            if (holder.imageView_Picture_Preview.getDrawable() != null) {
                continue;
            }
            Bitmap cachedPreview = ImageMethods.getCachedPreviewBitmap(item.id);
            if (cachedPreview != null) {
                holder.imageView_Picture_Preview.setImageBitmap(cachedPreview);
                continue;
            }
            requestPreviewLoad(position, PREVIEW_PRIORITY_VISIBLE, true);
            missingPreviewCount++;
        }
        return missingPreviewCount;
    }

    @Override
    public long getItemId(int position) {
        ManageListItem item = getItem(position);
        return item != null ? item.stableId : androidx.recyclerview.widget.RecyclerView.NO_ID;
    }

    private ArrayList<ManageListItem> buildItems() {
        PictureData.invalidateCache();
        LinkedHashMap<String, String> pictureInfo = pictureData.getListArray();
        ArrayList<ManageListItem> newItems = new ArrayList<>();
        if (pictureInfo != null) {
            PictureData itemPictureData = new PictureData();
            for (Map.Entry<?, ?> entry : pictureInfo.entrySet()) {
                String pictureId = entry.getKey().toString();
                itemPictureData.setDataControl(pictureId);
                newItems.add(new ManageListItem(
                        pictureId,
                        entry.getValue().toString(),
                        itemPictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED),
                        itemPictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE),
                        itemPictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT),
                        ImageMethods.isPictureFileExist(pictureId),
                        ImageMethods.getManagePreviewVersion(pictureId)
                ));
            }
        }
        return newItems;
    }

    private void submitItems(ArrayList<ManageListItem> newItems) {
        ArrayList<ManageListItem> oldItems = items;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).id.equals(newItems.get(newItemPosition).id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).hasSameContent(newItems.get(newItemPosition));
            }
        });
        items = newItems;
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onBindViewHolder(final ManageListViewHolder holder, int position) {
        ManageListItem item = getItem(position);
        if (item == null) {
            return;
        }
        registerAttachedPreviewHolder(item.id, holder);
        holder.textView_Picture_Name.setText(item.pictureName);
        holder.textView_Picture_Id.setText(item.id);
        bindPreview(holder, item.id, position);
        holder.textView_Picture_Error.setVisibility(item.pictureExists ? View.GONE : View.VISIBLE);

        SwitchCompat switch_Picture_Show = holder.switch_Picture_Show;
        switch_Picture_Show.setOnCheckedChangeListener(null);
        switch_Picture_Show.setChecked(item.visible);
        bindStatusChips(holder, item.visible, item.touchAndMove, item.overLayout);
        android.widget.CompoundButton.OnCheckedChangeListener visibilityChangeListener = (compoundButton, checked) -> {
            item.visible = checked;
            OverlayRuntimeController.setWindowVisible(mActivity, item.id, checked);
            // 实际状态由 overlay 进程写盘并广播回来；这里先反映用户操作，随后列表刷新会校正为真实状态。
            bindStatusChips(holder, checked, item.touchAndMove, item.overLayout);
        };
        switch_Picture_Show.setOnCheckedChangeListener(visibilityChangeListener);

        holder.button_Picture_Edit.setOnClickListener(view -> {
            int currentPos = holder.getAdapterPosition();
            ManageListItem currentItem = getItem(currentPos);
            if (currentItem == null) {
                return;
            }
            Intent intent = new Intent(mActivity, PictureSettingsActivity.class);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_MODE, true);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_ID, currentItem.id);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_POSITION, currentPos);
            intent.putExtra(Config.INTENT_PICTURE_WAS_HIDDEN, !currentItem.visible);
            editPictureLauncher.launch(intent);
        });

        holder.button_Picture_Delete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            ManageListItem currentItem = getItem(currentPos);
            if (currentItem == null) {
                return;
            }
            PictureData deletePictureData = new PictureData();
            deletePictureData.setDataControl(currentItem.id);
            deletePictureData.remove();
            ImageMethods.clearAllTemp(mActivity, currentItem.id);
            OverlayRuntimeController.deletePicture(mActivity, currentItem.id);
            holder.switch_Picture_Show.setOnCheckedChangeListener(null);
            holder.button_Picture_Edit.setOnClickListener(null);
            holder.button_Picture_Delete.setOnClickListener(null);
            refreshData();
            MainActivity.SnackShow(mActivity, R.string.action_delete_window);
            OverlayRuntimeController.refreshNotification(mActivity);
        });
    }

    @Override
    @NonNull
    public ManageListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        View mView = inflater.inflate(R.layout.adapter_manage_list, parent, false);
        return new ManageListViewHolder(mView);
    }

    @Override
    public void onViewRecycled(@NonNull ManageListViewHolder holder) {
        holder.switch_Picture_Show.setOnCheckedChangeListener(null);
        holder.button_Picture_Edit.setOnClickListener(null);
        holder.button_Picture_Delete.setOnClickListener(null);
        clearAttachedPreviewHolder(holder);
        holder.imageView_Picture_Preview.setTag(null);
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
        super.onViewRecycled(holder);
    }

    private ManageListItem getItem(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    private void bindPreview(ManageListViewHolder holder, String pictureId, int adapterPosition) {
        holder.imageView_Picture_Preview.setTag(pictureId);
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
        Bitmap cachedPreview = ImageMethods.getCachedPreviewBitmap(pictureId);
        if (cachedPreview != null) {
            holder.imageView_Picture_Preview.setImageBitmap(cachedPreview);
            return;
        }
        requestPreviewLoad(adapterPosition, PREVIEW_PRIORITY_VISIBLE);
    }

    private void bindStatusChips(ManageListViewHolder holder, boolean visible, boolean touchAndMove, boolean overLayout) {
        setChip(holder.textView_Picture_Visible,
                visible ? R.string.manage_state_visible : R.string.manage_state_hidden,
                visible ? R.drawable.bg_chip_success : R.drawable.bg_chip_neutral,
                visible ? R.color.colorStatusSuccessText : R.color.colorStatusNeutralText);
        setChip(holder.textView_Picture_TouchMode,
                touchAndMove ? R.string.manage_state_drag_enabled : R.string.manage_state_click_through,
                touchAndMove ? R.drawable.bg_chip_warning : R.drawable.bg_chip_info,
                touchAndMove ? R.color.colorStatusWarningText : R.color.colorStatusInfoText);
        setChip(holder.textView_Picture_Boundary,
                overLayout ? R.string.manage_state_allow_overflow : R.string.manage_state_screen_bound,
                overLayout ? R.drawable.bg_chip_warning : R.drawable.bg_chip_neutral,
                overLayout ? R.color.colorStatusWarningText : R.color.colorStatusNeutralText);
    }

    private void setChip(TextView textView, int textResId, int backgroundResId, int textColorResId) {
        textView.setText(textResId);
        textView.setBackgroundResource(backgroundResId);
        textView.setTextColor(ContextCompat.getColor(mActivity, textColorResId));
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

    private void scheduleCurrentViewportPreviewLoads(boolean includePrefetch) {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return;
        }
        boolean replaceVisibleRequests = includePrefetch;
        for (int position = visibleStartPosition; position <= visibleEndPosition; position++) {
            requestPreviewLoad(position, PREVIEW_PRIORITY_VISIBLE, replaceVisibleRequests);
        }
        if (includePrefetch) {
            scheduleNeighborPrefetchLoads();
        }
    }

    private void scheduleNeighborPrefetchLoads() {
        if (visibleStartPosition == RecyclerView.NO_POSITION || visibleEndPosition == RecyclerView.NO_POSITION) {
            return;
        }
        for (int offset = 1; offset <= PREVIEW_PREFETCH_ITEM_COUNT; offset++) {
            requestPreviewLoad(visibleStartPosition - offset, PREVIEW_PRIORITY_PREFETCH);
            requestPreviewLoad(visibleEndPosition + offset, PREVIEW_PRIORITY_PREFETCH);
        }
    }

    private void requestPreviewLoad(int adapterPosition, int priorityTier) {
        requestPreviewLoad(adapterPosition, priorityTier, false);
    }

    private void requestPreviewLoad(int adapterPosition, int priorityTier, boolean replaceExistingRequest) {
        ManageListItem item = getItem(adapterPosition);
        if (item == null || ImageMethods.getCachedPreviewBitmap(item.id) != null) {
            return;
        }
        long requestToken = PREVIEW_TASK_SEQUENCE.incrementAndGet();
        PreviewRequestSpec requestSpec = new PreviewRequestSpec(
                requestToken,
                previewGeneration.get(),
                priorityTier,
                resolveDistanceScore(adapterPosition)
        );
        if (!registerPreviewRequest(item.id, requestSpec, replaceExistingRequest)) {
            return;
        }
        PREVIEW_LOAD_EXECUTOR.execute(new PreviewLoadTask(item.id, adapterPosition, requestSpec, requestToken));
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

    private void invalidateQueuedPreviewRequests() {
        previewGeneration.incrementAndGet();
        PREVIEW_LOAD_EXECUTOR.getQueue().clear();
        synchronized (previewRequestLock) {
            previewRequestSpecs.clear();
        }
    }

    private int resolveDistanceScore(int adapterPosition) {
        if (visibleCenterPosition == RecyclerView.NO_POSITION) {
            return 0;
        }
        return Math.abs(adapterPosition - visibleCenterPosition);
    }

    private boolean isPreviewRequestStillRelevant(String pictureId, int adapterPosition, PreviewRequestSpec requestSpec) {
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
                                                    Bitmap previewBitmap,
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
                holder.imageView_Picture_Preview.setImageBitmap(previewBitmap);
            }
        } finally {
            clearPreviewRequest(pictureId, requestSpec);
        }
    }

    private static final class ManageListItem {
        private final String id;
        private final String pictureName;
        private final boolean touchAndMove;
        private final boolean overLayout;
        private final boolean pictureExists;
        private final long previewVersion;
        private final long stableId;
        private boolean visible;

        private ManageListItem(String id,
                               String pictureName,
                               boolean visible,
                               boolean touchAndMove,
                               boolean overLayout,
                               boolean pictureExists,
                               long previewVersion) {
            this.id = id;
            this.pictureName = pictureName;
            this.visible = visible;
            this.touchAndMove = touchAndMove;
            this.overLayout = overLayout;
            this.pictureExists = pictureExists;
            this.previewVersion = previewVersion;
            stableId = id.hashCode() & 0xffffffffL;
        }

        private boolean hasSameContent(ManageListItem other) {
            return visible == other.visible
                    && touchAndMove == other.touchAndMove
                    && overLayout == other.overLayout
                    && pictureExists == other.pictureExists
                    && previewVersion == other.previewVersion
                    && pictureName.equals(other.pictureName);
        }
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
                if (previewBitmap == null || previewBitmap.isRecycled() || !isPreviewRequestStillRelevant(pictureId, adapterPosition, requestSpec)) {
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
