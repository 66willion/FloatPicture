package tool.xfy9326.floatpicture.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResult;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Services.TrustedOverlayAccessibilityService;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.View.AdvancedRecyclerView;
import tool.xfy9326.floatpicture.View.ManageListAdapter;
import tool.xfy9326.floatpicture.View.RecyclerFastScrollerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int MAIN_LIST_VIEW_CACHE_SIZE = 8;
    private static final long PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS = 300L;
    private static final long STARTUP_SPLASH_MAX_WAIT_MS = 1500L;
    private static final long STARTUP_SPLASH_FADE_MS = 180L;
    private static final int OPERATION_SNACKBAR_DURATION_MS = 200;
    private static final int MANAGE_LIST_LANDSCAPE_SPAN_COUNT = 2;
    private static final int MANAGE_LIST_LANDSCAPE_ITEM_GAP_DP = 8;
    private static final float FLOATING_BLUR_RADIUS_DP = 18f;

    private ManageListAdapter manageListAdapter;
    private ManageListPreviewController manageListPreviewController;
    private MainDrawerController mainDrawerController;
    private AdvancedRecyclerView recyclerView;
    private RecyclerFastScrollerView fastScrollerView;
    private FloatingActionButton randomWindowButton;
    private FloatingActionButton releaseMemoryButton;
    private FloatingActionButton pureOverlayButton;
    private FloatingActionButton trustedOverlayButton;
    private MaterialButton drawerButton;
    private View batchSelectAllContainer;
    private CheckBox batchSelectAllCheckBox;
    private View startupSplashOverlay;
    private RecyclerView.ItemDecoration manageListSpacingDecoration;
    private boolean pureOverlayToggleInProgress = false;
    private boolean startupSplashDismissed = false;
    private boolean batchEditMode = false;
    private boolean updatingBatchSelectAllState = false;
    private long BackClickTime;
    private ActivityResultLauncher<String> picturePickerLauncher;
    private ActivityResultLauncher<String> batchPicturePickerLauncher;
    private ActivityResultLauncher<Intent> addPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> editPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> batchPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> trustedOverlaySettingsLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private final Runnable trustedOverlayStateUpdater = this::updateTrustedOverlayButtonState;
    private final Runnable startupSplashTimeoutRunnable = this::dismissStartupSplash;
    private final BroadcastReceiver overlayRuntimeStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            refreshManageListData();
            updatePureOverlayButtonState();
            refreshTrustedOverlayButtonState();
        }
    };
    private boolean overlayRuntimeReceiverRegistered = false;

    public static void SnackShow(Activity mActivity, int resourceId) {
        CoordinatorLayout coordinatorLayout = mActivity.findViewById(R.id.main_layout_content);
        Snackbar snackbar = ApplicationMethods.makeStyledSnackbar(
                mActivity,
                coordinatorLayout,
                resourceId,
                OPERATION_SNACKBAR_DURATION_MS
        );
        snackbar.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deactivatePureOverlayModeIfNeeded();
        registerLaunchers();
        initBackPressedCallback();
        init(savedInstanceState);
        ApplicationMethods.startNotificationControl(this);
        requestNotificationPermissionIfNeeded();
        ApplicationMethods.runStartupMaintenance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        deactivatePureOverlayModeIfNeeded();
        if (startupSplashDismissed) {
            refreshManageListData();
        } else {
            refreshManageListData(this::dismissStartupSplash);
        }
        updatePureOverlayButtonState();
        refreshTrustedOverlayButtonState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerOverlayRuntimeReceiver();
    }

    @Override
    protected void onStop() {
        unregisterOverlayRuntimeReceiver();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (trustedOverlayButton != null) {
            trustedOverlayButton.removeCallbacks(trustedOverlayStateUpdater);
        }
        if (startupSplashOverlay != null) {
            startupSplashOverlay.removeCallbacks(startupSplashTimeoutRunnable);
            startupSplashOverlay.animate().cancel();
        }
        if (manageListPreviewController != null) {
            manageListPreviewController.detach();
            manageListPreviewController = null;
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        if (fastScrollerView != null) {
            fastScrollerView.attachToRecyclerView(null);
        }
        manageListAdapter = null;
        manageListPreviewController = null;
        recyclerView = null;
        fastScrollerView = null;
        randomWindowButton = null;
        releaseMemoryButton = null;
        pureOverlayButton = null;
        trustedOverlayButton = null;
        drawerButton = null;
        batchSelectAllContainer = null;
        batchSelectAllCheckBox = null;
        startupSplashOverlay = null;
        mainDrawerController = null;
        super.onDestroy();
    }

    private void init(Bundle savedInstanceState) {
        BackClickTime = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
        }
        ViewSet();
        if (PermissionMethods.canStartOverlayRuntime(this)) {
            ApplicationMethods.startNotificationControl(this);
            IOMethods.setNoMedia();
        }
    }

    private void ViewSet() {
        View actionsLayout = findViewById(R.id.main_layout_actions);
        actionsLayout.bringToFront();
        actionsLayout.setTranslationZ(18f);
        applyFloatingBackgroundBlur(findViewById(R.id.main_layout_actions_blur));

        manageListAdapter = new ManageListAdapter(this, this::launchPictureSettingsForEdit);
        manageListAdapter.setBatchSelectionListener(this::onBatchSelectionChanged);
        recyclerView = findViewById(R.id.main_list_manage);
        recyclerView.setLayoutManager(createManageListLayoutManager());
        applyManageListSpacingDecoration();
        recyclerView.setAdapter(manageListAdapter);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(MAIN_LIST_VIEW_CACHE_SIZE);
        recyclerView.setEmptyView(findViewById(R.id.layout_widget_empty_view));
        fastScrollerView = findViewById(R.id.main_fast_scroller);
        if (fastScrollerView != null) {
            fastScrollerView.attachToRecyclerView(recyclerView);
            fastScrollerView.bringToFront();
            fastScrollerView.setTranslationZ(getResources().getDisplayMetrics().density * 18f);
        }
        manageListPreviewController = new ManageListPreviewController(manageListAdapter, recyclerView, fastScrollerView);
        manageListPreviewController.attach();
        setupStartupSplash();

        randomWindowButton = findViewById(R.id.main_button_random_window);
        if (randomWindowButton != null) {
            randomWindowButton.setOnClickListener(view -> showRandomWindow());
        }

        FloatingActionButton floatingActionButton = findViewById(R.id.main_button_add);
        if (floatingActionButton != null) {
            floatingActionButton.setOnClickListener(view -> {
                if (ensureOverlayRuntimeStartable()) {
                    picturePickerLauncher.launch("image/*");
                }
            });
        }

        pureOverlayButton = findViewById(R.id.main_button_pure_overlay);
        pureOverlayButton.setOnClickListener(view -> togglePureOverlayMode());
        updatePureOverlayButtonState();

        trustedOverlayButton = findViewById(R.id.main_button_trusted_overlay);
        trustedOverlayButton.setOnClickListener(view ->
                trustedOverlaySettingsLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        refreshTrustedOverlayButtonState();

        releaseMemoryButton = null;

        final DrawerLayout drawerLayout = findViewById(R.id.main_drawer_layout);
        drawerButton = findViewById(R.id.main_button_drawer);
        if (drawerButton != null) {
            drawerButton.bringToFront();
            drawerButton.setTranslationZ(18f);
            drawerButton.setOnClickListener(view -> {
                if (batchEditMode) {
                    launchBatchPictureSettings();
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
        applyFloatingBackgroundBlur(findViewById(R.id.main_button_drawer_blur));
        batchSelectAllContainer = findViewById(R.id.main_batch_select_all_container);
        batchSelectAllCheckBox = findViewById(R.id.main_check_batch_select_all);
        if (batchSelectAllContainer != null) {
            batchSelectAllContainer.bringToFront();
            batchSelectAllContainer.setTranslationZ(18f);
            batchSelectAllContainer.setOnClickListener(view -> {
                if (batchSelectAllCheckBox != null) {
                    batchSelectAllCheckBox.toggle();
                }
            });
        }
        if (batchSelectAllCheckBox != null) {
            batchSelectAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (updatingBatchSelectAllState || manageListAdapter == null) {
                    return;
                }
                manageListAdapter.setAllBatchItemsSelected(isChecked);
            });
        }
        updateBatchEditUi(false);
        applyFloatingBackgroundBlur(findViewById(R.id.main_drawer_capsule_blur));
        mainDrawerController = new MainDrawerController(
                this,
                drawerLayout,
                this::hideAllWindowsSafely,
                this::releaseMemory,
                this::enterBatchEditMode,
                this::launchBatchImportPicker
        );
        mainDrawerController.setup();
    }

    private RecyclerView.LayoutManager createManageListLayoutManager() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return new GridLayoutManager(this, MANAGE_LIST_LANDSCAPE_SPAN_COUNT);
        }
        return new LinearLayoutManager(this);
    }

    private void applyManageListSpacingDecoration() {
        if (recyclerView == null) {
            return;
        }
        if (manageListSpacingDecoration != null) {
            recyclerView.removeItemDecoration(manageListSpacingDecoration);
            manageListSpacingDecoration = null;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            manageListSpacingDecoration = new LandscapeGridSpacingDecoration(dpToPx(MANAGE_LIST_LANDSCAPE_ITEM_GAP_DP));
            recyclerView.addItemDecoration(manageListSpacingDecoration);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void applyFloatingBackgroundBlur(View backgroundView) {
        if (backgroundView == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            backgroundView.setClipToOutline(true);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        float radius = FLOATING_BLUR_RADIUS_DP * getResources().getDisplayMetrics().density;
        backgroundView.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    private void setupStartupSplash() {
        startupSplashOverlay = findViewById(R.id.main_splash_overlay);
        if (startupSplashOverlay == null) {
            startupSplashDismissed = true;
            return;
        }
        startupSplashOverlay.bringToFront();
        startupSplashOverlay.setAlpha(1f);
        startupSplashOverlay.setVisibility(View.VISIBLE);
        startupSplashOverlay.setClickable(true);
        startupSplashOverlay.removeCallbacks(startupSplashTimeoutRunnable);
        startupSplashOverlay.postDelayed(startupSplashTimeoutRunnable, STARTUP_SPLASH_MAX_WAIT_MS);
    }

    private void dismissStartupSplash() {
        if (startupSplashDismissed) {
            return;
        }
        startupSplashDismissed = true;
        if (startupSplashOverlay == null) {
            return;
        }
        startupSplashOverlay.removeCallbacks(startupSplashTimeoutRunnable);
        startupSplashOverlay.animate()
                .alpha(0f)
                .setDuration(STARTUP_SPLASH_FADE_MS)
                .withEndAction(() -> {
                    if (startupSplashOverlay == null) {
                        return;
                    }
                    startupSplashOverlay.setVisibility(View.GONE);
                    startupSplashOverlay.setClickable(false);
                })
                .start();
    }

    private void registerLaunchers() {
        picturePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPictureSelected);
        batchPicturePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::onBatchPicturesSelected);
        addPictureSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onAddPictureSettingsResult
        );
        editPictureSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onEditPictureSettingsResult
        );
        batchPictureSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onBatchPictureSettingsResult
        );
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> PermissionMethods.delayOverlayPermissionCheck(this)
        );
        trustedOverlaySettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    refreshTrustedOverlayButtonState();
                    if (PermissionMethods.canStartOverlayRuntime(this)) {
                        ApplicationMethods.startNotificationControl(this);
                    }
                }
        );
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        ApplicationMethods.startNotificationControl(this);
                    }
                }
        );
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void initBackPressedCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
    }

    private void onPictureSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        Intent intent = new Intent(this, PictureSettingsActivity.class);
        intent.putExtra(Config.INTENT_PICTURE_EDIT_MODE, false);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        addPictureSettingsLauncher.launch(intent);
    }

    private void onBatchPicturesSelected(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        final String defaultPictureName = getString(R.string.new_picture_name);
        MainActionController.importPictures(this, uris, defaultPictureName, importedPictureIds -> {
            if (isFinishing() || isDestroyed()) {
                MainActionController.cleanupImportedPictures(this, importedPictureIds);
                return;
            }
            if (importedPictureIds.isEmpty()) {
                SnackShow(this, R.string.action_batch_import_failed);
                return;
            }
            launchBatchPictureSettings(importedPictureIds, true);
        });
    }

    private void onAddPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }
        manageListAdapter.refreshData();
        if (manageListPreviewController != null) {
            manageListPreviewController.refreshViewportWhenReady();
        }
        SnackShow(this, R.string.action_add_window);
        OverlayRuntimeController.refreshNotification(this);
    }

    private void onEditPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        manageListAdapter.refreshData();
        if (manageListPreviewController != null) {
            manageListPreviewController.refreshViewportWhenReady();
        }
    }

    private void onBatchPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }
        exitBatchEditMode();
        refreshManageListData();
        SnackShow(this, R.string.action_batch_settings_saved);
        OverlayRuntimeController.refreshNotification(this, false);
    }

    private void launchOverlayPermissionRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionLauncher.launch(PermissionMethods.createOverlayPermissionIntent(this));
        }
    }

    private void launchPictureSettingsForEdit(Intent intent) {
        editPictureSettingsLauncher.launch(intent);
    }

    private void launchBatchPictureSettings() {
        if (manageListAdapter == null) {
            return;
        }
        launchBatchPictureSettings(manageListAdapter.getSelectedPictureIds(), false);
    }

    private void launchBatchPictureSettings(ArrayList<String> pictureIds, boolean importMode) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            SnackShow(this, R.string.action_batch_edit_no_selection);
            return;
        }
        Intent intent = new Intent(this, PictureSettingsActivity.class);
        intent.putExtra(Config.INTENT_PICTURE_BATCH_EDIT_MODE, true);
        intent.putExtra(Config.INTENT_PICTURE_BATCH_IMPORT_MODE, importMode);
        intent.putStringArrayListExtra(Config.INTENT_PICTURE_BATCH_EDIT_IDS, pictureIds);
        batchPictureSettingsLauncher.launch(intent);
    }

    private boolean ensureOverlayRuntimeStartable() {
        if (PermissionMethods.canStartOverlayRuntime(this)) {
            ApplicationMethods.startNotificationControl(this);
            IOMethods.setNoMedia();
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
        }
        return false;
    }

    private void launchBatchImportPicker() {
        if (!ensureOverlayRuntimeStartable()) {
            return;
        }
        batchPicturePickerLauncher.launch("image/*");
    }

    private void showRandomWindow() {
        if (randomWindowButton == null || pureOverlayToggleInProgress) {
            return;
        }
        if (!ensureOverlayRuntimeStartable()) {
            return;
        }
        randomWindowButton.setEnabled(false);
        MainActionController.showRandomWindow(this, result -> {
            if (randomWindowButton != null) {
                randomWindowButton.setEnabled(true);
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (result == OverlayRuntimeController.RANDOM_WINDOW_RESULT_SUCCESS) {
                SnackShow(this, R.string.action_random_window_success);
            } else if (result == OverlayRuntimeController.RANDOM_WINDOW_RESULT_NO_CANDIDATE) {
                SnackShow(this, R.string.action_random_window_no_candidate);
            } else {
                SnackShow(this, R.string.action_random_window_failed);
            }
        });
    }

    private void hideAllWindowsSafely() {
        if (pureOverlayToggleInProgress) {
            return;
        }
        if (!ensureOverlayRuntimeStartable()) {
            return;
        }
        hideAllWindows();
    }

    private void hideAllWindows() {
        OverlayRuntimeController.hideAllWindows(getApplicationContext());
        SnackShow(this, R.string.action_close_all_windows_success);
    }

    private void releaseMemory() {
        if (releaseMemoryButton != null && !releaseMemoryButton.isEnabled()) {
            return;
        }
        if (releaseMemoryButton != null) {
            releaseMemoryButton.setEnabled(false);
        }
        trimRecyclerPreviewCache();
        MainActionController.releaseMemory(this, result -> {
            if (releaseMemoryButton != null) {
                releaseMemoryButton.setEnabled(true);
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            CoordinatorLayout coordinatorLayout = findViewById(R.id.main_layout_content);
            Snackbar snackbar = ApplicationMethods.makeStyledSnackbar(
                    this,
                    coordinatorLayout,
                    getString(
                            R.string.action_release_memory_result,
                            result.getReleasedWindowCount(),
                            result.getDeletedTempFileCount()
                    ),
                    OPERATION_SNACKBAR_DURATION_MS
            );
            snackbar.show();
        });
    }

    private void trimRecyclerPreviewCache() {
        if (recyclerView == null) {
            return;
        }
        ImageMethods.clearManagePreviewCache();
        recyclerView.setItemViewCacheSize(0);
        recyclerView.getRecycledViewPool().clear();
        recyclerView.post(() -> recyclerView.setItemViewCacheSize(MAIN_LIST_VIEW_CACHE_SIZE));
    }

    private void refreshTrustedOverlayButtonState() {
        updateTrustedOverlayButtonState();
        if (trustedOverlayButton != null) {
            trustedOverlayButton.removeCallbacks(trustedOverlayStateUpdater);
            trustedOverlayButton.postDelayed(trustedOverlayStateUpdater, 350);
            trustedOverlayButton.postDelayed(trustedOverlayStateUpdater, 1200);
        }
    }

    private void updateTrustedOverlayButtonState() {
        if (trustedOverlayButton == null) {
            return;
        }
        boolean trustedOverlayEnabled = TrustedOverlayAccessibilityService.isEnabled(this);
        int backgroundColor = ContextCompat.getColor(
                this,
                trustedOverlayEnabled ? R.color.colorTrustedOverlayOn : R.color.colorTrustedOverlayOff
        );
        trustedOverlayButton.setContentDescription(getString(trustedOverlayEnabled ? R.string.main_trusted_overlay_on : R.string.main_trusted_overlay_off));
        trustedOverlayButton.setImageResource(trustedOverlayEnabled ? R.drawable.ic_visible : R.drawable.ic_invisible);
        trustedOverlayButton.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
    }

    private void deactivatePureOverlayModeIfNeeded() {
        boolean pureOverlayModeEnabled = ApplicationMethods.isPureOverlayModeEnabled(this);
        boolean managedPictureIdsPresent = OverlayRuntimeStateStore.hasPureOverlayManagedPictureIds(this);
        if (!pureOverlayModeEnabled && !managedPictureIdsPresent) {
            return;
        }
        if (pureOverlayModeEnabled) {
            ApplicationMethods.setPureOverlayModeEnabled(this, false);
        }
        if (managedPictureIdsPresent) {
            OverlayRuntimeStateStore.clearPureOverlayManagedPictureIds(this);
        }
        if (pureOverlayModeEnabled && PermissionMethods.canStartOverlayRuntime(this)) {
            OverlayRuntimeController.refreshNotification(this);
        }
    }

    private void togglePureOverlayMode() {
        if (pureOverlayToggleInProgress) {
            return;
        }
        boolean enabled = ApplicationMethods.isPureOverlayModeEnabled(this);
        Context appContext = getApplicationContext() != null ? getApplicationContext() : this;
        if (enabled) {
            pureOverlayToggleInProgress = true;
            pureOverlayButton.setEnabled(false);
            AppExecutors.io().execute(() -> {
                ApplicationMethods.setPureOverlayModeEnabled(appContext, false);
                OverlayRuntimeStateStore.clearPureOverlayManagedPictureIds(appContext);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    OverlayRuntimeController.refreshNotification(appContext);
                    updatePureOverlayButtonState();
                    pureOverlayButton.postDelayed(() -> {
                        pureOverlayToggleInProgress = false;
                        if (!isFinishing() && !isDestroyed()) {
                            pureOverlayButton.setEnabled(true);
                        }
                    }, PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS);
                });
            });
            return;
        }
        if (!ensureOverlayRuntimeStartable()) {
            return;
        }
        pureOverlayToggleInProgress = true;
        pureOverlayButton.setEnabled(false);
        AppExecutors.io().execute(() -> {
            Set<String> pureOverlayManagedPictureIds = ManageMethods.getVisibleConfiguredPictureIds(appContext);
            if (pureOverlayManagedPictureIds.isEmpty()) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    pureOverlayToggleInProgress = false;
                    pureOverlayButton.setEnabled(true);
                    SnackShow(this, R.string.main_pure_overlay_requires_visible_window);
                });
                return;
            }
            OverlayRuntimeStateStore.savePureOverlayManagedPictureIds(appContext, pureOverlayManagedPictureIds);
            ApplicationMethods.setPureOverlayModeEnabled(appContext, true);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ApplicationMethods.startNotificationControl(this);
                OverlayRuntimeController.refreshNotification(appContext, false);
                updatePureOverlayButtonState();
                pureOverlayButton.post(() -> {
                    pureOverlayToggleInProgress = false;
                    ApplicationMethods.CloseMainUi(this);
                });
            });
        });
    }

    private void updatePureOverlayButtonState() {
        if (pureOverlayButton == null) {
            return;
        }
        boolean pureOverlayEnabled = ApplicationMethods.isPureOverlayModeEnabled(this);
        int backgroundColor = ContextCompat.getColor(
                this,
                pureOverlayEnabled ? R.color.colorPureOverlayOn : R.color.colorPureOverlayOff
        );
        pureOverlayButton.setContentDescription(getString(pureOverlayEnabled ? R.string.main_pure_overlay_on : R.string.main_pure_overlay_off));
        pureOverlayButton.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
    }

    private void handleBackPressed() {
        DrawerLayout drawerLayout = findViewById(R.id.main_drawer_layout);
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (mainDrawerController != null && !mainDrawerController.isMenuPage()) {
                mainDrawerController.showMenuPage();
                return;
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (batchEditMode) {
            exitBatchEditMode();
            return;
        }
        long BackNowClickTime = System.currentTimeMillis();
        if ((BackNowClickTime - BackClickTime) < 2200) {
            ApplicationMethods.DoubleClickCloseSnackBar(this, true);
        } else {
            ApplicationMethods.DoubleClickCloseSnackBar(this, false);
            BackClickTime = System.currentTimeMillis();
        }
    }

    private void refreshManageListData() {
        refreshManageListData(null);
    }

    private void refreshManageListData(@Nullable Runnable completionCallback) {
        if (manageListAdapter == null) {
            if (completionCallback != null) {
                completionCallback.run();
            }
            return;
        }
        manageListAdapter.refreshData(() -> {
            if (manageListPreviewController != null) {
                manageListPreviewController.refreshViewportWhenReady();
            }
            if (fastScrollerView != null) {
                fastScrollerView.refreshScrollerState();
            }
            if (completionCallback != null) {
                completionCallback.run();
            }
        });
    }

    private void enterBatchEditMode() {
        if (batchEditMode || manageListAdapter == null) {
            return;
        }
        batchEditMode = true;
        manageListAdapter.setBatchEditMode(true);
        updateBatchEditUi(true);
    }

    private void exitBatchEditMode() {
        if (!batchEditMode) {
            return;
        }
        batchEditMode = false;
        if (manageListAdapter != null) {
            manageListAdapter.setBatchEditMode(false);
        }
        updateBatchEditUi(false);
    }

    private void updateBatchEditUi(boolean enabled) {
        if (drawerButton != null) {
            drawerButton.setIconResource(enabled ? R.drawable.ic_edit : R.drawable.ic_menu);
            drawerButton.setContentDescription(getString(enabled ? R.string.main_batch_edit : R.string.open));
        }
        if (batchSelectAllContainer != null) {
            batchSelectAllContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (!enabled && batchSelectAllCheckBox != null) {
            updatingBatchSelectAllState = true;
            batchSelectAllCheckBox.setChecked(false);
            updatingBatchSelectAllState = false;
        }
    }

    private void onBatchSelectionChanged(int selectedCount, int totalCount, boolean allSelected) {
        if (batchSelectAllCheckBox == null) {
            return;
        }
        updatingBatchSelectAllState = true;
        batchSelectAllCheckBox.setEnabled(totalCount > 0);
        batchSelectAllCheckBox.setChecked(allSelected);
        updatingBatchSelectAllState = false;
    }

    private void registerOverlayRuntimeReceiver() {
        if (overlayRuntimeReceiverRegistered) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter(OverlayRuntimeController.ACTION_RUNTIME_STATE_CHANGED);
        ContextCompat.registerReceiver(this, overlayRuntimeStateReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        overlayRuntimeReceiverRegistered = true;
    }

    private void unregisterOverlayRuntimeReceiver() {
        if (!overlayRuntimeReceiverRegistered) {
            return;
        }
        unregisterReceiver(overlayRuntimeStateReceiver);
        overlayRuntimeReceiverRegistered = false;
    }

    private static final class LandscapeGridSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int halfGap;

        private LandscapeGridSpacingDecoration(int gapPx) {
            halfGap = Math.max(gapPx / 2, 0);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            if ((position % MANAGE_LIST_LANDSCAPE_SPAN_COUNT) == 0) {
                outRect.right = halfGap;
            } else {
                outRect.left = halfGap;
            }
        }
    }
}
