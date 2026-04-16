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
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class PictureSettingsFragment extends PreferenceFragmentCompat {
    private final static String WINDOW_CREATED = "WINDOW_CREATED";
    private static final int THREE_DECIMAL_SCALE = 1000;
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
            windowManager = WindowsMethods.getWindowManager(requireActivity());
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
                    touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
                    allow_picture_over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
                    bitmap = loadCurrentSourceBitmap();
                    if (bitmap == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
                    floatImageView = ImageMethods.getFloatImageViewById(requireContext(), PictureId);
                    if (floatImageView == null) {
                        Bitmap displayBitmap = ImageMethods.hasPendingReplacementImage(PictureId)
                                ? ImageMethods.createAndSaveDisplayBitmap(PictureId, bitmap, zoom, picture_degree)
                                : ImageMethods.getDisplayBitmap(requireContext(), PictureId, zoom, picture_degree);
                        floatImageView = ImageMethods.createPictureView(requireContext(), displayBitmap, touch_and_move, allow_picture_over_layout, picture_alpha);
                        registerWorkingFloatImageView();
                    }
                    floatImageView.setPictureAlpha(picture_alpha);
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
                    touch_and_move = Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE;
                    allow_picture_over_layout = Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT;
                    bitmap = ImageMethods.getEditSourceBitmap(requireContext(), PictureId);
                    if (bitmap == null) {
                        finishWithError(alertDialog);
                        return;
                    }
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    zoom = default_zoom;
                    Bitmap displayBitmap = ImageMethods.createAndSaveDisplayBitmap(PictureId, zoom, picture_degree);
                    floatImageView = ImageMethods.createPictureView(requireContext(), displayBitmap, touch_and_move, allow_picture_over_layout, picture_alpha);
                    registerWorkingFloatImageView();
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
                    if (!Edit_Mode && floatImageView != null) {
                        // 新增模式：直接创建并显示悬浮窗
                        WindowsMethods.createWindow(windowManager, floatImageView, touch_and_move, allow_picture_over_layout, picture_alpha, position_x, position_y);
                    } else if (Edit_Mode && floatImageView != null) {
                        showWorkingWindowPreview(picture_alpha);
                    }
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
        requirePreference(Config.PREFERENCE_PICTURE_DEGREE).setOnPreferenceClickListener(preference -> {
            setPictureDegree();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_ALPHA).setOnPreferenceClickListener(preference -> {
            showPictureAlphaDialog();
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
            Toast.makeText(requireContext(), R.string.picture_settings_open_failed, Toast.LENGTH_SHORT).show();
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
        floatImageView.setOverLayout(allow_picture_over_layout);
        showWorkingWindowPreview(picture_alpha);
    }

    private void setPictureTouchAndMove(boolean touchable_and_moveable) {
        touch_and_move = touchable_and_moveable;
        floatImageView.setMoveable(touchable_and_moveable);
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
            Toast.makeText(requireContext(), R.string.picture_settings_open_failed, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), R.string.settings_picture_name_warn, Toast.LENGTH_SHORT).show();
            } else {
                PictureName = editText.getText().toString();
            }
        });
        dialog.setNegativeButton(R.string.cancel, (dialog1, which) -> {
            if (editText.getText().toString().isEmpty()) {
                Toast.makeText(requireContext(), R.string.settings_picture_name_warn, Toast.LENGTH_SHORT).show();
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
            Bitmap displayBitmap = ImageMethods.createAndSaveDisplayBitmap(PictureId, replacementBitmap, zoom, picture_degree);
            if (displayBitmap == null) {
                ImageMethods.recycleBitmap(replacementBitmap);
                notifyReplacePictureFailed(alertDialog);
                return;
            }
            if (!ImageMethods.applyStagedReplacementImage(PictureId)) {
                ImageMethods.recycleBitmap(replacementBitmap);
                ImageMethods.recycleBitmap(displayBitmap);
                notifyReplacePictureFailed(alertDialog);
                return;
            }
            if (shouldAbortFragmentWork()) {
                ImageMethods.recycleBitmap(replacementBitmap);
                ImageMethods.recycleBitmap(displayBitmap);
                requireActivity().runOnUiThread(() -> dismissDialogIfShowing(alertDialog));
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (shouldAbortFragmentWork()) {
                    ImageMethods.recycleBitmap(replacementBitmap);
                    ImageMethods.recycleBitmap(displayBitmap);
                    dismissDialogIfShowing(alertDialog);
                    return;
                }
                dismissDialogIfShowing(alertDialog);
                releaseSourceBitmap();
                bitmap = replacementBitmap;
                default_zoom = replacementDefaultZoom;
                if (floatImageView == null) {
                    floatImageView = ImageMethods.createPictureView(requireContext(), displayBitmap, touch_and_move, allow_picture_over_layout, picture_alpha);
                    registerWorkingFloatImageView();
                } else {
                    ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
                }
                showWorkingWindowPreview(picture_alpha);
                Toast.makeText(requireContext(), R.string.picture_settings_replace_success, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), R.string.picture_settings_replace_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void setPictureSize() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }
        bitmap_Edit = ImageMethods.getEditBitmap(requireActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(requireActivity(), bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom, picture_degree);
        onEditPicture(floatImageView_Edit);

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
                    WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom_temp, picture_degree, position_x, position_y);
                }
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
                        WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom_temp, picture_degree, position_x, position_y);
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.settings_picture_resize_warn, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException ignored) {
                Toast.makeText(requireContext(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
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
            onSuccessEditPicture();
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture());
        dialog.setView(mView);
        // 用户按返回键关闭对话框时，OnDismissListener 保证资源一定被回收
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> {
            if (onUseEditPicture) {
                onFailedEditPicture();
            } else if (floatImageView_Edit != null || bitmap_Edit != null) {
                releaseEditResources();
            }
        });
    }

    private void setPictureDegree() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }
        bitmap_Edit = ImageMethods.getEditBitmap(requireActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(requireActivity(), bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom, picture_degree);
        onEditPicture(floatImageView_Edit);

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
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom, picture_degree_temp, position_x, position_y);
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
                if (edittext_temp >= 0 && edittext_temp <= 360) {
                    picture_degree_temp = edittext_temp;
                    editText.setText(formatThreeDecimal(picture_degree_temp));
                    int progress = Math.min(seekBar.getMax(), toThreeDecimalProgress(picture_degree_temp));
                    if (seekBar.getProgress() != progress) {
                        seekBar.setProgress(progress);
                    } else {
                        WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, zoom, picture_degree_temp, position_x, position_y);
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException ignored) {
                Toast.makeText(requireContext(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
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
            onSuccessEditPicture();
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture());
        dialog.setView(mView);
        // 用户按返回键关闭对话框时，OnDismissListener 保证资源一定被回收
        AlertDialog alertDialogDegree = dialog.show();
        alertDialogDegree.setOnDismissListener(d -> {
            if (onUseEditPicture) {
                onFailedEditPicture();
            } else if (floatImageView_Edit != null || bitmap_Edit != null) {
                releaseEditResources();
            }
        });
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
                    Toast.makeText(requireContext(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException ignored) {
                Toast.makeText(requireContext(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
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

    private void setPicturePosition() {
        if (!ensureSourceBitmapLoaded()) {
            return;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean touchable_edit = (touch_and_move || sharedPreferences.getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false));
        bitmap_Edit = ImageMethods.getEditBitmap(requireActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(requireActivity(), bitmap_Edit, touchable_edit, allow_picture_over_layout, picture_alpha, zoom, picture_degree);
        onEditPicture(floatImageView_Edit);
        if (touchable_edit) {
            WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, true, allow_picture_over_layout, picture_alpha, zoom, picture_degree, position_x, position_y);
        }

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
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touchable_edit, allow_picture_over_layout, picture_alpha, zoom, picture_degree, position_x_temp, position_y_temp);
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
                    WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touchable_edit, allow_picture_over_layout, picture_alpha, zoom, picture_degree, position_x_temp, position_y_temp);
                } else {
                    Toast.makeText(requireContext(), R.string.settings_picture_position_warn, Toast.LENGTH_SHORT).show();
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
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touchable_edit, allow_picture_over_layout, picture_alpha, zoom, picture_degree, position_x_temp, position_y_temp);
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
                    WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, touchable_edit, allow_picture_over_layout, picture_alpha, zoom, picture_degree, position_x_temp, position_y_temp);
                } else {
                    Toast.makeText(requireContext(), R.string.settings_picture_position_warn, Toast.LENGTH_SHORT).show();
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
                position_x = (int) floatImageView_Edit.getMovedPositionX();
                position_y = (int) floatImageView_Edit.getMovedPositionY();
                onSuccessEditPicture();
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
            onSuccessEditPicture();
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture());
        dialog.setView(mView);
        // 用户按返回键关闭对话框时，OnDismissListener 保证资源一定被回收
        AlertDialog alertDialogPosition = dialog.show();
        alertDialogPosition.setOnDismissListener(d -> {
            if (onUseEditPicture) {
                onFailedEditPicture();
            } else if (floatImageView_Edit != null || bitmap_Edit != null) {
                releaseEditResources();
            }
        });
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

    private float roundToThreeDecimals(float value) {
        return Math.round(value * THREE_DECIMAL_SCALE) / (float) THREE_DECIMAL_SCALE;
    }

    private int toThreeDecimalProgress(float value) {
        return Math.round(roundToThreeDecimals(value) * THREE_DECIMAL_SCALE);
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

    private void showWorkingWindowPreview(float alpha) {
        if (floatImageView == null) {
            return;
        }
        floatImageView.setPictureAlpha(alpha);
        floatImageView.setMoveable(touch_and_move);
        floatImageView.setOverLayout(allow_picture_over_layout);
        WindowsMethods.createWindow(windowManager, floatImageView, touch_and_move, allow_picture_over_layout, alpha, position_x, position_y);
    }

    private boolean restoreWorkingWindowVisibility(float alpha) {
        if (!wasHidden) {
            showWorkingWindowPreview(alpha);
            return true;
        }
        if (floatImageView == null) {
            return true;
        }
        floatImageView.setPictureAlpha(alpha);
        floatImageView.setMoveable(touch_and_move);
        floatImageView.setOverLayout(allow_picture_over_layout);
        removeViewIfAttached(floatImageView);
        Context context = getContext();
        if (context != null) {
            WindowsMethods.syncAllWindows(context);
        }
        if (!floatImageView.isAttachedToWindow()) {
            releaseWorkingWindowRegistration();
            return true;
        }
        return false;
    }

    private void releaseWorkingWindowRegistration() {
        if (floatImageView == null || PictureId == null) {
            return;
        }
        if (!removeViewIfAttached(floatImageView) || floatImageView.isAttachedToWindow()) {
            return;
        }
        ImageMethods.releasePictureView(floatImageView);
        MainApplication mainApplication = (MainApplication) MainApplication.getAppContext();
        if (mainApplication != null) {
            mainApplication.unregisterView(PictureId);
        }
    }

    private void onEditPicture(FloatImageView FloatImageView_Edit) {
        if (!onUseEditPicture) {
            removeViewIfAttached(floatImageView);
            floatImageView.refreshDrawableState();
            WindowsMethods.createWindow(windowManager, FloatImageView_Edit, touch_and_move, allow_picture_over_layout, picture_alpha, position_x, position_y);
            onUseEditPicture = true;
        }
    }

    private void onSuccessEditPicture() {
        if (!onUseEditPicture) {
            return;
        }
        Bitmap displayBitmap;
        if (Edit_Mode && PictureId != null && ImageMethods.hasPendingReplacementImage(PictureId)) {
            if (!ensureSourceBitmapLoaded()) {
                releaseEditResources();
                return;
            }
            displayBitmap = ImageMethods.createAndSaveDisplayBitmap(PictureId, bitmap, zoom, picture_degree);
        } else {
            displayBitmap = ImageMethods.createAndSaveDisplayBitmap(PictureId, zoom, picture_degree);
        }
        releaseEditResources(true);
        if (displayBitmap != null) {
            ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
        }
        showWorkingWindowPreview(picture_alpha);
    }

    private void onFailedEditPicture() {
        if (!onUseEditPicture) {
            return;
        }
        releaseEditResources();
        showWorkingWindowPreview(picture_alpha);
    }

    public void saveAllData(@Nullable Runnable onComplete, @Nullable Runnable onFailed) {
        // 若编辑前窗口是隐藏的，保存后仍保持隐藏（用户只修改设置，不改变显示状态）
        pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, !wasHidden);
        pictureData.put(Config.DATA_PICTURE_ZOOM, zoom);
        pictureData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, default_zoom);
        pictureData.put(Config.DATA_PICTURE_ALPHA, picture_alpha);
        if (touch_and_move) {
            position_x = (int) floatImageView.getMovedPositionX();
            position_y = (int) floatImageView.getMovedPositionY();
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
        final int snapshotX = position_x;
        final int snapshotY = position_y;
        final boolean snapshotTouchAndMove = touch_and_move;
        final boolean snapshotOverLayout = allow_picture_over_layout;
        final boolean snapshotWasHidden = wasHidden;
        new Thread(() -> {
            if (!ImageMethods.commitPendingReplacementImage(snapshotPictureId)) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.picture_settings_save_failed, Toast.LENGTH_SHORT).show();
                    if (onFailed != null) {
                        onFailed.run();
                    }
                });
                return;
            }
            // JSON 序列化写磁盘（阻塞 IO）
            pictureData.commit(snapshotPictureName);
            // Bitmap 缩放 + PNG 压缩写磁盘（CPU + IO 密集）
            Bitmap displayBitmap = ImageMethods.createAndSaveDisplayBitmap(snapshotPictureId, snapshotZoom, snapshotDegree);
            if (shouldAbortFragmentWork()) {
                if (displayBitmap != null) {
                    ImageMethods.recycleBitmap(displayBitmap);
                }
                releaseSourceBitmap();
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (shouldAbortFragmentWork()) {
                    if (displayBitmap != null) {
                        ImageMethods.recycleBitmap(displayBitmap);
                    }
                    releaseSourceBitmap();
                    return;
                }
                if (displayBitmap != null) {
                    ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
                }
                touch_and_move = snapshotTouchAndMove;
                allow_picture_over_layout = snapshotOverLayout;
                picture_alpha = snapshotAlpha;
                position_x = snapshotX;
                position_y = snapshotY;
                wasHidden = snapshotWasHidden;
                if (snapshotWasHidden) {
                    if (!restoreWorkingWindowVisibility(snapshotAlpha) && floatImageView.isAttachedToWindow()) {
                        pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                        pictureData.commit(null);
                        wasHidden = false;
                    }
                } else {
                    restoreWorkingWindowVisibility(snapshotAlpha);
                }
                releaseEditResources(true);
                changesSaved = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).start();
    }

    public void clearEditView() {
        if (onUseEditPicture) {
            onFailedEditPicture();
        } else if (floatImageView_Edit != null || bitmap_Edit != null) {
            releaseEditResources();
        }
    }

    public void exit() {
        fragmentClosing = true;
        if (!Edit_Mode) {
            releaseEditResources(true);
            if (floatImageView != null) {
                removeViewIfAttached(floatImageView);
                ImageMethods.releasePictureView(floatImageView);
                floatImageView = null;
            }
            ImageMethods.clearAllTemp(requireActivity(), PictureId);
        } else {
            releaseEditResources();
            float original_zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, zoom);
            float original_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, picture_degree);
            // 恢复磁盘上的 display bitmap（用户可能在子对话框中确认了临时修改）
            Bitmap originalDisplayBitmap = ImageMethods.createAndSaveDisplayBitmap(PictureId, original_zoom, original_degree);
            if (originalDisplayBitmap != null) {
                ImageMethods.setPictureBitmap(floatImageView, originalDisplayBitmap);
            }
            if (wasHidden) {
                if (!restoreWorkingWindowVisibility(picture_alpha) && floatImageView.isAttachedToWindow()) {
                    pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                    pictureData.commit(null);
                    wasHidden = false;
                }
            } else {
                float original_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, picture_alpha);
                int original_position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, position_x);
                int original_position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, position_y);
                boolean original_allow_picture_over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, allow_picture_over_layout);
                boolean original_touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
                floatImageView.setPictureAlpha(original_alpha);
                floatImageView.setOverLayout(original_allow_picture_over_layout);
                floatImageView.setMoveable(original_touch_and_move);
                WindowsMethods.updateWindow(windowManager, floatImageView, original_touch_and_move, original_allow_picture_over_layout, original_alpha, original_position_x, original_position_y);
                // updateWindow 走单窗口路径（alpha 上限 0.8），若存在其他可见窗口则需重新平衡
                WindowsMethods.syncAllWindows(requireContext());
            }
            ImageMethods.clearStagedReplacementImage(PictureId);
            ImageMethods.clearPendingReplacementImage(PictureId);
            releaseSourceBitmap();
        }
    }

}
