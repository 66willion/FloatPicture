package tool.xfy9326.floatpicture.Activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.result.ActivityResult;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
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

public class MainActivity extends AppCompatActivity {
    private static final int MAIN_LIST_VIEW_CACHE_SIZE = 2;
    private static final long PURE_OVERLAY_BUTTON_REENABLE_DELAY_MS = 300L;

    private ManageListAdapter manageListAdapter;
    private AdvancedRecyclerView recyclerView;
    private FloatingActionButton pureOverlayButton;
    private FloatingActionButton trustedOverlayButton;
    private boolean pureOverlayToggleInProgress = false;
    private long BackClickTime;
    private ActivityResultLauncher<String> picturePickerLauncher;
    private ActivityResultLauncher<Intent> addPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> editPictureSettingsLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> trustedOverlaySettingsLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private final Runnable trustedOverlayStateUpdater = this::updateTrustedOverlayButtonState;
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
        View anchorView = mActivity.findViewById(R.id.main_layout_actions);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, mActivity.getString(resourceId), Snackbar.LENGTH_SHORT);
        if (anchorView != null) {
            snackbar.setAnchorView(anchorView);
        }
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
        ApplicationMethods.ClearUselessTemp(this);
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
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        View actionsLayout = findViewById(R.id.main_layout_actions);
        actionsLayout.bringToFront();
        actionsLayout.setTranslationZ(16f);

        manageListAdapter = new ManageListAdapter(this, this::launchPictureSettingsForEdit);
        ((MainApplication) getApplicationContext()).setManageListAdapter(manageListAdapter);
        recyclerView = findViewById(R.id.main_list_manage);
        recyclerView.setAdapter(manageListAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setItemViewCacheSize(MAIN_LIST_VIEW_CACHE_SIZE);
        recyclerView.setEmptyView(findViewById(R.id.layout_widget_empty_view));

        FloatingActionButton floatingActionButton = findViewById(R.id.main_button_add);
        floatingActionButton.setOnClickListener(view -> {
            if (PermissionMethods.hasOverlayPermission(MainActivity.this)) {
                picturePickerLauncher.launch("image/*");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionMethods.askOverlayPermission(MainActivity.this, this::launchOverlayPermissionRequest);
            }
        });

        pureOverlayButton = findViewById(R.id.main_button_pure_overlay);
        pureOverlayButton.bringToFront();
        pureOverlayButton.setOnClickListener(view -> togglePureOverlayMode());
        updatePureOverlayButtonState();

        trustedOverlayButton = findViewById(R.id.main_button_trusted_overlay);
        trustedOverlayButton.bringToFront();
        trustedOverlayButton.setOnClickListener(view ->
                trustedOverlaySettingsLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        refreshTrustedOverlayButtonState();

        FloatingActionButton releaseMemoryButton = findViewById(R.id.main_button_release_memory);
        releaseMemoryButton.bringToFront();
        releaseMemoryButton.setOnClickListener(view -> {
            view.setEnabled(false);
            trimRecyclerPreviewCache();
            new Thread(() -> {
                ApplicationMethods.MemoryReleaseResult result = ApplicationMethods.releaseMemory(getApplicationContext());
                runOnUiThread(() -> {
                    view.setEnabled(true);
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    CoordinatorLayout coordinatorLayout = findViewById(R.id.main_layout_content);
                    View anchorView = findViewById(R.id.main_layout_actions);
                    Snackbar snackbar = Snackbar.make(
                            coordinatorLayout,
                            getString(
                                    R.string.action_release_memory_result,
                                    result.getReleasedWindowCount(),
                                    result.getDeletedTempFileCount()
                            ),
                            Snackbar.LENGTH_SHORT
                    );
                    if (anchorView != null) {
                        snackbar.setAnchorView(anchorView);
                    }
                    snackbar.show();
                });
            }).start();
        });

        final DrawerLayout drawerLayout = findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();

        NavigationView navigationView = findViewById(R.id.main_navigation_view);
        ApplicationMethods.disableNavigationViewScrollbars(navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            int itemId = item.getItemId();
            if (itemId == R.id.menu_global_settings) {
                startActivity(new Intent(MainActivity.this, GlobalSettingsActivity.class));
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
            @SuppressLint("NotifyDataSetChanged")
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

    @SuppressLint("NotifyDataSetChanged")
    private void onAddPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }
        int previousItemCount = manageListAdapter.getItemCount();
        manageListAdapter.updateData();
        int currentItemCount = manageListAdapter.getItemCount();
        if (currentItemCount <= 0) {
            return;
        }
        if (previousItemCount == 0) {
            manageListAdapter.notifyDataSetChanged();
        } else if (currentItemCount > previousItemCount) {
            manageListAdapter.notifyItemInserted(currentItemCount - 1);
        } else {
            manageListAdapter.notifyDataSetChanged();
        }
        SnackShow(this, R.string.action_add_window);
        OverlayRuntimeController.refreshNotification(this);
    }

    private void onEditPictureSettingsResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        int position = result.getData().getIntExtra(Config.INTENT_PICTURE_EDIT_POSITION, -1);
        manageListAdapter.updateData();
        if (position >= 0) {
            manageListAdapter.notifyItemChanged(position);
        } else {
            manageListAdapter.notifyDataSetChanged();
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

    private void trimRecyclerPreviewCache() {
        if (recyclerView == null) {
            return;
        }
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

    @SuppressLint("NotifyDataSetChanged")
    private void refreshManageListData() {
        if (manageListAdapter == null) {
            return;
        }
        manageListAdapter.updateData();
        manageListAdapter.notifyDataSetChanged();
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
}
