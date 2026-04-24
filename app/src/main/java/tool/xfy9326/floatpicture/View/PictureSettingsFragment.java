package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class PictureSettingsFragment extends PreferenceFragmentCompat {
    private final static String WINDOW_CREATED = "WINDOW_CREATED";
    private static final int THREE_DECIMAL_SCALE = 1000;
    private static final int PERCENT_SCALE = 100;
    private static final int MAX_CORNER_RADIUS_PERCENT = 25;
    private static final int MAX_EDGE_FEATHER_PERCENT = 15;
    private boolean Edit_Mode;
    private boolean Window_Created;
    private boolean onUseEditPicture = false;
    private boolean changesSaved = false;
    private LayoutInflater inflater;
    private PictureData pictureData;
    private String PictureId;
    private String PictureName;
    private WindowManager windowManager;
    private ActivityResultLauncher<String> replacePictureLauncher;
    private volatile FloatImageView floatImageView;
    private Bitmap bitmap;
    private Bitmap bitmap_Edit;
    private FloatImageView floatImageView_Edit;
    private boolean touch_and_move;
    private float default_zoom;
    private float zoom;
    private float zoom_temp;
    private float picture_degree;
    private float picture_degree_temp;
    private float picture_alpha;
    private float picture_alpha_temp;
    private float picture_corner_radius_ratio;
    private float picture_corner_radius_ratio_temp;
    private int picture_corner_radius_mask;
    private int picture_corner_radius_mask_temp;
    private float picture_edge_feather_ratio;
    private float picture_edge_feather_ratio_temp;
    private int picture_edge_feather_mask;
    private int picture_edge_feather_mask_temp;
    private int position_x;
    private int position_y;
    private int position_x_temp;
    private int position_y_temp;
    private boolean allow_picture_over_layout;
    /** 进入编辑时窗口是否处于隐藏状态；编辑完成后恢复该状态 */
    private boolean wasHidden = false;
    private volatile boolean fragmentClosing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window_Created = false;
        Edit_Mode = false;
        changesSaved = false;
        pictureData = new PictureData();
        inflater = LayoutInflater.from(requireActivity());
        windowManager = WindowsMethods.getWindowManager(requireActivity());
        replacePictureLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onReplacementPictureSelected);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_picture_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fragmentClosing = false;
        restoreData(savedInstanceState);
        PreferenceSet();
        setMode();
    }

    @Override
    public void onDestroy() {
        fragmentClosing = true;
        boolean finishing = getActivity() != null && getActivity().isFinishing();
        if (PictureId != null) {
            ImageMethods.clearStagedReplacementImage(PictureId);
        }
        if (Edit_Mode && finishing && !changesSaved && PictureId != null) {
            ImageMethods.clearPendingReplacementImage(PictureId);
        }
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(WINDOW_CREATED, true);
        super.onSaveInstanceState(outState);
    }

    private void restoreData(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Window_Created = savedInstanceState.getBoolean(WINDOW_CREATED, false);
        }
    }

    private void setMode() {
        Intent intent = Objects.requireNonNull(requireActivity().getIntent());
        Edit_Mode = intent.getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false);
        wasHidden = intent.getBooleanExtra(Config.INTENT_PICTURE_WAS_HIDDEN, false);
        updateReplacePicturePreferenceVisibility();
        AlertDialog.Builder loading = new AlertDialog.Builder(requireActivity());
        loading.setCancelable(false);
        View mView = inflater.inflate(R.layout.dialog_loading, requireActivity().findViewById(R.id.layout_dialog_loading));
        loading.setView(mView);
        final AlertDialog alertDialog = loading.show();
        new Thread(() -> {
            if (!Window_Created) {
                if (Edit_Mode) {
                    //Edit
                    PictureId = intent.getStringExtra(Config.INTENT_PICTURE_EDIT_ID);
                    if (PictureId == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    pictureData.setDataControl(PictureId);
                    LinkedHashMap<String, String> listArray = pictureData.getListArray();
                    if (listArray == null || !listArray.containsKey(PictureId)) {
                        finishWithError(alertDialog);
                        return;
                    }
                    PictureName = listArray.get(PictureId);
                    position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
                    position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
                    picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
                    picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
                    picture_corner_radius_ratio = pictureData.getFloat(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO);
                    picture_corner_radius_mask = pictureData.getInt(Config.DATA_PICTURE_CORNER_RADIUS_MASK, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK);
                    picture_edge_feather_ratio = pictureData.getFloat(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO);
                    picture_edge_feather_mask = pictureData.getInt(Config.DATA_PICTURE_EDGE_FEATHER_MASK, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK);
                    touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
                    allow_picture_over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
                    bitmap = loadCurrentSourceBitmap();
                    if (bitmap == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
                } else {
                    //New
                    PictureId = ImageMethods.setNewImage(requireActivity(), intent.getData());
                    if (PictureId == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    pictureData.setDataControl(PictureId);
                    PictureName = getString(R.string.new_picture_name);
                    position_x = Config.DATA_DEFAULT_PICTURE_POSITION_X;
                    position_y = Config.DATA_DEFAULT_PICTURE_POSITION_Y;
                    picture_alpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
                    picture_degree = Config.DATA_DEFAULT_PICTURE_DEGREE;
                    picture_corner_radius_ratio = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO;
                    picture_corner_radius_mask = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK;
                    picture_edge_feather_ratio = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO;
                    picture_edge_feather_mask = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK;
                    touch_and_move = Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE;
                    allow_picture_over_layout = Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT;
                    bitmap = ImageMethods.getEditSourceBitmap(requireContext(), PictureId);
                    if (bitmap == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    zoom = default_zoom;
                }
                if (shouldAbortFragmentWork()) {
                    dismissDialogIfShowing(alertDialog);
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (shouldAbortFragmentWork()) {
                        dismissDialogIfShowing(alertDialog);
                        return;
                    }
                    bindPreferenceValues();
                    updateReplacePicturePreferenceVisibility();
                    showWorkingWindowPreview(picture_alpha);
                    dismissDialogIfShowing(alertDialog);
                });
            }
        }).start();
    }

    @NonNull
    private Preference requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void PreferenceSet() {
        requirePreference(Config.PREFERENCE_PICTURE_NAME).setOnPreferenceClickListener(preference -> {
            setPictureName();
            return true;
        });
        Preference replacePreference = requirePreference(Config.PREFERENCE_PICTURE_REPLACE);
        replacePreference.setVisible(false);
        replacePreference.setOnPreferenceClickListener(preference -> {
            replacePicture();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_RESIZE).setOnPreferenceClickListener(preference -> {
            setPictureSize();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_FIT_SCREEN_HEIGHT).setOnPreferenceClickListener(preference -> {
            fitPictureToScreenHeight();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_DEGREE).setOnPreferenceClickListener(preference -> {
            setPictureDegree();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_ALPHA).setOnPreferenceClickListener(preference -> {
            showPictureAlphaDialog();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_CORNER_RADIUS).setOnPreferenceClickListener(preference -> {
            setPictureCornerRadius();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_EDGE_FEATHER).setOnPreferenceClickListener(preference -> {
            setPictureEdgeFeather();
            return true;
        });
        SwitchPreferenceCompat preference_touch_and_move = requireSwitchPreference(Config.PREFERENCE_PICTURE_TOUCH_AND_MOVE);
        preference_touch_and_move.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((boolean) newValue) {
                PictureTouchAndMoveAlert();
                return false;
            } else {
                setPictureTouchAndMove(false);
                return true;
            }
        });
        SwitchPreferenceCompat preference_over_layout = requireSwitchPreference(Config.PREFERENCE_ALLOW_PICTURE_OVER_LAYOUT);
        preference_over_layout.setOnPreferenceChangeListener((preference, newValue) -> {
            setAllowPictureOverLayout((boolean) newValue);
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_POSITION).setOnPreferenceClickListener(preference -> {
            setPicturePosition();
            return true;
        });
    }

    @NonNull
    private SwitchPreferenceCompat requireSwitchPreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void bindPreferenceValues() {
        requireSwitchPreference(Config.PREFERENCE_PICTURE_TOUCH_AND_MOVE).setChecked(touch_and_move);
        requireSwitchPreference(Config.PREFERENCE_ALLOW_PICTURE_OVER_LAYOUT).setChecked(allow_picture_over_layout);
        updateAppearancePreferenceSummaries();
    }

    private void updateReplacePicturePreferenceVisibility() {
        Preference replacePreference = findPreference(Config.PREFERENCE_PICTURE_REPLACE);
        if (replacePreference != null) {
            replacePreference.setVisible(Edit_Mode);
        }
    }

    @Nullable
    private Bitmap loadCurrentSourceBitmap() {
        if (Edit_Mode && PictureId != null) {
            Bitmap pendingBitmap = ImageMethods.getPendingEditSourceBitmap(PictureId);
            if (pendingBitmap != null) {
                return pendingBitmap;
            }
        }
        return ImageMethods.getEditSourceBitmap(requireContext(), PictureId);
    }

    private boolean ensureSourceBitmapLoaded() {
        if (bitmap != null && !bitmap.isRecycled()) {
            return true;
        }
        bitmap = loadCurrentSourceBitmap();
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = null;
            ApplicationMethods.showToast(requireContext(), R.string.picture_settings_open_failed);
            return false;
        }
        return true;
    }

    private void releaseSourceBitmap() {
        ImageMethods.recycleBitmap(bitmap);
        bitmap = null;
    }

    private void releaseEditResources() {
        releaseEditResources(false);
    }

    private void releaseEditResources(boolean releaseSourceBitmapNow) {
        FloatImageView editView = floatImageView_Edit;
        Bitmap editBitmap = bitmap_Edit;
        floatImageView_Edit = null;
        bitmap_Edit = null;
        if (editView != null) {
            removeViewIfAttached(editView);
            ImageMethods.releasePictureView(editView);
        }
        ImageMethods.recycleBitmap(editBitmap);
        if (releaseSourceBitmapNow) {
            releaseSourceBitmap();
        }
        onUseEditPicture = false;
    }

    private void setAllowPictureOverLayout(boolean allow) {
        allow_picture_over_layout = allow;
        showWorkingWindowPreview(picture_alpha);
    }

    private void setPictureTouchAndMove(boolean touchable_and_moveable) {
        if (touch_and_move) {
            Point previewPosition = getPreviewPosition(position_x, position_y);
            position_x = previewPosition.x;
            position_y = previewPosition.y;
        }
        touch_and_move = touchable_and_moveable;
        showWorkingWindowPreview(picture_alpha);
    }

    private void PictureTouchAndMoveAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.settings_picture_touchable_and_moveable);
        builder.setMessage(R.string.settings_picture_touchable_and_moveable_warn);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.done, (dialog, which) -> {
            requireSwitchPreference(Config.PREFERENCE_PICTURE_TOUCH_AND_MOVE).setChecked(true);
            setPictureTouchAndMove(true);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void finishWithError(AlertDialog alertDialog) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || getActivity() == null) {
                return;
            }
            dismissDialogIfShowing(alertDialog);
            fragmentClosing = true;
            ApplicationMethods.showToast(requireContext(), R.string.picture_settings_open_failed);
            requireActivity().finish();
        });
    }

    private void setPictureName() {
        View mView = inflater.inflate(R.layout.dialog_edit_text, requireActivity().findViewById(R.id.layout_dialog_edit_text));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_name);
        final EditText editText = mView.findViewById(R.id.edittext_dialog);
        editText.setText(PictureName);
        dialog.setPositiveButton(R.string.done, (dialog12, which) -> {
            if (editText.getText().toString().isEmpty()) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_picture_name_warn);
            } else {
                PictureName = editText.getText().toString();
            }
        });
        dialog.setNegativeButton(R.string.cancel, (dialog1, which) -> {
            if (editText.getText().toString().isEmpty()) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_picture_name_warn);
            }
        });
        dialog.setView(mView);
        dialog.show();
    }

    private void replacePicture() {
        if (!Edit_Mode || PictureId == null || replacePictureLauncher == null) {
            return;
        }
        replacePictureLauncher.launch("image/*");
    }

    private void onReplacementPictureSelected(@Nullable Uri uri) {
        if (uri == null || !Edit_Mode || PictureId == null || shouldAbortFragmentWork()) {
            return;
        }
        final Context appContext = requireContext().getApplicationContext();
        AlertDialog.Builder loading = new AlertDialog.Builder(requireActivity());
        loading.setCancelable(false);
        View loadingView = inflater.inflate(R.layout.dialog_loading, requireActivity().findViewById(R.id.layout_dialog_loading));
        loading.setView(loadingView);
        final AlertDialog alertDialog = loading.show();
        new Thread(() -> {
            if (!ImageMethods.stageReplacementImage(appContext, PictureId, uri)) {
                notifyReplacePictureFailed(alertDialog);
                return;
            }
            Bitmap replacementBitmap = ImageMethods.getStagedReplacementBitmap(PictureId);
            if (replacementBitmap == null) {
                notifyReplacePictureFailed(alertDialog);
                return;
            }
            float replacementDefaultZoom = ImageMethods.getDefaultZoom(appContext, replacementBitmap, false);
            if (!ImageMethods.applyStagedReplacementImage(PictureId)) {
                ImageMethods.recycleBitmap(replacementBitmap);
                notifyReplacePictureFailed(alertDialog);
                return;
            }
            if (shouldAbortFragmentWork()) {
                ImageMethods.recycleBitmap(replacementBitmap);
                requireActivity().runOnUiThread(() -> dismissDialogIfShowing(alertDialog));
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (shouldAbortFragmentWork()) {
                    ImageMethods.recycleBitmap(replacementBitmap);
                    dismissDialogIfShowing(alertDialog);
                    return;
                }
                dismissDialogIfShowing(alertDialog);
                releaseSourceBitmap();
                bitmap = replacementBitmap;
                default_zoom = replacementDefaultZoom;
                showWorkingWindowPreview(picture_alpha, true);
                ApplicationMethods.showToast(requireContext(), R.string.picture_settings_replace_success);
            });
        }).start();
    }

    private void notifyReplacePictureFailed(AlertDialog alertDialog) {
        if (PictureId != null) {
            ImageMethods.clearStagedReplacementImage(PictureId);
        }
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || getActivity() == null) {
                return;
            }
            dismissDialogIfShowing(alertDialog);
            ApplicationMethods.showToast(requireContext(), R.string.picture_settings_replace_failed);
        });
    }

    private void setPictureSize() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }

        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_resize);
        dialog.setCancelable(false);
        final float maxSize = roundToThreeDecimals(ImageMethods.getDefaultZoom(requireContext(), bitmap, true));
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_picture_resize_size);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(Math.max(1, toThreeDecimalProgress(maxSize)));
        seekBar.setProgress(Math.max(1, Math.min(seekBar.getMax(), toThreeDecimalProgress(zoom))));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(zoom));
        zoom_temp = roundToThreeDecimals(zoom);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > 0) {
                    zoom_temp = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                    editText.setText(formatThreeDecimal(zoom_temp));
                    showPreview(
                            zoom_temp,
                            picture_degree,
                            picture_alpha,
                            position_x,
                            position_y,
                            touch_and_move,
                            allow_picture_over_layout,
                            fromUser ? OverlayRuntimeController.PREVIEW_MODE_OUTLINE : OverlayRuntimeController.PREVIEW_MODE_FULL,
                            false,
                            touch_and_move
                    );
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() > 0) {
                    showPreview(
                            zoom_temp,
                            picture_degree,
                            picture_alpha,
                            position_x,
                            position_y,
                            touch_and_move,
                            allow_picture_over_layout,
                            OverlayRuntimeController.PREVIEW_MODE_FULL,
                            false,
                            touch_and_move
                    );
                }
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float edittext_temp = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (edittext_temp > 0 && (allow_picture_over_layout || edittext_temp <= maxSize)) {
                    zoom_temp = edittext_temp;
                    editText.setText(formatThreeDecimal(zoom_temp));
                    boolean updatedBySeekBar = false;
                    if (zoom_temp <= maxSize) {
                        int progress = Math.max(1, Math.min(seekBar.getMax(), toThreeDecimalProgress(zoom_temp)));
                        if (seekBar.getProgress() != progress) {
                            seekBar.setProgress(progress);
                            updatedBySeekBar = true;
                        }
                    }
                    if (!updatedBySeekBar) {
                        showPreview(zoom_temp, picture_degree, picture_alpha, position_x, position_y, touch_and_move, allow_picture_over_layout, false, touch_and_move);
                    }
                } else {
                    ApplicationMethods.showToast(requireContext(), R.string.settings_picture_resize_warn);
                }
            } catch (NumberFormatException ignored) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            if (inputValue != null && inputValue > 0f && (allow_picture_over_layout || inputValue <= maxSize)) {
                zoom = inputValue;
            } else {
                zoom = zoom_temp;
            }
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> showWorkingWindowPreview(picture_alpha));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> showWorkingWindowPreview(picture_alpha));
    }

    private void fitPictureToScreenHeight() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }
        float fittedZoom = resolveScreenHeightZoom(bitmap, picture_degree);
        if (fittedZoom <= 0f) {
            return;
        }
        Point currentPosition = touch_and_move ? getPreviewPosition(position_x, position_y) : new Point(position_x, position_y);
        zoom = fittedZoom;
        position_x = currentPosition.x;
        position_y = 0;
        showPreview(
                zoom,
                picture_degree,
                picture_alpha,
                position_x,
                position_y,
                touch_and_move,
                allow_picture_over_layout,
                false,
                false
        );
    }

    private void setPictureDegree() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }

        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_degree);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.degree);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(360 * THREE_DECIMAL_SCALE);
        seekBar.setProgress(Math.min(seekBar.getMax(), toThreeDecimalProgress(picture_degree)));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(picture_degree));
        picture_degree_temp = roundToThreeDecimals(picture_degree);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                picture_degree_temp = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                editText.setText(formatThreeDecimal(picture_degree_temp));
                showPreview(
                        zoom,
                        picture_degree_temp,
                        picture_alpha,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        fromUser ? OverlayRuntimeController.PREVIEW_MODE_LOW_RES : OverlayRuntimeController.PREVIEW_MODE_FULL,
                        false,
                        touch_and_move
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                showPreview(
                        zoom,
                        picture_degree_temp,
                        picture_alpha,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        OverlayRuntimeController.PREVIEW_MODE_FULL,
                        false,
                        touch_and_move
                );
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float edittext_temp = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (edittext_temp >= 0 && edittext_temp <= 360) {
                    picture_degree_temp = edittext_temp;
                    editText.setText(formatThreeDecimal(picture_degree_temp));
                    int progress = Math.min(seekBar.getMax(), toThreeDecimalProgress(picture_degree_temp));
                    if (seekBar.getProgress() != progress) {
                        seekBar.setProgress(progress);
                    } else {
                        showPreview(zoom, picture_degree_temp, picture_alpha, position_x, position_y, touch_and_move, allow_picture_over_layout, false, touch_and_move);
                    }
                } else {
                    ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
                }
            } catch (NumberFormatException ignored) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            if (inputValue != null && inputValue >= 0f && inputValue <= 360f) {
                picture_degree = inputValue;
            } else {
                picture_degree = picture_degree_temp;
            }
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> showWorkingWindowPreview(picture_alpha));
        dialog.setView(mView);
        AlertDialog alertDialogDegree = dialog.show();
        alertDialogDegree.setOnDismissListener(d -> showWorkingWindowPreview(picture_alpha));
    }

    // 方法名改为 showPictureAlphaDialog 避免与 FloatImageView.setPictureAlpha(float) 重名
    private void showPictureAlphaDialog() {
        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_alpha);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.transparency);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(THREE_DECIMAL_SCALE);
        seekBar.setProgress(Math.min(seekBar.getMax(), toThreeDecimalProgress(picture_alpha)));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(picture_alpha));
        picture_alpha_temp = roundToThreeDecimals(picture_alpha);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                picture_alpha_temp = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                editText.setText(formatThreeDecimal(picture_alpha_temp));
                showWorkingWindowPreview(picture_alpha_temp);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float edittext_temp = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (edittext_temp >= 0 && edittext_temp <= 1) {
                    picture_alpha_temp = edittext_temp;
                    editText.setText(formatThreeDecimal(picture_alpha_temp));
                    int progress = Math.min(seekBar.getMax(), toThreeDecimalProgress(picture_alpha_temp));
                    if (seekBar.getProgress() != progress) {
                        seekBar.setProgress(progress);
                    } else {
                        showWorkingWindowPreview(picture_alpha_temp);
                    }
                } else {
                    ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
                }
            } catch (NumberFormatException ignored) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            if (inputValue != null && inputValue >= 0f && inputValue <= 1f) {
                picture_alpha = inputValue;
            } else {
                picture_alpha = picture_alpha_temp;
            }
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> {
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setView(mView);
        dialog.show();
    }

    private void setPictureCornerRadius() {
        View mView = inflater.inflate(
                R.layout.dialog_set_appearance_options,
                requireActivity().findViewById(R.id.layout_dialog_set_appearance_options)
        );
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_corner_radius);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_picture_corner_radius_value);
        TextView positionsLabel = mView.findViewById(R.id.textview_set_positions);
        positionsLabel.setText(R.string.settings_picture_corner_radius_positions);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(MAX_CORNER_RADIUS_PERCENT);
        seekBar.setProgress(toRatioPercentProgress(picture_corner_radius_ratio));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        enableIntegerInput(editText);
        editText.setText(String.valueOf(toRatioPercentProgress(picture_corner_radius_ratio)));
        final CheckBox[] optionCheckBoxes = getAppearanceOptionCheckBoxes(mView);
        final int[] optionBits = new int[]{
                Config.MASK_CORNER_TOP_LEFT,
                Config.MASK_CORNER_TOP_RIGHT,
                Config.MASK_CORNER_BOTTOM_LEFT,
                Config.MASK_CORNER_BOTTOM_RIGHT
        };
        bindAppearanceOptionCheckBoxes(
                optionCheckBoxes,
                new int[]{
                        R.string.position_top_left,
                        R.string.position_top_right,
                        R.string.position_bottom_left,
                        R.string.position_bottom_right
                },
                optionBits,
                picture_corner_radius_mask
        );
        picture_corner_radius_ratio_temp = picture_corner_radius_ratio;
        picture_corner_radius_mask_temp = picture_corner_radius_mask;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                picture_corner_radius_ratio_temp = percentProgressToRatio(progress);
                editText.setText(String.valueOf(progress));
                picture_corner_radius_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio_temp,
                        picture_corner_radius_mask_temp,
                        picture_edge_feather_ratio,
                        picture_edge_feather_mask,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        false,
                        touch_and_move
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            Integer progress = parsePercentProgress(editText);
            if (progress != null && progress >= 0 && progress <= MAX_CORNER_RADIUS_PERCENT) {
                picture_corner_radius_ratio_temp = percentProgressToRatio(progress);
                picture_corner_radius_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                editText.setText(String.valueOf(progress));
                if (seekBar.getProgress() != progress) {
                    seekBar.setProgress(progress);
                } else {
                    showPreview(
                            zoom,
                            picture_degree,
                            picture_alpha,
                            picture_corner_radius_ratio_temp,
                            picture_corner_radius_mask_temp,
                            picture_edge_feather_ratio,
                            picture_edge_feather_mask,
                            position_x,
                            position_y,
                            touch_and_move,
                            allow_picture_over_layout,
                            false,
                            touch_and_move
                    );
                }
            } else {
                ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
            }
            return false;
        });
        for (CheckBox optionCheckBox : optionCheckBoxes) {
            optionCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                picture_corner_radius_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio_temp,
                        picture_corner_radius_mask_temp,
                        picture_edge_feather_ratio,
                        picture_edge_feather_mask,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        false,
                        touch_and_move
                );
            });
        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Integer progress = parsePercentProgress(editText);
            if (progress != null && progress >= 0 && progress <= MAX_CORNER_RADIUS_PERCENT) {
                picture_corner_radius_ratio = percentProgressToRatio(progress);
            } else {
                picture_corner_radius_ratio = picture_corner_radius_ratio_temp;
            }
            picture_corner_radius_mask = resolveCheckedMask(optionCheckBoxes, optionBits);
            updateAppearancePreferenceSummaries();
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> showWorkingWindowPreview(picture_alpha));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> showWorkingWindowPreview(picture_alpha));
    }

    private void setPictureEdgeFeather() {
        View mView = inflater.inflate(
                R.layout.dialog_set_appearance_options,
                requireActivity().findViewById(R.id.layout_dialog_set_appearance_options)
        );
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_edge_feather);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_picture_edge_feather_value);
        TextView positionsLabel = mView.findViewById(R.id.textview_set_positions);
        positionsLabel.setText(R.string.settings_picture_edge_feather_edges);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(MAX_EDGE_FEATHER_PERCENT);
        seekBar.setProgress(toRatioPercentProgress(picture_edge_feather_ratio));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        enableIntegerInput(editText);
        editText.setText(String.valueOf(toRatioPercentProgress(picture_edge_feather_ratio)));
        final CheckBox[] optionCheckBoxes = getAppearanceOptionCheckBoxes(mView);
        final int[] optionBits = new int[]{
                Config.MASK_EDGE_TOP,
                Config.MASK_EDGE_BOTTOM,
                Config.MASK_EDGE_LEFT,
                Config.MASK_EDGE_RIGHT
        };
        bindAppearanceOptionCheckBoxes(
                optionCheckBoxes,
                new int[]{
                        R.string.position_top,
                        R.string.position_bottom,
                        R.string.position_left,
                        R.string.position_right
                },
                optionBits,
                picture_edge_feather_mask
        );
        picture_edge_feather_ratio_temp = picture_edge_feather_ratio;
        picture_edge_feather_mask_temp = picture_edge_feather_mask;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                picture_edge_feather_ratio_temp = percentProgressToRatio(progress);
                editText.setText(String.valueOf(progress));
                picture_edge_feather_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio,
                        picture_corner_radius_mask,
                        picture_edge_feather_ratio_temp,
                        picture_edge_feather_mask_temp,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        false,
                        touch_and_move
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            Integer progress = parsePercentProgress(editText);
            if (progress != null && progress >= 0 && progress <= MAX_EDGE_FEATHER_PERCENT) {
                picture_edge_feather_ratio_temp = percentProgressToRatio(progress);
                picture_edge_feather_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                editText.setText(String.valueOf(progress));
                if (seekBar.getProgress() != progress) {
                    seekBar.setProgress(progress);
                } else {
                    showPreview(
                            zoom,
                            picture_degree,
                            picture_alpha,
                            picture_corner_radius_ratio,
                            picture_corner_radius_mask,
                            picture_edge_feather_ratio_temp,
                            picture_edge_feather_mask_temp,
                            position_x,
                            position_y,
                            touch_and_move,
                            allow_picture_over_layout,
                            false,
                            touch_and_move
                    );
                }
            } else {
                ApplicationMethods.showToast(requireContext(), R.string.settings_number_warn);
            }
            return false;
        });
        for (CheckBox optionCheckBox : optionCheckBoxes) {
            optionCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                picture_edge_feather_mask_temp = resolveCheckedMask(optionCheckBoxes, optionBits);
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio,
                        picture_corner_radius_mask,
                        picture_edge_feather_ratio_temp,
                        picture_edge_feather_mask_temp,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        false,
                        touch_and_move
                );
            });
        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Integer progress = parsePercentProgress(editText);
            if (progress != null && progress >= 0 && progress <= MAX_EDGE_FEATHER_PERCENT) {
                picture_edge_feather_ratio = percentProgressToRatio(progress);
            } else {
                picture_edge_feather_ratio = picture_edge_feather_ratio_temp;
            }
            picture_edge_feather_mask = resolveCheckedMask(optionCheckBoxes, optionBits);
            updateAppearancePreferenceSummaries();
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> showWorkingWindowPreview(picture_alpha));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> showWorkingWindowPreview(picture_alpha));
    }

    private void setPicturePosition() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean touchable_edit = (touch_and_move || sharedPreferences.getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false));
        showPreview(
                zoom,
                picture_degree,
                picture_alpha,
                position_x,
                position_y,
                touchable_edit,
                allow_picture_over_layout,
                OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                false,
                false
        );

        View mView = inflater.inflate(R.layout.dialog_set_position, requireActivity().findViewById(R.id.layout_dialog_set_position));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_position);
        dialog.setCancelable(false);
        Point size = getWindowSize();
        final int Max_X = size.x;
        final int Max_Y = size.y;
        final SeekBar seekBar_x = mView.findViewById(R.id.seekbar_set_position_x);
        if (!allow_picture_over_layout) {
            seekBar_x.setMax(Max_X);
            seekBar_x.setProgress(position_x);
        }
        final EditText editText_x = mView.findViewById(R.id.edittext_set_position_x);
        editText_x.setText(String.valueOf(position_x));
        final SeekBar seekBar_y = mView.findViewById(R.id.seekbar_set_position_y);
        if (!allow_picture_over_layout) {
            seekBar_y.setMax(Max_Y);
            seekBar_y.setProgress(position_y);
        }
        final EditText editText_y = mView.findViewById(R.id.edittext_set_position_y);
        editText_y.setText(String.valueOf(position_y));
        if (allow_picture_over_layout) {
            editText_x.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            editText_y.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        position_x_temp = position_x;
        position_y_temp = position_y;
        seekBar_x.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                position_x_temp = progress;
                editText_x.setText(String.valueOf(progress));
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        position_x_temp,
                        position_y_temp,
                        touchable_edit,
                        allow_picture_over_layout,
                        OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                        false,
                        false
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText_x.setOnEditorActionListener((v, actionId, event) -> {
            try {
                int edittext_temp = Integer.parseInt(v.getText().toString());
                if (allow_picture_over_layout || (edittext_temp >= 0 && edittext_temp <= Max_X)) {
                    position_x_temp = edittext_temp;
                    if (!allow_picture_over_layout) {
                        seekBar_x.setProgress(edittext_temp);
                    }
                    showPreview(
                            zoom,
                            picture_degree,
                            picture_alpha,
                            position_x_temp,
                            position_y_temp,
                            touchable_edit,
                            allow_picture_over_layout,
                            OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                            false,
                            false
                    );
                } else {
                    ApplicationMethods.showToast(requireContext(), R.string.settings_picture_position_warn);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        });
        seekBar_y.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                position_y_temp = progress;
                editText_y.setText(String.valueOf(progress));
                showPreview(
                        zoom,
                        picture_degree,
                        picture_alpha,
                        position_x_temp,
                        position_y_temp,
                        touchable_edit,
                        allow_picture_over_layout,
                        OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                        false,
                        false
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText_y.setOnEditorActionListener((v, actionId, event) -> {
            try {
                int edittext_temp = Integer.parseInt(v.getText().toString());
                if (allow_picture_over_layout || (edittext_temp >= 0 && edittext_temp <= Max_Y)) {
                    position_y_temp = edittext_temp;
                    if (!allow_picture_over_layout) {
                        seekBar_y.setProgress(edittext_temp);
                    }
                    showPreview(
                            zoom,
                            picture_degree,
                            picture_alpha,
                            position_x_temp,
                            position_y_temp,
                            touchable_edit,
                            allow_picture_over_layout,
                            OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                            false,
                            false
                    );
                } else {
                    ApplicationMethods.showToast(requireContext(), R.string.settings_picture_position_warn);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        });
        if (allow_picture_over_layout) {
            seekBar_x.setEnabled(false);
            seekBar_y.setEnabled(false);
        }
        if (touchable_edit) {
            dialog.setNeutralButton(R.string.save_moved_position, (dialog1, which) -> {
                Point previewPosition = getPreviewPosition(position_x_temp, position_y_temp);
                position_x = previewPosition.x;
                position_y = previewPosition.y;
                showWorkingWindowPreview(picture_alpha);
            });
        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            if (allow_picture_over_layout) {
                try {
                    position_x = Integer.parseInt(editText_x.getText().toString());
                    position_y = Integer.parseInt(editText_y.getText().toString());
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    position_x = position_x_temp;
                    position_y = position_y_temp;
                }
            } else {
                position_x = position_x_temp;
                position_y = position_y_temp;
            }
            showWorkingWindowPreview(picture_alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> showWorkingWindowPreview(picture_alpha));
        dialog.setView(mView);
        AlertDialog alertDialogPosition = dialog.show();
        alertDialogPosition.setOnDismissListener(d -> showWorkingWindowPreview(picture_alpha));
    }

    private Point getWindowSize() {
        Point size = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds = requireActivity().getWindowManager().getMaximumWindowMetrics().getBounds();
            size.x = bounds.width();
            size.y = bounds.height();
        } else {
            size.x = requireContext().getResources().getDisplayMetrics().widthPixels;
            size.y = requireContext().getResources().getDisplayMetrics().heightPixels;
        }
        return size;
    }

    private float resolveScreenHeightZoom(@NonNull Bitmap sourceBitmap, float degreeValue) {
        Point windowSize = getWindowSize();
        if (windowSize.y <= 0) {
            return 0f;
        }
        double radians = Math.toRadians(degreeValue);
        double rotatedBaseHeight = (Math.abs(sourceBitmap.getHeight() * Math.cos(radians))
                + Math.abs(sourceBitmap.getWidth() * Math.sin(radians)));
        if (rotatedBaseHeight <= 0d) {
            return 0f;
        }
        return Math.max(roundToThreeDecimals((float) (windowSize.y / rotatedBaseHeight)), 0.01f);
    }

    private boolean removeViewIfAttached(FloatImageView imageView) {
        return WindowsMethods.removeWindowIfAttached(imageView);
    }

    private boolean shouldAbortFragmentWork() {
        if (fragmentClosing || !isAdded()) {
            return true;
        }
        if (getActivity() == null) {
            return true;
        }
        return requireActivity().isFinishing() || requireActivity().isDestroyed();
    }

    private void dismissDialogIfShowing(AlertDialog alertDialog) {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    private void registerWorkingFloatImageView() {
        if (floatImageView == null || PictureId == null) {
            return;
        }
        Context context = getContext();
        if (context != null) {
            ImageMethods.saveFloatImageViewById(context, PictureId, floatImageView);
        }
    }

    private void enableDecimalInput(EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    private void enableIntegerInput(EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
    }

    private float roundToThreeDecimals(float value) {
        return Math.round(value * THREE_DECIMAL_SCALE) / (float) THREE_DECIMAL_SCALE;
    }

    private int toThreeDecimalProgress(float value) {
        return Math.round(roundToThreeDecimals(value) * THREE_DECIMAL_SCALE);
    }

    private int toRatioPercentProgress(float ratio) {
        return Math.round(clampRatio(ratio) * PERCENT_SCALE);
    }

    private String formatThreeDecimal(float value) {
        return String.format(Locale.US, "%.3f", roundToThreeDecimals(value));
    }

    @Nullable
    private Float parseThreeDecimalFloat(EditText editText) {
        try {
            return roundToThreeDecimals(Float.parseFloat(editText.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Integer parsePercentProgress(EditText editText) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private float percentProgressToRatio(int progress) {
        return clampRatio(progress / (float) PERCENT_SCALE);
    }

    private float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private String formatRatioPercent(float ratio) {
        return toRatioPercentProgress(ratio) + "%";
    }

    private void updateAppearancePreferenceSummaries() {
        Preference cornerRadiusPreference = findPreference(Config.PREFERENCE_PICTURE_CORNER_RADIUS);
        if (cornerRadiusPreference != null) {
            cornerRadiusPreference.setSummary(getString(
                    R.string.appearance_summary_format,
                    formatRatioPercent(picture_corner_radius_ratio),
                    formatCornerMaskSummary(picture_corner_radius_mask)
            ));
        }
        Preference edgeFeatherPreference = findPreference(Config.PREFERENCE_PICTURE_EDGE_FEATHER);
        if (edgeFeatherPreference != null) {
            edgeFeatherPreference.setSummary(getString(
                    R.string.appearance_summary_format,
                    formatRatioPercent(picture_edge_feather_ratio),
                    formatEdgeMaskSummary(picture_edge_feather_mask)
            ));
        }
    }

    private String formatCornerMaskSummary(int mask) {
        return formatSelectionSummary(
                mask,
                new int[]{
                        Config.MASK_CORNER_TOP_LEFT,
                        Config.MASK_CORNER_TOP_RIGHT,
                        Config.MASK_CORNER_BOTTOM_LEFT,
                        Config.MASK_CORNER_BOTTOM_RIGHT
                },
                new int[]{
                        R.string.position_top_left,
                        R.string.position_top_right,
                        R.string.position_bottom_left,
                        R.string.position_bottom_right
                }
        );
    }

    private String formatEdgeMaskSummary(int mask) {
        return formatSelectionSummary(
                mask,
                new int[]{
                        Config.MASK_EDGE_TOP,
                        Config.MASK_EDGE_BOTTOM,
                        Config.MASK_EDGE_LEFT,
                        Config.MASK_EDGE_RIGHT
                },
                new int[]{
                        R.string.position_top,
                        R.string.position_bottom,
                        R.string.position_left,
                        R.string.position_right
                }
        );
    }

    private String formatSelectionSummary(int mask, int[] optionBits, int[] labelResIds) {
        int fullMask = 0;
        for (int optionBit : optionBits) {
            fullMask |= optionBit;
        }
        if ((mask & fullMask) == 0) {
            return getString(R.string.selection_none);
        }
        if ((mask & fullMask) == fullMask) {
            return getString(R.string.selection_all);
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (int index = 0; index < optionBits.length; index++) {
            if ((mask & optionBits[index]) == 0) {
                continue;
            }
            if (summaryBuilder.length() > 0) {
                summaryBuilder.append(' ');
            }
            summaryBuilder.append(getString(labelResIds[index]));
        }
        return summaryBuilder.toString();
    }

    private CheckBox[] getAppearanceOptionCheckBoxes(View mView) {
        return new CheckBox[]{
                mView.findViewById(R.id.checkbox_option_1),
                mView.findViewById(R.id.checkbox_option_2),
                mView.findViewById(R.id.checkbox_option_3),
                mView.findViewById(R.id.checkbox_option_4)
        };
    }

    private void bindAppearanceOptionCheckBoxes(CheckBox[] checkBoxes, int[] labelResIds, int[] optionBits, int mask) {
        for (int index = 0; index < checkBoxes.length; index++) {
            checkBoxes[index].setText(labelResIds[index]);
            checkBoxes[index].setChecked((mask & optionBits[index]) != 0);
        }
    }

    private int resolveCheckedMask(CheckBox[] checkBoxes, int[] optionBits) {
        int mask = 0;
        for (int index = 0; index < checkBoxes.length; index++) {
            if (checkBoxes[index].isChecked()) {
                mask |= optionBits[index];
            }
        }
        return mask;
    }

    private void showWorkingWindowPreview(float alpha) {
        showWorkingWindowPreview(alpha, false);
    }

    private void showWorkingWindowPreview(float alpha, boolean reloadSource) {
        Point previewPosition = touch_and_move ? getPreviewPosition(position_x, position_y) : new Point(position_x, position_y);
        showPreview(
                zoom,
                picture_degree,
                alpha,
                picture_corner_radius_ratio,
                picture_corner_radius_mask,
                picture_edge_feather_ratio,
                picture_edge_feather_mask,
                previewPosition.x,
                previewPosition.y,
                touch_and_move,
                allow_picture_over_layout,
                OverlayRuntimeController.PREVIEW_MODE_FULL,
                reloadSource,
                false
        );
    }

    private void showPreview(float zoomValue,
                             float degreeValue,
                             float alphaValue,
                             int positionX,
                             int positionY,
                             boolean touchAndMove,
                             boolean overLayout,
                             boolean reloadSource,
                             boolean useRuntimePosition) {
        showPreview(
                zoomValue,
                degreeValue,
                alphaValue,
                picture_corner_radius_ratio,
                picture_corner_radius_mask,
                picture_edge_feather_ratio,
                picture_edge_feather_mask,
                positionX,
                positionY,
                touchAndMove,
                overLayout,
                OverlayRuntimeController.PREVIEW_MODE_FULL,
                reloadSource,
                useRuntimePosition
        );
    }

    private void showPreview(float zoomValue,
                             float degreeValue,
                             float alphaValue,
                             int positionX,
                             int positionY,
                             boolean touchAndMove,
                             boolean overLayout,
                             int previewMode,
                             boolean reloadSource,
                             boolean useRuntimePosition) {
        showPreview(
                zoomValue,
                degreeValue,
                alphaValue,
                picture_corner_radius_ratio,
                picture_corner_radius_mask,
                picture_edge_feather_ratio,
                picture_edge_feather_mask,
                positionX,
                positionY,
                touchAndMove,
                overLayout,
                previewMode,
                reloadSource,
                useRuntimePosition
        );
    }

    private void showPreview(float zoomValue,
                             float degreeValue,
                             float alphaValue,
                             float cornerRadiusRatio,
                             int cornerRadiusMask,
                             float edgeFeatherRatio,
                             int edgeFeatherMask,
                             int positionX,
                             int positionY,
                             boolean touchAndMove,
                             boolean overLayout,
                             boolean reloadSource,
                             boolean useRuntimePosition) {
        showPreview(
                zoomValue,
                degreeValue,
                alphaValue,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask,
                positionX,
                positionY,
                touchAndMove,
                overLayout,
                OverlayRuntimeController.PREVIEW_MODE_FULL,
                reloadSource,
                useRuntimePosition
        );
    }

    private void showPreview(float zoomValue,
                             float degreeValue,
                             float alphaValue,
                             float cornerRadiusRatio,
                             int cornerRadiusMask,
                             float edgeFeatherRatio,
                             int edgeFeatherMask,
                             int positionX,
                             int positionY,
                             boolean touchAndMove,
                             boolean overLayout,
                             int previewMode,
                             boolean reloadSource,
                             boolean useRuntimePosition) {
        if (PictureId == null || shouldAbortFragmentWork()) {
            return;
        }
        int resolvedPositionX = positionX;
        int resolvedPositionY = positionY;
        if (useRuntimePosition) {
            Point runtimePosition = getPreviewPosition(positionX, positionY);
            resolvedPositionX = runtimePosition.x;
            resolvedPositionY = runtimePosition.y;
        }
        OverlayRuntimeController.updatePreview(
                requireContext(),
                PictureId,
                zoomValue,
                degreeValue,
                alphaValue,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask,
                resolvedPositionX,
                resolvedPositionY,
                touchAndMove,
                overLayout,
                previewMode,
                reloadSource
        );
    }

    private Point getPreviewPosition(int fallbackX, int fallbackY) {
        if (PictureId == null) {
            return new Point(fallbackX, fallbackY);
        }
        return OverlayRuntimeStateStore.getWindowPosition(requireContext(), PictureId, fallbackX, fallbackY);
    }

    public void saveAllData(@Nullable Runnable onComplete, @Nullable Runnable onFailed) {
        // 若编辑前窗口是隐藏的，保存后仍保持隐藏（用户只修改设置，不改变显示状态）
        pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, !wasHidden);
        pictureData.put(Config.DATA_PICTURE_ZOOM, zoom);
        pictureData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, default_zoom);
        pictureData.put(Config.DATA_PICTURE_ALPHA, picture_alpha);
        pictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, picture_corner_radius_ratio);
        pictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_MASK, picture_corner_radius_mask);
        pictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, picture_edge_feather_ratio);
        pictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_MASK, picture_edge_feather_mask);
        if (touch_and_move) {
            Point previewPosition = getPreviewPosition(position_x, position_y);
            position_x = previewPosition.x;
            position_y = previewPosition.y;
        }
        pictureData.put(Config.DATA_PICTURE_POSITION_X, position_x);
        pictureData.put(Config.DATA_PICTURE_POSITION_Y, position_y);
        pictureData.put(Config.DATA_PICTURE_DEGREE, picture_degree);
        pictureData.put(Config.DATA_PICTURE_TOUCH_AND_MOVE, touch_and_move);
        pictureData.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, allow_picture_over_layout);
        // 快照不可变值供后台线程使用，避免主线程字段被并发读
        final String snapshotPictureId = PictureId;
        final String snapshotPictureName = PictureName;
        final float snapshotZoom = zoom;
        final float snapshotDegree = picture_degree;
        final float snapshotAlpha = picture_alpha;
        final float snapshotCornerRadiusRatio = picture_corner_radius_ratio;
        final int snapshotCornerRadiusMask = picture_corner_radius_mask;
        final float snapshotEdgeFeatherRatio = picture_edge_feather_ratio;
        final int snapshotEdgeFeatherMask = picture_edge_feather_mask;
        final int snapshotX = position_x;
        final int snapshotY = position_y;
        final boolean snapshotTouchAndMove = touch_and_move;
        final boolean snapshotOverLayout = allow_picture_over_layout;
        final boolean snapshotWasHidden = wasHidden;
        final Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            if (!ImageMethods.commitPendingReplacementImage(snapshotPictureId)) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    ApplicationMethods.showToast(requireContext(), R.string.picture_settings_save_failed);
                    if (onFailed != null) {
                        onFailed.run();
                    }
                });
                return;
            }
            // JSON 序列化写磁盘（阻塞 IO）
            pictureData.commit(snapshotPictureName);
            // Bitmap 缩放 + 显示缓存压缩写磁盘（CPU + IO 密集）
            ImageMethods.createAndSaveDisplayBitmap(
                    snapshotPictureId,
                    snapshotZoom,
                    snapshotDegree,
                    snapshotCornerRadiusRatio,
                    snapshotCornerRadiusMask,
                    snapshotEdgeFeatherRatio,
                    snapshotEdgeFeatherMask
            );
            OverlayRuntimeController.finishPreview(appContext, snapshotPictureId);
            if (shouldAbortFragmentWork()) {
                releaseSourceBitmap();
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (shouldAbortFragmentWork()) {
                    releaseSourceBitmap();
                    return;
                }
                touch_and_move = snapshotTouchAndMove;
                allow_picture_over_layout = snapshotOverLayout;
                picture_alpha = snapshotAlpha;
                picture_corner_radius_ratio = snapshotCornerRadiusRatio;
                picture_corner_radius_mask = snapshotCornerRadiusMask;
                picture_edge_feather_ratio = snapshotEdgeFeatherRatio;
                picture_edge_feather_mask = snapshotEdgeFeatherMask;
                position_x = snapshotX;
                position_y = snapshotY;
                wasHidden = snapshotWasHidden;
                releaseSourceBitmap();
                changesSaved = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).start();
    }

    public void clearEditView() {
        if (changesSaved || PictureId == null) {
            return;
        }
        if (Edit_Mode) {
            OverlayRuntimeController.cancelPreview(requireContext(), PictureId);
        } else {
            OverlayRuntimeController.deletePicture(requireContext(), PictureId);
        }
    }

    public void exit() {
        fragmentClosing = true;
        if (!Edit_Mode) {
            releaseSourceBitmap();
            OverlayRuntimeController.deletePicture(requireActivity(), PictureId);
            ImageMethods.clearAllTemp(requireActivity(), PictureId);
        } else {
            ImageMethods.clearStagedReplacementImage(PictureId);
            ImageMethods.clearPendingReplacementImage(PictureId);
            releaseSourceBitmap();
            OverlayRuntimeController.cancelPreview(requireContext(), PictureId);
        }
    }

}
