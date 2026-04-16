package tool.xfy9326.floatpicture.View;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ManageListAdapter extends AdvancedRecyclerView.Adapter<ManageListViewHolder> {
    public interface EditPictureLauncher {
        void launch(Intent intent);
    }

    private final Activity mActivity;
    private final EditPictureLauncher editPictureLauncher;
    private final PictureData pictureData;
    private LinkedHashMap<String, String> pictureInfo;
    private ArrayList<String> PictureId_Array;
    private ArrayList<String> PictureName_Array;

    public ManageListAdapter(Activity mActivity, EditPictureLauncher editPictureLauncher) {
        this.mActivity = mActivity;
        this.editPictureLauncher = editPictureLauncher;
        pictureData = new PictureData();
        updateData();
    }

    public void updateData() {
        pictureInfo = pictureData.getListArray();
        if (PictureId_Array == null) {
            PictureId_Array = new ArrayList<>();
            PictureName_Array = new ArrayList<>();
        } else {
            PictureId_Array.clear();
            PictureName_Array.clear();
        }
        if (pictureInfo != null) {
            for (Map.Entry<?, ?> entry : pictureInfo.entrySet()) {
                PictureId_Array.add(entry.getKey().toString());
                PictureName_Array.add(entry.getValue().toString());
            }
        }
    }

    @Override
    public int getItemCount() {
        return pictureInfo != null ? pictureInfo.size() : 0;
    }

    @Override
    public void onBindViewHolder(final ManageListViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION || pos >= PictureId_Array.size()) {
            return;
        }
        final String mPictureId = PictureId_Array.get(pos);
        final String mPictureName = PictureName_Array.get(pos);
        holder.textView_Picture_Name.setText(mPictureName);
        holder.textView_Picture_Id.setText(mPictureId);
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
        holder.imageView_Picture_Preview.setImageBitmap(ImageMethods.getPreviewBitmap(mActivity, mPictureId));
        final PictureData pictureData = new PictureData();
        pictureData.setDataControl(mPictureId);
        final boolean pictureVisible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
        final boolean touchAndMove = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        final boolean overLayout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        final boolean pictureExists = ImageMethods.isPictureFileExist(mPictureId);

        if (!pictureExists) {
            holder.textView_Picture_Error.setVisibility(View.VISIBLE);
        } else {
            holder.textView_Picture_Error.setVisibility(View.GONE);
        }

        SwitchCompat switch_Picture_Show = holder.switch_Picture_Show;
        switch_Picture_Show.setOnCheckedChangeListener(null);
        switch_Picture_Show.setChecked(pictureVisible);
        bindStatusChips(holder, pictureVisible, touchAndMove, overLayout);
        android.widget.CompoundButton.OnCheckedChangeListener visibilityChangeListener = new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean b) {
                ManageMethods.setWindowVisible(mActivity, pictureData, mPictureId, b);
                boolean actualVisible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
                if (switch_Picture_Show.isChecked() != actualVisible) {
                    switch_Picture_Show.setOnCheckedChangeListener(null);
                    switch_Picture_Show.setChecked(actualVisible);
                    switch_Picture_Show.setOnCheckedChangeListener(this);
                }
                bindStatusChips(holder, actualVisible, touchAndMove, overLayout);
            }
        };
        switch_Picture_Show.setOnCheckedChangeListener(visibilityChangeListener);

        holder.button_Picture_Edit.setOnClickListener(view -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                return;
            }
            PictureData currentPictureData = new PictureData();
            currentPictureData.setDataControl(mPictureId);
            boolean wasHidden = !currentPictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
            Intent intent = new Intent(mActivity, PictureSettingsActivity.class);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_MODE, true);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_ID, mPictureId);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_POSITION, currentPos);
            intent.putExtra(Config.INTENT_PICTURE_WAS_HIDDEN, wasHidden);
            editPictureLauncher.launch(intent);
        });

        holder.button_Picture_Delete.setOnClickListener(v -> {
            if (!ManageMethods.DeleteWin(mActivity, mPictureId)) {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    notifyItemChanged(currentPosition);
                }
                return;
            }
            updateData();
            holder.switch_Picture_Show.setOnCheckedChangeListener(null);
            holder.button_Picture_Edit.setOnClickListener(null);
            holder.button_Picture_Delete.setOnClickListener(null);
            int position1 = holder.getAdapterPosition();
            if (position1 != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                notifyItemRemoved(position1);
                notifyItemRangeChanged(position1, getItemCount() - position1);
            }
            MainActivity.SnackShow(mActivity, R.string.action_delete_window);
            ManageMethods.updateNotificationCount(mActivity);
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
        ImageMethods.releaseImageBitmap(holder.imageView_Picture_Preview);
        super.onViewRecycled(holder);
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
}
