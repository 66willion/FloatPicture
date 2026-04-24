package tool.xfy9326.floatpicture.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
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

import androidx.annotation.NonNull;
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
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Services.TrustedOverlayAccessibilityService;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.View.AdvancedRecyclerView;
import tool.xfy9326.floatpicture.View.ManageListAdapter;
import tool.xfy9326.floatpicture.View.RecyclerFastScrollerView;

public class MainActivity extends AppCompatActivity {
    private static final int MAIN_LIST_VIEW_CACHE_SIZE = 8;
    private static final long PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS = 300L;
    private static final long MANAGE_LIST_PREVIEW_FALLBACK_FIRST_DELAY_MS = 40L;
    private static final long MANAGE_LIST_PREVIEW_FALLBACK_SECOND_DELAY_MS = 140L;
    private static final int OPERATION_SNACKBAR_DURATION_MS = 200;
    private static final int MANAGE_LIST_LANDSCAPE_SPAN_COUNT = 2;
    private static final int MANAGE_LIST_LANDSCAPE_ITEM_GAP_DP = 8;
    private static final float FLOATING_BLUR_RADIUS_DP = 18f;

    private ManageListAdapter manageListAdapter;
    private AdvancedRecyclerView recyclerView;
    private RecyclerFastScrollerView fastScrollerView;
    private FloatingActionButton randomWindowButton;
    private FloatingActionButton releaseMemoryButton;
    private FloatingActionButton pureOverlayButton;
    private FloatingActionButton trustedOverlayButton;
    private RecyclerView.ItemDecoration manageListSpacingDecoration;
    private boolean pureOverlayToggleInProgress = false;
    private long BackClickTime;
    private ActivityResultLauncher<String> picturePickerLauncher;
    private ActivityResultLauncher<Intent> addPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> editPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> trustedOverlaySettingsLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private final Runnable trustedOverlayStateUpdater = this::updateTrustedOverlayButtonState;
    private final Runnable manageListPreviewFirstFallbackRunnable = this::runManageListPreviewFirstFallback;
    private final Runnable manageListPreviewSecondFallbackRunnable = this::runManageListPreviewSecondFallback;
    private final RecyclerView.OnScrollListener manageListPreviewScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateManageListPreviewViewport(recyclerView, false);
            handleManageListPreviewFallbackScheduling(recyclerView);
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            updateManageListPreviewViewport(recyclerView, newState == RecyclerView.SCROLL_STATE_IDLE);
            handleManageListPreviewFallbackScheduling(recyclerView);
        }
    };
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
        refreshManageListData();
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
        if (recyclerView != null) {
            cancelManageListPreviewFallbacks(recyclerView);
            recyclerView.removeOnScrollListener(manageListPreviewScrollListener);
            recyclerView.setAdapter(null);
        }
        if (fastScrollerView != null) {
            fastScrollerView.attachToRecyclerView(null);
        }
        manageListAdapter = null;
        recyclerView = null;
        fastScrollerView = null;
        randomWindowButton = null;
        releaseMemoryButton = null;
        pureOverlayButton = null;
        trustedOverlayButton = null;
        super.onDestroy();
    }

    private void init(Bundle savedInstanceState) {
        BackClickTime = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
        }
        ViewSet();
        if (PermissionMethods.hasOverlayPermission(this)) {
            IOMethods.setNoMedia();
        }
    }

    private void ViewSet() {
        View actionsLayout = findViewById(R.id.main_layout_actions);
        actionsLayout.bringToFront();
        actionsLayout.setTranslationZ(18f);
        applyFloatingBackgroundBlur(findViewById(R.id.main_layout_actions_blur));

        manageListAdapter = new ManageListAdapter(this, this::launchPictureSettingsForEdit);
        recyclerView = findViewById(R.id.main_list_manage);
        recyclerView.setLayoutManager(createManageListLayoutManager());
        applyManageListSpacingDecoration();
        recyclerView.setAdapter(manageListAdapter);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(MAIN_LIST_VIEW_CACHE_SIZE);
        recyclerView.setEmptyView(findViewById(R.id.layout_widget_empty_view));
        recyclerView.addOnScrollListener(manageListPreviewScrollListener);
        recyclerView.post(() -> updateManageListPreviewViewport(recyclerView, true));
        fastScrollerView = findViewById(R.id.main_fast_scroller);
        if (fastScrollerView != null) {
            fastScrollerView.attachToRecyclerView(recyclerView);
            fastScrollerView.bringToFront();
            fastScrollerView.setTranslationZ(getResources().getDisplayMetrics().density * 18f);
        }

        randomWindowButton = findViewById(R.id.main_button_random_window);
        if (randomWindowButton != null) {
            randomWindowButton.setOnClickListener(view -> showRandomWindow());
        }

        FloatingActionButton floatingActionButton = findViewById(R.id.main_button_add);
        if (floatingActionButton != null) {
            floatingActionButton.setOnClickListener(view -> {
                if (PermissionMethods.hasOverlayPermission(MainActivity.this)) {
                    picturePickerLauncher.launch("image/*");
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PermissionMethods.askOverlayPermission(MainActivity.this, this::launchOverlayPermissionRequest);
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
        View drawerButton = findViewById(R.id.main_button_drawer);
        if (drawerButton != null) {
            drawerButton.bringToFront();
            drawerButton.setTranslationZ(18f);
            drawerButton.setOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.START));
        }
        applyFloatingBackgroundBlur(findViewById(R.id.main_button_drawer_blur));

        NavigationView navigationView = findViewById(R.id.main_navigation_view);
        ApplicationMethods.disableNavigationViewScrollbars(navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            int itemId = item.getItemId();
            if (itemId == R.id.menu_global_settings) {
                startActivity(new Intent(MainActivity.this, GlobalSettingsActivity.class));
            } else if (itemId == R.id.menu_close_all_windows) {
                drawerLayout.post(this::hideAllWindowsSafely);
            } else if (itemId == R.id.menu_release_memory) {
                drawerLayout.post(this::releaseMemory);
            } else if (itemId == R.id.menu_about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            } else if (itemId == R.id.menu_back_to_launcher) {
                MainActivity.this.moveTaskToBack(true);
            } else if (itemId == R.id.menu_exit) {
                ApplicationMethods.CloseApplication(MainActivity.this);
            }
            return false;
        });
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

    private void registerLaunchers() {
        picturePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPictureSelected);
        addPictureSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onAddPictureSettingsResult
        );
        editPictureSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onEditPictureSettingsResult
        );
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> PermissionMethods.delayOverlayPermissionCheck(this)
        );
        trustedOverlaySettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> refreshTrustedOverlayButtonState()
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

    private void onAddPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }
        manageListAdapter.refreshData();
        if (recyclerView != null) {
            recyclerView.post(() -> updateManageListPreviewViewport(recyclerView, true));
        }
        SnackShow(this, R.string.action_add_window);
        OverlayRuntimeController.refreshNotification(this);
    }

    private void onEditPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        manageListAdapter.refreshData();
        if (recyclerView != null) {
            recyclerView.post(() -> updateManageListPreviewViewport(recyclerView, true));
        }
    }

    private void launchOverlayPermissionRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionLauncher.launch(PermissionMethods.createOverlayPermissionIntent(this));
        }
    }

    private void launchPictureSettingsForEdit(Intent intent) {
        editPictureSettingsLauncher.launch(intent);
    }

    private void showRandomWindow() {
        if (randomWindowButton == null || pureOverlayToggleInProgress) {
            return;
        }
        if (!PermissionMethods.hasOverlayPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
            }
            return;
        }
        randomWindowButton.setEnabled(false);
        new Thread(() -> {
            int result = OverlayRuntimeController.showRandomWindow(getApplicationContext());
            runOnUiThread(() -> {
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
        }).start();
    }

    private void hideAllWindowsSafely() {
        if (pureOverlayToggleInProgress) {
            return;
        }
        if (!PermissionMethods.hasOverlayPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
            }
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
        new Thread(() -> {
            ApplicationMethods.MemoryReleaseResult result = ApplicationMethods.releaseMemory(getApplicationContext());
            runOnUiThread(() -> {
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
        }).start();
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
        if (pureOverlayModeEnabled && PermissionMethods.hasOverlayPermission(this)) {
            OverlayRuntimeController.refreshNotification(this);
        }
    }

    private void togglePureOverlayMode() {
        if (pureOverlayToggleInProgress) {
            return;
        }
        boolean enabled = ApplicationMethods.isPureOverlayModeEnabled(this);
        if (enabled) {
            pureOverlayToggleInProgress = true;
            pureOverlayButton.setEnabled(false);
            ApplicationMethods.setPureOverlayModeEnabled(this, false);
            OverlayRuntimeStateStore.clearPureOverlayManagedPictureIds(this);
            OverlayRuntimeController.refreshNotification(this);
            updatePureOverlayButtonState();
            pureOverlayButton.postDelayed(() -> {
                pureOverlayToggleInProgress = false;
                if (!isFinishing() && !isDestroyed()) {
                    pureOverlayButton.setEnabled(true);
                }
            }, PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS);
            return;
        }
        if (!PermissionMethods.hasOverlayPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
            }
            return;
        }
        if (!ManageMethods.hasVisibleWindowsConfigured(this)) {
            SnackShow(this, R.string.main_pure_overlay_requires_visible_window);
            return;
        }
        java.util.Set<String> pureOverlayManagedPictureIds = ManageMethods.getVisibleConfiguredPictureIds(this);
        if (pureOverlayManagedPictureIds.isEmpty()) {
            SnackShow(this, R.string.main_pure_overlay_requires_visible_window);
            return;
        }
        pureOverlayToggleInProgress = true;
        pureOverlayButton.setEnabled(false);
        OverlayRuntimeStateStore.savePureOverlayManagedPictureIds(this, pureOverlayManagedPictureIds);
        ApplicationMethods.setPureOverlayModeEnabled(this, true);
        ApplicationMethods.startNotificationControl(this);
        updatePureOverlayButtonState();
        pureOverlayButton.post(() -> {
            pureOverlayToggleInProgress = false;
            ApplicationMethods.CloseMainUi(this);
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
            drawerLayout.closeDrawer(GravityCompat.START);
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
        if (manageListAdapter == null) {
            return;
        }
        manageListAdapter.refreshData();
        if (recyclerView != null) {
            recyclerView.post(() -> updateManageListPreviewViewport(recyclerView, true));
        }
        if (fastScrollerView != null) {
            fastScrollerView.refreshScrollerState();
        }
    }

    private void updateManageListPreviewViewport(@NonNull RecyclerView targetRecyclerView, boolean idle) {
        if (manageListAdapter == null) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = targetRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition();
        manageListAdapter.updatePreviewViewport(firstVisiblePosition, lastVisiblePosition, idle);
    }

    private void handleManageListPreviewFallbackScheduling(@NonNull RecyclerView targetRecyclerView) {
        if (targetRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
            scheduleManageListPreviewFallbacks(targetRecyclerView);
        } else {
            cancelManageListPreviewFallbacks(targetRecyclerView);
        }
    }

    private void scheduleManageListPreviewFallbacks(@NonNull RecyclerView targetRecyclerView) {
        cancelManageListPreviewFallbacks(targetRecyclerView);
        targetRecyclerView.postDelayed(
                manageListPreviewFirstFallbackRunnable,
                MANAGE_LIST_PREVIEW_FALLBACK_FIRST_DELAY_MS
        );
    }

    private void cancelManageListPreviewFallbacks(@NonNull RecyclerView targetRecyclerView) {
        targetRecyclerView.removeCallbacks(manageListPreviewFirstFallbackRunnable);
        targetRecyclerView.removeCallbacks(manageListPreviewSecondFallbackRunnable);
    }

    private void runManageListPreviewFirstFallback() {
        if (manageListAdapter == null || recyclerView == null || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        int remainingBlankCount = manageListAdapter.refreshVisiblePreviewFallback(recyclerView);
        if (fastScrollerView != null) {
            fastScrollerView.refreshScrollerState();
        }
        recyclerView.removeCallbacks(manageListPreviewSecondFallbackRunnable);
        if (remainingBlankCount > 0) {
            recyclerView.postDelayed(
                    manageListPreviewSecondFallbackRunnable,
                    MANAGE_LIST_PREVIEW_FALLBACK_SECOND_DELAY_MS
            );
        }
    }

    private void runManageListPreviewSecondFallback() {
        if (manageListAdapter == null || recyclerView == null || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        manageListAdapter.refreshVisiblePreviewFallback(recyclerView);
        if (fastScrollerView != null) {
            fastScrollerView.refreshScrollerState();
        }
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
