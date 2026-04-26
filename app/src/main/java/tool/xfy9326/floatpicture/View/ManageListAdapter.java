package tool.xfy9326.floatpicture.View;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ManageListAdapter extends AdvancedRecyclerView.Adapter<ManageListViewHolder> {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface EditPictureLauncher {
        void launch(Intent intent);
    }

    public interface BatchSelectionListener {
        void onSelectionChanged(int selectedCount, int totalCount, boolean allSelected);
    }

    private final Activity mActivity;
    private final Context appContext;
    private final EditPictureLauncher editPictureLauncher;
    private final ManageListPreviewLoader previewLoader;
    private final AtomicInteger refreshGeneration = new AtomicInteger();
    private ArrayList<ManageListItem> items = new ArrayList<>();
    private final LinkedHashSet<String> selectedPictureIds = new LinkedHashSet<>();
    private BatchSelectionListener batchSelectionListener;
    private boolean batchEditMode = false;

    public ManageListAdapter(Activity mActivity, EditPictureLauncher editPictureLauncher) {
        this.mActivity = mActivity;
        appContext = mActivity.getApplicationContext() != null ? mActivity.getApplicationContext() : mActivity;
        this.editPictureLauncher = editPictureLauncher;
        previewLoader = new ManageListPreviewLoader(appContext);
        setHasStableIds(true);
    }

    public void refreshData() {
        refreshData(null);
    }

    public void refreshData(@Nullable Runnable completionCallback) {
        previewLoader.invalidateQueuedPreviewRequests();
        int generation = refreshGeneration.incrementAndGet();
        ArrayList<ManageListItem> oldItems = copyItems(items);
        AppExecutors.io().execute(() -> {
            ArrayList<ManageListItem> newItems = buildItems();
            DiffUtil.DiffResult diffResult = calculateDiff(oldItems, newItems);
            MAIN_HANDLER.post(() -> {
                if (generation != refreshGeneration.get() || !isActivityAlive()) {
                    return;
                }
                submitItems(newItems, diffResult);
                previewLoader.scheduleCurrentViewportPreviewLoads(false, this::getPictureIdForPosition);
                if (completionCallback != null) {
                    completionCallback.run();
                }
            });
        });
    }

    public void setBatchSelectionListener(BatchSelectionListener batchSelectionListener) {
        this.batchSelectionListener = batchSelectionListener;
        notifyBatchSelectionChanged();
    }

    public void setBatchEditMode(boolean enabled) {
        if (batchEditMode == enabled) {
            return;
        }
        batchEditMode = enabled;
        if (!batchEditMode) {
            selectedPictureIds.clear();
        }
        notifyDataSetChanged();
        notifyBatchSelectionChanged();
    }

    public boolean isBatchEditMode() {
        return batchEditMode;
    }

    public ArrayList<String> getSelectedPictureIds() {
        return new ArrayList<>(selectedPictureIds);
    }

    public int getSelectedPictureCount() {
        return selectedPictureIds.size();
    }

    public void setAllBatchItemsSelected(boolean selected) {
        if (!batchEditMode) {
            return;
        }
        selectedPictureIds.clear();
        if (selected) {
            for (ManageListItem item : items) {
                selectedPictureIds.add(item.id);
            }
        }
        notifyDataSetChanged();
        notifyBatchSelectionChanged();
    }

    public void updatePreviewViewport(int firstVisiblePosition, int lastVisiblePosition, boolean idle) {
        if (items.isEmpty() || firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) {
            previewLoader.clearViewport();
            return;
        }

        int normalizedFirst = Math.max(Math.min(firstVisiblePosition, lastVisiblePosition), 0);
        int normalizedLast = Math.min(Math.max(firstVisiblePosition, lastVisiblePosition), items.size() - 1);
        boolean viewportChanged = previewLoader.updateViewport(normalizedFirst, normalizedLast);

        if (viewportChanged) {
            previewLoader.invalidateQueuedPreviewRequests();
        }
        previewLoader.scheduleCurrentViewportPreviewLoads(idle, this::getPictureIdForPosition);
    }

    public int refreshVisiblePreviewFallback(@NonNull RecyclerView recyclerView) {
        return previewLoader.refreshVisiblePreviewFallback(recyclerView, this::getPictureIdForPosition);
    }

    @Override
    public long getItemId(int position) {
        ManageListItem item = getItem(position);
        return item != null ? item.stableId : androidx.recyclerview.widget.RecyclerView.NO_ID;
    }

    private ArrayList<ManageListItem> buildItems() {
        PictureData.invalidateCache();
        PictureData pictureData = new PictureData();
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

    private DiffUtil.DiffResult calculateDiff(ArrayList<ManageListItem> oldItems, ArrayList<ManageListItem> newItems) {
        return DiffUtil.calculateDiff(new DiffUtil.Callback() {
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
    }

    private void submitItems(ArrayList<ManageListItem> newItems, DiffUtil.DiffResult diffResult) {
        items = newItems;
        pruneSelectedPictureIds();
        diffResult.dispatchUpdatesTo(this);
        notifyBatchSelectionChanged();
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
        holder.textView_Picture_Name.setText(item.pictureName);
        previewLoader.bindPreview(holder, item.id, position);
        holder.textView_Picture_Error.setVisibility(item.pictureExists ? View.GONE : View.VISIBLE);

        holder.checkBox_Picture_Select.setOnCheckedChangeListener(null);
        holder.checkBox_Picture_Select.setVisibility(batchEditMode ? View.VISIBLE : View.GONE);
        holder.checkBox_Picture_Select.setChecked(selectedPictureIds.contains(item.id));
        holder.checkBox_Picture_Select.setOnCheckedChangeListener((compoundButton, checked) -> {
            int currentPos = holder.getAdapterPosition();
            ManageListItem currentItem = getItem(currentPos);
            if (currentItem == null) {
                return;
            }
            if (checked) {
                selectedPictureIds.add(currentItem.id);
            } else {
                selectedPictureIds.remove(currentItem.id);
            }
            notifyBatchSelectionChanged();
        });
        holder.card_Picture_Item.setOnClickListener(view -> {
            int currentPos = holder.getAdapterPosition();
            toggleBatchSelection(currentPos);
        });

        SwitchCompat switch_Picture_Show = holder.switch_Picture_Show;
        switch_Picture_Show.setOnCheckedChangeListener(null);
        switch_Picture_Show.setChecked(item.visible);
        android.widget.CompoundButton.OnCheckedChangeListener visibilityChangeListener = (compoundButton, checked) -> {
            item.visible = checked;
            OverlayRuntimeController.setWindowVisible(mActivity, item.id, checked);
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
            String pictureId = currentItem.id;
            holder.switch_Picture_Show.setOnCheckedChangeListener(null);
            holder.button_Picture_Edit.setOnClickListener(null);
            holder.button_Picture_Delete.setOnClickListener(null);
            holder.checkBox_Picture_Select.setOnCheckedChangeListener(null);
            removeItemOptimistically(pictureId);
            MainActivity.SnackShow(mActivity, R.string.action_delete_window);
            deletePictureAsync(pictureId);
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
        holder.checkBox_Picture_Select.setOnCheckedChangeListener(null);
        holder.card_Picture_Item.setOnClickListener(null);
        previewLoader.recyclePreviewHolder(holder);
        super.onViewRecycled(holder);
    }

    private ManageListItem getItem(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    private String getPictureIdForPosition(int position) {
        ManageListItem item = getItem(position);
        return item != null ? item.id : null;
    }

    private void toggleBatchSelection(int position) {
        if (!batchEditMode) {
            return;
        }
        ManageListItem item = getItem(position);
        if (item == null) {
            return;
        }
        if (selectedPictureIds.contains(item.id)) {
            selectedPictureIds.remove(item.id);
        } else {
            selectedPictureIds.add(item.id);
        }
        notifyItemChanged(position);
        notifyBatchSelectionChanged();
    }

    private void pruneSelectedPictureIds() {
        if (selectedPictureIds.isEmpty()) {
            return;
        }
        LinkedHashSet<String> existingIds = new LinkedHashSet<>();
        for (ManageListItem item : items) {
            existingIds.add(item.id);
        }
        selectedPictureIds.retainAll(existingIds);
    }

    private void notifyBatchSelectionChanged() {
        if (batchSelectionListener == null) {
            return;
        }
        int selectedCount = selectedPictureIds.size();
        int totalCount = items.size();
        batchSelectionListener.onSelectionChanged(selectedCount, totalCount, totalCount > 0 && selectedCount == totalCount);
    }

    private void removeItemOptimistically(@NonNull String pictureId) {
        int removeIndex = -1;
        ArrayList<ManageListItem> newItems = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            ManageListItem item = items.get(index);
            if (item.id.equals(pictureId)) {
                removeIndex = index;
                continue;
            }
            newItems.add(item.copy());
        }
        if (removeIndex < 0) {
            return;
        }
        refreshGeneration.incrementAndGet();
        items = newItems;
        selectedPictureIds.remove(pictureId);
        previewLoader.invalidateQueuedPreviewRequests();
        notifyItemRemoved(removeIndex);
        notifyBatchSelectionChanged();
    }

    private void deletePictureAsync(@NonNull String pictureId) {
        AppExecutors.io().execute(() -> {
            try {
                PictureData deletePictureData = new PictureData();
                deletePictureData.setDataControl(pictureId);
                deletePictureData.remove();
                ImageMethods.clearAllTemp(appContext, pictureId);
                OverlayRuntimeController.deletePicture(appContext, pictureId);
            } catch (Exception e) {
                Log.w("ManageListAdapter", "deletePictureAsync failed: " + pictureId, e);
            } finally {
                MAIN_HANDLER.post(() -> {
                    if (!isActivityAlive()) {
                        return;
                    }
                    refreshData();
                    OverlayRuntimeController.refreshNotification(appContext, false);
                });
            }
        });
    }

    private boolean isActivityAlive() {
        return !mActivity.isFinishing() && !mActivity.isDestroyed();
    }

    private static ArrayList<ManageListItem> copyItems(ArrayList<ManageListItem> sourceItems) {
        ArrayList<ManageListItem> copiedItems = new ArrayList<>(sourceItems.size());
        for (ManageListItem item : sourceItems) {
            copiedItems.add(item.copy());
        }
        return copiedItems;
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

        private ManageListItem copy() {
            return new ManageListItem(
                    id,
                    pictureName,
                    visible,
                    touchAndMove,
                    overLayout,
                    pictureExists,
                    previewVersion
            );
        }
    }

}
