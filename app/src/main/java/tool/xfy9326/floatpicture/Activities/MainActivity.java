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
import android.widget.TextView;

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
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.AdvancedRecyclerView;
import tool.xfy9326.floatpicture.View.GlobalSettingsFragment;
import tool.xfy9326.floatpicture.View.ManageListAdapter;
import tool.xfy9326.floatpicture.View.RecyclerFastScrollerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int MAIN_LIST_VIEW_CACHE_SIZE = 8;
    private static final long PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS = 300L;
    private static final long MANAGE_LIST_PREVIEW_FALLBACK_FIRST_DELAY_MS = 40L;
    private static final long MANAGE_LIST_PREVIEW_FALLBACK_SECOND_DELAY_MS = 140L;
    private static final int OPERATION_SNACKBAR_DURATION_MS = 200;
    private static final int MANAGE_LIST_LANDSCAPE_SPAN_COUNT = 2;
    private static final int MANAGE_LIST_LANDSCAPE_ITEM_GAP_DP = 8;
    private static final float FLOATING_BLUR_RADIUS_DP = 18f;
    private static final int DRAWER_PAGE_MENU = 0;
    private static final int DRAWER_PAGE_GLOBAL_SETTINGS = 1;
    private static final int DRAWER_PAGE_ABOUT = 2;
    private static final String DRAWER_GLOBAL_SETTINGS_FRAGMENT_TAG = "drawer_global_settings";

    private ManageListAdapter manageListAdapter;
    private AdvancedRecyclerView recyclerView;
    private RecyclerFastScrollerView fastScrollerView;
    private FloatingActionButton randomWindowButton;
    private FloatingActionButton releaseMemoryButton;
    private FloatingActionButton pureOverlayButton;
    private FloatingActionButton trustedOverlayButton;
    private MaterialButton drawerButton;
    private View batchSelectAllContainer;
    private CheckBox batchSelectAllCheckBox;
    private View drawerMenuPage;
    private View drawerGlobalSettingsPage;
    private View drawerAboutPage;
    private RecyclerView.ItemDecoration manageListSpacingDecoration;
    private boolean pureOverlayToggleInProgress = false;
    private boolean batchEditMode = false;
    private boolean updatingBatchSelectAllState = false;
    private int currentDrawerPage = DRAWER_PAGE_MENU;
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
        drawerButton = null;
        batchSelectAllContainer = null;
        batchSelectAllCheckBox = null;
        drawerMenuPage = null;
        drawerGlobalSettingsPage = null;
        drawerAboutPage = null;
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
        manageListAdapter.setBatchSelectionListener(this::onBatchSelectionChanged);
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
        setupDrawerPages(drawerLayout);
        bindDrawerActions(drawerLayout);
    }

    private void bindDrawerActions(@NonNull DrawerLayout drawerLayout) {
        bindDrawerPageAction(R.id.main_drawer_global_settings, () -> showDrawerPage(DRAWER_PAGE_GLOBAL_SETTINGS));
        bindDrawerAction(drawerLayout, R.id.main_drawer_close_all_windows, this::hideAllWindowsSafely);
        bindDrawerAction(drawerLayout, R.id.main_drawer_release_memory, this::releaseMemory);
        bindDrawerAction(drawerLayout, R.id.main_drawer_batch_edit, this::enterBatchEditMode);
        bindDrawerAction(drawerLayout, R.id.main_drawer_batch_import, this::launchBatchImportPicker);
        bindDrawerPageAction(R.id.main_drawer_about, () -> showDrawerPage(DRAWER_PAGE_ABOUT));
        bindDrawerAction(drawerLayout, R.id.main_drawer_back_to_launcher,
                () -> MainActivity.this.moveTaskToBack(true));
        bindDrawerAction(drawerLayout, R.id.main_drawer_exit,
                () -> ApplicationMethods.CloseApplication(MainActivity.this));
        bindDrawerPageAction(R.id.main_drawer_global_back, () -> showDrawerPage(DRAWER_PAGE_MENU));
        bindDrawerPageAction(R.id.main_drawer_about_back, () -> showDrawerPage(DRAWER_PAGE_MENU));
        bindDrawerAction(drawerLayout, R.id.main_drawer_about_open_source,
                () -> startActivity(new Intent(MainActivity.this, LicenseActivity.class)));
    }

    private void bindDrawerAction(@NonNull DrawerLayout drawerLayout, int viewId, @NonNull Runnable action) {
        View actionView = findViewById(viewId);
        if (actionView == null) {
            return;
        }
        actionView.setOnClickListener(view -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            drawerLayout.post(action);
        });
    }

    private void bindDrawerPageAction(int viewId, @NonNull Runnable action) {
        View actionView = findViewById(viewId);
        if (actionView == null) {
            return;
        }
        actionView.setOnClickListener(view -> action.run());
    }

    private void setupDrawerPages(@NonNull DrawerLayout drawerLayout) {
        drawerMenuPage = findViewById(R.id.main_drawer_menu_page);
        drawerGlobalSettingsPage = findViewById(R.id.main_drawer_global_page);
        drawerAboutPage = findViewById(R.id.main_drawer_about_page);
        TextView aboutVersion = findViewById(R.id.main_drawer_about_version);
        if (aboutVersion != null) {
            aboutVersion.setText(getString(R.string.application_version) + ApplicationMethods.getApplicationVersion(this));
        }
        showDrawerPage(DRAWER_PAGE_MENU, false);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                showDrawerPage(DRAWER_PAGE_MENU, false);
            }
        });
    }

    private void showDrawerPage(int drawerPage) {
        showDrawerPage(drawerPage, true);
    }

    private void showDrawerPage(int drawerPage, boolean animate) {
        if (drawerPage == currentDrawerPage
                && drawerMenuPage != null
                && drawerMenuPage.getVisibility() == View.VISIBLE) {
            return;
        }
        if (drawerPage == DRAWER_PAGE_GLOBAL_SETTINGS) {
            ensureDrawerGlobalSettingsFragment();
        }
        View targetPage = getDrawerPageView(drawerPage);
        View previousPage = getDrawerPageView(currentDrawerPage);
        currentDrawerPage = drawerPage;
        if (targetPage == null) {
            return;
        }
        if (previousPage != null && previousPage != targetPage) {
            hideDrawerPage(previousPage, animate);
        }
        showDrawerPageView(targetPage, animate);
    }

    private View getDrawerPageView(int drawerPage) {
        if (drawerPage == DRAWER_PAGE_GLOBAL_SETTINGS) {
            return drawerGlobalSettingsPage;
        }
        if (drawerPage == DRAWER_PAGE_ABOUT) {
            return drawerAboutPage;
        }
        return drawerMenuPage;
    }

    private void showDrawerPageView(@NonNull View pageView, boolean animate) {
        pageView.setVisibility(View.VISIBLE);
        pageView.bringToFront();
        if (!animate) {
            pageView.setAlpha(1f);
            pageView.setTranslationX(0f);
            return;
        }
        float offset = getResources().getDisplayMetrics().density * 16f;
        pageView.setAlpha(0f);
        pageView.setTranslationX(offset);
        pageView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(160L)
                .start();
    }

    private void hideDrawerPage(@NonNull View pageView, boolean animate) {
        if (!animate) {
            pageView.setVisibility(View.GONE);
            pageView.setAlpha(1f);
            pageView.setTranslationX(0f);
            return;
        }
        float offset = -getResources().getDisplayMetrics().density * 10f;
        pageView.animate()
                .alpha(0f)
                .translationX(offset)
                .setDuration(120L)
                .withEndAction(() -> {
                    pageView.setVisibility(View.GONE);
                    pageView.setAlpha(1f);
                    pageView.setTranslationX(0f);
                })
                .start();
    }

    private void ensureDrawerGlobalSettingsFragment() {
        if (getSupportFragmentManager().findFragmentByTag(DRAWER_GLOBAL_SETTINGS_FRAGMENT_TAG) != null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.main_drawer_global_settings_container,
                        new GlobalSettingsFragment(),
                        DRAWER_GLOBAL_SETTINGS_FRAGMENT_TAG
                )
                .commit();
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

    private void onBatchPicturesSelected(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        final ArrayList<Uri> selectedUris = new ArrayList<>(uris);
        final String defaultPictureName = getString(R.string.new_picture_name);
        new Thread(() -> {
            Context appContext = getApplicationContext();
            ArrayList<String> importedPictureIds = new ArrayList<>();
            for (Uri uri : selectedUris) {
                if (uri == null) {
                    continue;
                }
                String pictureId = ImageMethods.setNewImage(appContext, uri);
                if (pictureId == null || importedPictureIds.contains(pictureId)) {
                    continue;
                }
                initializeImportedPictureData(appContext, pictureId, defaultPictureName);
                importedPictureIds.add(pictureId);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    cleanupImportedPictures(importedPictureIds);
                    return;
                }
                if (importedPictureIds.isEmpty()) {
                    SnackShow(this, R.string.action_batch_import_failed);
                    return;
                }
                launchBatchPictureSettings(importedPictureIds, true);
            });
        }).start();
    }

    private void initializeImportedPictureData(Context context, String pictureId, String pictureName) {
        PictureData importedPictureData = new PictureData();
        importedPictureData.setDataControl(pictureId);
        float defaultZoom = ImageMethods.getDefaultZoom(context, pictureId, false);
        importedPictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
        importedPictureData.put(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        importedPictureData.put(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        importedPictureData.put(Config.DATA_PICTURE_ZOOM, defaultZoom);
        importedPictureData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, defaultZoom);
        importedPictureData.put(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        importedPictureData.put(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        importedPictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO);
        importedPictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_MASK, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK);
        importedPictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO);
        importedPictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_MASK, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK);
        importedPictureData.put(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        importedPictureData.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        importedPictureData.commit(pictureName);
    }

    private void cleanupImportedPictures(ArrayList<String> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        Context appContext = getApplicationContext();
        for (String pictureId : pictureIds) {
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

    private void launchBatchImportPicker() {
        if (!PermissionMethods.hasOverlayPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionMethods.askOverlayPermission(this, this::launchOverlayPermissionRequest);
            }
            return;
        }
        batchPicturePickerLauncher.launch("image/*");
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
            if (currentDrawerPage != DRAWER_PAGE_MENU) {
                showDrawerPage(DRAWER_PAGE_MENU);
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
