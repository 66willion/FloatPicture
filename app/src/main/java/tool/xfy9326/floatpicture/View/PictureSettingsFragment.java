package tool.xfy9326.floatpicture.View;

import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.formatRatioPercent;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.roundToThreeDecimals;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

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
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class PictureSettingsFragment extends PreferenceFragmentCompat {
    private static final int MAX_CORNER_RADIUS_PERCENT = 25;
    private static final int MAX_EDGE_FEATHER_PERCENT = 15;
    private static final float BATCH_MAX_ZOOM = 10f;
    private boolean Batch_Mode;
    private boolean Batch_Import_Mode;
    private boolean Edit_Mode;
    private boolean onUseEditPicture = false;
    private boolean changesSaved = false;
    private LayoutInflater inflater;
    private PictureData pictureData;
    private String PictureId;
    private String PictureName;
    private WindowManager windowManager;
    private PictureSettingsDialogController settingsDialogController;
    private ActivityResultLauncher<String> replacePictureLauncher;
    private volatile FloatImageView floatImageView;
    private Bitmap bitmap;
    private Bitmap bitmap_Edit;
    private FloatImageView floatImageView_Edit;
    private boolean touch_and_move;
    private float default_zoom;
    private float zoom;
    private float picture_degree;
    private float picture_alpha;
    private float picture_corner_radius_ratio;
    private int picture_corner_radius_mask;
    private float picture_edge_feather_ratio;
    private int picture_edge_feather_mask;
    private int position_x;
    private int position_y;
    private boolean allow_picture_over_layout;
    private ArrayList<String> batchPictureIds = new ArrayList<>();
    private boolean batchZoomChanged = false;
    private boolean batchFitScreenHeightChanged = false;
    private boolean batchDegreeChanged = false;
    private boolean batchAlphaChanged = false;
    private boolean batchCornerRadiusChanged = false;
    private boolean batchEdgeFeatherChanged = false;
    private boolean batchPositionChanged = false;
    private boolean positionChanged = false;
    private boolean batchTouchAndMoveChanged = false;
    private boolean batchOverLayoutChanged = false;
    /** 进入编辑时窗口是否处于隐藏状态；编辑完成后恢复该状态 */
    private boolean wasHidden = false;
    private volatile boolean fragmentClosing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = requireActivity().getIntent();
        Batch_Mode = intent != null && intent.getBooleanExtra(Config.INTENT_PICTURE_BATCH_EDIT_MODE, false);
        Batch_Import_Mode = intent != null && intent.getBooleanExtra(Config.INTENT_PICTURE_BATCH_IMPORT_MODE, false);
        Edit_Mode = false;
        changesSaved = false;
        pictureData = new PictureData();
        inflater = LayoutInflater.from(requireActivity());
        windowManager = WindowsMethods.getWindowManager(requireActivity());
        settingsDialogController = new PictureSettingsDialogController(
                this,
                inflater,
                new PictureSettingsDialogController.Callbacks() {
                    @Override
                    public void showPreview(float zoom,
                                            float degree,
                                            float alpha,
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
                        PictureSettingsFragment.this.showPreview(
                                zoom,
                                degree,
                                alpha,
                                cornerRadiusRatio,
                                cornerRadiusMask,
                                edgeFeatherRatio,
                                edgeFeatherMask,
                                positionX,
                                positionY,
                                touchAndMove,
                                overLayout,
                                previewMode,
                                reloadSource,
                                useRuntimePosition
                        );
                    }

                    @Override
                    public void showWorkingWindowPreview(float alpha) {
                        PictureSettingsFragment.this.showWorkingWindowPreview(alpha);
                    }

                    @NonNull
                    @Override
                    public Point getPreviewPosition(int fallbackX, int fallbackY) {
                        return PictureSettingsFragment.this.getPreviewPosition(fallbackX, fallbackY);
                    }
                }
        );
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
        RecyclerView recyclerView = getListView();
        recyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        recyclerView.setClipToPadding(false);
        int horizontalPadding = Math.round(8 * getResources().getDisplayMetrics().density);
        int verticalPadding = Math.round(12 * getResources().getDisplayMetrics().density);
        recyclerView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        recyclerView.setItemAnimator(null);
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

    private void setMode() {
        Intent intent = Objects.requireNonNull(requireActivity().getIntent());
        Batch_Mode = intent.getBooleanExtra(Config.INTENT_PICTURE_BATCH_EDIT_MODE, false);
        Batch_Import_Mode = intent.getBooleanExtra(Config.INTENT_PICTURE_BATCH_IMPORT_MODE, false);
        if (Batch_Mode) {
            initializeBatchMode(intent);
            return;
        }
        Edit_Mode = intent.getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false);
        wasHidden = intent.getBooleanExtra(Config.INTENT_PICTURE_WAS_HIDDEN, false);
        updatePicturePreferenceVisibility();
        AlertDialog.Builder loading = new AlertDialog.Builder(requireActivity());
        loading.setCancelable(false);
        View mView = inflater.inflate(R.layout.dialog_loading, requireActivity().findViewById(R.id.layout_dialog_loading));
        loading.setView(mView);
        final AlertDialog alertDialog = loading.show();
        final Context appContext = requireContext().getApplicationContext();
        final boolean editMode = Edit_Mode;
        final String newPictureName = getString(R.string.new_picture_name);
        AppExecutors.io().execute(() -> {
                PictureSettingsLoader.LoadedSettings loadedSettings =
                        PictureSettingsLoader.loadSingle(appContext, intent, pictureData, editMode, newPictureName);
                if (loadedSettings == null) {
                    finishWithError(alertDialog);
                    return;
                }
                if (shouldAbortFragmentWork()) {
                    discardLoadedSettings(appContext, loadedSettings);
                    dismissDialogIfShowing(alertDialog);
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (shouldAbortFragmentWork()) {
                        discardLoadedSettings(appContext, loadedSettings);
                        dismissDialogIfShowing(alertDialog);
                        return;
                    }
                    applyLoadedSettings(loadedSettings);
                    bindPreferenceValues();
                    updatePicturePreferenceVisibility();
                    showWorkingWindowPreview(picture_alpha);
                    dismissDialogIfShowing(alertDialog);
                });
        });
    }

    private void initializeBatchMode(Intent intent) {
        requireActivity().setTitle(R.string.settings_batch_label);
        Edit_Mode = false;
        wasHidden = false;
        PictureSettingsLoader.LoadedSettings loadedSettings =
                PictureSettingsLoader.loadBatch(intent, pictureData, getString(R.string.settings_batch_label));
        if (loadedSettings == null) {
            ApplicationMethods.showToast(requireContext(), R.string.action_batch_edit_no_selection);
            requireActivity().finish();
            return;
        }
        applyLoadedSettings(loadedSettings);
        bindPreferenceValues();
        updatePicturePreferenceVisibility();
    }

    private void applyLoadedSettings(@NonNull PictureSettingsLoader.LoadedSettings loadedSettings) {
        PictureSettingsLoader.SettingsSnapshot snapshot = loadedSettings.snapshot;
        PictureId = loadedSettings.pictureId;
        PictureName = loadedSettings.pictureName;
        bitmap = loadedSettings.bitmap;
        batchPictureIds.clear();
        if (loadedSettings.batchPictureIds != null) {
            batchPictureIds.addAll(loadedSettings.batchPictureIds);
        }
        position_x = snapshot.positionX;
        position_y = snapshot.positionY;
        picture_degree = snapshot.pictureDegree;
        picture_alpha = snapshot.pictureAlpha;
        picture_corner_radius_ratio = snapshot.cornerRadiusRatio;
        picture_corner_radius_mask = snapshot.cornerRadiusMask;
        picture_edge_feather_ratio = snapshot.edgeFeatherRatio;
        picture_edge_feather_mask = snapshot.edgeFeatherMask;
        touch_and_move = snapshot.touchAndMove;
        allow_picture_over_layout = snapshot.allowPictureOverLayout;
        default_zoom = snapshot.defaultZoom;
        zoom = snapshot.zoom;
    }

    private void discardLoadedSettings(@NonNull Context appContext,
                                       @NonNull PictureSettingsLoader.LoadedSettings loadedSettings) {
        ImageMethods.recycleBitmap(loadedSettings.bitmap);
        if (loadedSettings.newPicture && loadedSettings.pictureId != null) {
            ImageMethods.clearAllTemp(appContext, loadedSettings.pictureId);
        }
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

    private void updatePicturePreferenceVisibility() {
        Preference generalCategory = findPreference(Config.PREFERENCE_CATEGORY_GENERAL);
        if (generalCategory != null) {
            generalCategory.setVisible(!Batch_Mode);
        }
        Preference namePreference = findPreference(Config.PREFERENCE_PICTURE_NAME);
        if (namePreference != null) {
            namePreference.setVisible(!Batch_Mode);
        }
        Preference replacePreference = findPreference(Config.PREFERENCE_PICTURE_REPLACE);
        if (replacePreference != null) {
            replacePreference.setVisible(Edit_Mode && !Batch_Mode);
        }
    }

    @Nullable
    private Bitmap loadCurrentSourceBitmap() {
        return loadCurrentSourceBitmap(requireContext());
    }

    @Nullable
    private Bitmap loadCurrentSourceBitmap(Context context) {
        return PictureSettingsLoader.loadCurrentSourceBitmap(context, Edit_Mode, PictureId);
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
        if (Batch_Mode) {
            allow_picture_over_layout = allow;
            batchOverLayoutChanged = true;
            return;
        }
        allow_picture_over_layout = allow;
        showWorkingWindowPreview(picture_alpha);
    }

    private void setPictureTouchAndMove(boolean touchable_and_moveable) {
        if (Batch_Mode) {
            touch_and_move = touchable_and_moveable;
            batchTouchAndMoveChanged = true;
            return;
        }
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
        AppExecutors.io().execute(() -> {
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
        });
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
        if (!Batch_Mode && !ensureSourceBitmapLoaded()) {
            return;
        }
        final float maxSize = Batch_Mode
                ? Math.max(BATCH_MAX_ZOOM, roundToThreeDecimals(zoom))
                : roundToThreeDecimals(ImageMethods.getDefaultZoom(requireContext(), bitmap, true));
        settingsDialogController.showSizeDialog(
                new PictureSettingsDialogController.SizeDialogRequest(Batch_Mode, maxSize, createPreviewValues()),
                value -> {
                    zoom = value;
                    if (Batch_Mode) {
                        batchZoomChanged = true;
                        batchFitScreenHeightChanged = false;
                    }
                }
        );
    }

    private void fitPictureToScreenHeight() {
        if (Batch_Mode) {
            batchFitScreenHeightChanged = true;
            batchZoomChanged = false;
            position_y = 0;
            Preference preference = findPreference(Config.PREFERENCE_PICTURE_FIT_SCREEN_HEIGHT);
            if (preference != null) {
                preference.setSummary(R.string.settings_picture_fit_screen_height_batch_pending);
            }
            return;
        }
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
        positionChanged = true;
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
        if (!Batch_Mode && !ensureSourceBitmapLoaded()) {
            return;
        }
        settingsDialogController.showDegreeDialog(createPreviewValues(), value -> {
            picture_degree = value;
            if (Batch_Mode) {
                batchDegreeChanged = true;
            }
        });
    }

    // 方法名改为 showPictureAlphaDialog 避免与 FloatImageView.setPictureAlpha(float) 重名
    private void showPictureAlphaDialog() {
        settingsDialogController.showAlphaDialog(createPreviewValues(), value -> {
            picture_alpha = value;
            if (Batch_Mode) {
                batchAlphaChanged = true;
            }
        });
    }

    private void setPictureCornerRadius() {
        settingsDialogController.showAppearanceDialog(
                new PictureSettingsDialogController.AppearanceDialogRequest(
                        true,
                        R.string.settings_picture_corner_radius,
                        R.string.settings_picture_corner_radius_value,
                        R.string.settings_picture_corner_radius_positions,
                        MAX_CORNER_RADIUS_PERCENT,
                        new int[]{
                                R.string.position_top_left,
                                R.string.position_top_right,
                                R.string.position_bottom_left,
                                R.string.position_bottom_right
                        },
                        new int[]{
                                Config.MASK_CORNER_TOP_LEFT,
                                Config.MASK_CORNER_TOP_RIGHT,
                                Config.MASK_CORNER_BOTTOM_LEFT,
                                Config.MASK_CORNER_BOTTOM_RIGHT
                        },
                        picture_corner_radius_ratio,
                        picture_corner_radius_mask,
                        createPreviewValues()
                ),
                (ratio, mask) -> {
                    picture_corner_radius_ratio = ratio;
                    picture_corner_radius_mask = mask;
                    if (Batch_Mode) {
                        batchCornerRadiusChanged = true;
                    }
                    updateAppearancePreferenceSummaries();
                }
        );
    }

    private void setPictureEdgeFeather() {
        settingsDialogController.showAppearanceDialog(
                new PictureSettingsDialogController.AppearanceDialogRequest(
                        false,
                        R.string.settings_picture_edge_feather,
                        R.string.settings_picture_edge_feather_value,
                        R.string.settings_picture_edge_feather_edges,
                        MAX_EDGE_FEATHER_PERCENT,
                        new int[]{
                                R.string.position_top,
                                R.string.position_bottom,
                                R.string.position_left,
                                R.string.position_right
                        },
                        new int[]{
                                Config.MASK_EDGE_TOP,
                                Config.MASK_EDGE_BOTTOM,
                                Config.MASK_EDGE_LEFT,
                                Config.MASK_EDGE_RIGHT
                        },
                        picture_edge_feather_ratio,
                        picture_edge_feather_mask,
                        createPreviewValues()
                ),
                (ratio, mask) -> {
                    picture_edge_feather_ratio = ratio;
                    picture_edge_feather_mask = mask;
                    if (Batch_Mode) {
                        batchEdgeFeatherChanged = true;
                    }
                    updateAppearancePreferenceSummaries();
                }
        );
    }

    private void setPicturePosition() {
        if (!Batch_Mode && !ensureSourceBitmapLoaded()) {
            return;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean touchable_edit = !Batch_Mode && (touch_and_move || sharedPreferences.getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false));
        settingsDialogController.showPositionDialog(
                new PictureSettingsDialogController.PositionDialogRequest(touchable_edit, getWindowSize(), createPreviewValues()),
                (positionX, positionY) -> {
                    position_x = positionX;
                    position_y = positionY;
                    if (Batch_Mode) {
                        batchPositionChanged = true;
                    } else {
                        positionChanged = true;
                    }
                }
        );
    }

    @NonNull
    private PictureSettingsDialogController.PreviewValues createPreviewValues() {
        return new PictureSettingsDialogController.PreviewValues(
                zoom,
                picture_degree,
                picture_alpha,
                picture_corner_radius_ratio,
                picture_corner_radius_mask,
                picture_edge_feather_ratio,
                picture_edge_feather_mask,
                position_x,
                position_y,
                touch_and_move,
                allow_picture_over_layout
        );
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
        return resolveScreenHeightZoom(sourceBitmap, degreeValue, getWindowSize());
    }

    private float resolveScreenHeightZoom(@NonNull Bitmap sourceBitmap, float degreeValue, @NonNull Point windowSize) {
        return PictureSettingsSaveController.resolveScreenHeightZoom(sourceBitmap, degreeValue, windowSize);
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

    private void updateAppearancePreferenceSummaries() {
        Preference cornerRadiusPreference = findPreference(Config.PREFERENCE_PICTURE_CORNER_RADIUS);
        if (cornerRadiusPreference != null) {
            cornerRadiusPreference.setSummary(getString(
                    R.string.appearance_summary_format,
                    formatRatioPercent(picture_corner_radius_ratio),
                    PictureSettingsAppearanceOptions.formatCornerMaskSummary(requireContext(), picture_corner_radius_mask)
            ));
        }
        Preference edgeFeatherPreference = findPreference(Config.PREFERENCE_PICTURE_EDGE_FEATHER);
        if (edgeFeatherPreference != null) {
            edgeFeatherPreference.setSummary(getString(
                    R.string.appearance_summary_format,
                    formatRatioPercent(picture_edge_feather_ratio),
                    PictureSettingsAppearanceOptions.formatEdgeMaskSummary(requireContext(), picture_edge_feather_mask)
            ));
        }
    }

    private void showWorkingWindowPreview(float alpha) {
        showWorkingWindowPreview(alpha, false);
    }

    private void showWorkingWindowPreview(float alpha, boolean reloadSource) {
        if (Batch_Mode) {
            return;
        }
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
        if (Batch_Mode || PictureId == null || shouldAbortFragmentWork()) {
            return;
        }
        PictureSettingsPreviewController.showPreview(
                requireContext(),
                PictureId,
                new PictureSettingsPreviewController.PreviewRequest(
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
                        previewMode,
                        reloadSource,
                        useRuntimePosition
                )
        );
    }

    private Point getPreviewPosition(int fallbackX, int fallbackY) {
        Context context = getContext();
        if (context == null || PictureId == null) {
            return new Point(fallbackX, fallbackY);
        }
        return PictureSettingsPreviewController.getPreviewPosition(context, PictureId, fallbackX, fallbackY);
    }

    private void saveBatchData(@Nullable Runnable onComplete, @Nullable Runnable onFailed) {
        final PictureSettingsSaveController.BatchSaveRequest saveRequest =
                new PictureSettingsSaveController.BatchSaveRequest(
                        new ArrayList<>(batchPictureIds),
                        batchZoomChanged,
                        batchFitScreenHeightChanged,
                        batchDegreeChanged,
                        batchAlphaChanged,
                        batchCornerRadiusChanged,
                        batchEdgeFeatherChanged,
                        batchPositionChanged,
                        batchTouchAndMoveChanged,
                        batchOverLayoutChanged,
                        zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio,
                        picture_corner_radius_mask,
                        picture_edge_feather_ratio,
                        picture_edge_feather_mask,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        getWindowSize()
                );
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            boolean saveFailed = !PictureSettingsSaveController.saveBatch(appContext, saveRequest);
            final boolean finalSaveFailed = saveFailed;
            if (!isAdded() || getActivity() == null) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                if (finalSaveFailed) {
                    ApplicationMethods.showToast(requireContext(), R.string.picture_settings_save_failed);
                    if (onFailed != null) {
                        onFailed.run();
                    }
                    return;
                }
                changesSaved = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void saveAllData(@Nullable Runnable onComplete, @Nullable Runnable onFailed) {
        if (Batch_Mode) {
            saveBatchData(onComplete, onFailed);
            return;
        }
        if (touch_and_move && !positionChanged) {
            Point previewPosition = getPreviewPosition(position_x, position_y);
            position_x = previewPosition.x;
            position_y = previewPosition.y;
        }
        final PictureSettingsSaveController.SingleSaveRequest saveRequest =
                new PictureSettingsSaveController.SingleSaveRequest(
                        pictureData,
                        PictureId,
                        PictureName,
                        zoom,
                        default_zoom,
                        picture_degree,
                        picture_alpha,
                        picture_corner_radius_ratio,
                        picture_corner_radius_mask,
                        picture_edge_feather_ratio,
                        picture_edge_feather_mask,
                        position_x,
                        position_y,
                        touch_and_move,
                        allow_picture_over_layout,
                        wasHidden,
                        positionChanged
                );
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            if (!PictureSettingsSaveController.saveSingle(appContext, saveRequest)) {
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
            if (shouldAbortFragmentWork()) {
                releaseSourceBitmap();
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (shouldAbortFragmentWork()) {
                    releaseSourceBitmap();
                    return;
                }
                touch_and_move = saveRequest.touchAndMove;
                allow_picture_over_layout = saveRequest.overLayout;
                picture_alpha = saveRequest.alpha;
                picture_corner_radius_ratio = saveRequest.cornerRadiusRatio;
                picture_corner_radius_mask = saveRequest.cornerRadiusMask;
                picture_edge_feather_ratio = saveRequest.edgeFeatherRatio;
                picture_edge_feather_mask = saveRequest.edgeFeatherMask;
                position_x = saveRequest.positionX;
                position_y = saveRequest.positionY;
                wasHidden = saveRequest.wasHidden;
                releaseSourceBitmap();
                changesSaved = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void clearEditView() {
        if (Batch_Mode) {
            if (Batch_Import_Mode && !changesSaved) {
                clearImportedBatchPictures();
            }
            return;
        }
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
        if (Batch_Mode) {
            return;
        }
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

    private void clearImportedBatchPictures() {
        Context context = getContext();
        if (context == null || batchPictureIds == null || batchPictureIds.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        for (String pictureId : batchPictureIds) {
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            PictureData importedPictureData = new PictureData();
            importedPictureData.setDataControl(pictureId);
            importedPictureData.remove();
            ImageMethods.clearAllTemp(appContext, pictureId);
            OverlayRuntimeController.deletePicture(appContext, pictureId);
        }
    }

}
