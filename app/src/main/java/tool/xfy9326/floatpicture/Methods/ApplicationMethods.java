package tool.xfy9326.floatpicture.Methods;


import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ApplicationMethods {
    private static final String PREF_DISPLAY_CACHE_VERSION = "display_cache_version";
    private static final int DISPLAY_CACHE_VERSION_PNG = 1;
    private static final int DISPLAY_CACHE_VERSION_WEBP_LOSSLESS = 3;
    private static final int DOUBLE_CLICK_SNACKBAR_DURATION_MS = 1000;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean STARTUP_MAINTENANCE_RUNNING = new AtomicBoolean(false);
    private static final String[] ORIGINAL_PICTURE_RELATED_SUFFIXES = new String[]{
            "",
            ".pending",
            ".pending.new",
            ".pending.transaction",
            ".pending.backup",
            ".backup",
            ".commit.transaction"
    };
    private static final String[] DISPLAY_CACHE_RELATED_SUFFIXES = new String[]{
            "",
            ".pending",
            ".backup",
            ".transaction"
    };
    private static volatile boolean waitDoubleClick;

    public static final class MemoryReleaseResult {
        private final int releasedWindowCount;
        private final int deletedTempFileCount;

        public MemoryReleaseResult(int releasedWindowCount, int deletedTempFileCount) {
            this.releasedWindowCount = releasedWindowCount;
            this.deletedTempFileCount = deletedTempFileCount;
        }

        public int getReleasedWindowCount() {
            return releasedWindowCount;
        }

        public int getDeletedTempFileCount() {
            return deletedTempFileCount;
        }
    }

    public interface MemoryReleaseCallback {
        void onComplete(MemoryReleaseResult result);
    }

    public static void startNotificationControl(Context context) {
        if (PermissionMethods.canStartOverlayRuntime(context)) {
            OverlayRuntimeController.startRuntime(context);
        }
    }

    public static boolean isPureOverlayModeEnabled(Context context) {
        Boolean runtimeState = OverlayRuntimeStateStore.getPureOverlayModeEnabled(context);
        if (runtimeState != null) {
            return runtimeState;
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Config.PREFERENCE_PURE_OVERLAY_MODE, false);
    }

    public static boolean setPureOverlayModeEnabled(Context context, boolean enabled) {
        boolean saved = PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(Config.PREFERENCE_PURE_OVERLAY_MODE, enabled)
                .commit();
        OverlayRuntimeStateStore.setPureOverlayModeEnabled(context, enabled);
        return saved;
    }

    private static void closeNotificationControl(Context context) {
        OverlayRuntimeController.shutdownRuntime(context);
    }

    public static String getApplicationVersion(Context mContext) {
        try {
            PackageInfo packageInfo = getPackageInfoCompat(mContext);
            return packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static PackageInfo getPackageInfoCompat(Context context) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return getLegacyPackageInfo(packageManager, packageName);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo getLegacyPackageInfo(PackageManager packageManager, String packageName)
            throws PackageManager.NameNotFoundException {
        return packageManager.getPackageInfo(packageName, 0);
    }

    public static void CloseApplication(Activity mActivity) {
        closeNotificationControl(mActivity);
        mActivity.finish();
    }

    public static void CloseMainUi(Activity mActivity) {
        mActivity.finishAndRemoveTask();
    }

    public static void CloseMainUiOrApplication(Activity mActivity) {
        if (isPureOverlayModeEnabled(mActivity)) {
            CloseMainUi(mActivity);
        } else {
            CloseApplication(mActivity);
        }
    }

    public static void disableNavigationViewScrollbars(NavigationView navigationView) {
        if (navigationView != null) {
            navigationView.setVerticalScrollBarEnabled(false);
        }
    }

    public static Snackbar makeStyledSnackbar(Activity activity, View rootView, int messageResId, int durationMs) {
        return makeStyledSnackbar(activity, rootView, activity.getString(messageResId), durationMs);
    }

    public static Snackbar makeStyledSnackbar(Activity activity, View rootView, CharSequence message, int durationMs) {
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT);
        snackbar.setDuration(durationMs);
        styleSnackbar(activity, snackbar);
        return snackbar;
    }

    private static void styleSnackbar(Activity activity, Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundResource(R.drawable.bg_snackbar_status);
        snackbarView.setBackgroundTintList(null);
        snackbarView.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            snackbarView.setElevation(dp(activity, 12));
            snackbarView.setClipToOutline(true);
        }
        ViewGroup.LayoutParams layoutParams = snackbarView.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            int horizontalMargin = dp(activity, 12);
            marginLayoutParams.setMargins(horizontalMargin, 0, horizontalMargin, dp(activity, 12));
            if (marginLayoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) marginLayoutParams).gravity = Gravity.BOTTOM;
            }
            snackbarView.setLayoutParams(marginLayoutParams);
        }
        TextView snackbarText = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (snackbarText != null) {
            snackbarText.setTextColor(ContextCompat.getColor(activity, R.color.colorPromptText));
            snackbarText.setTextSize(14);
            snackbarText.setMaxLines(3);
        }
        TextView snackbarAction = snackbarView.findViewById(com.google.android.material.R.id.snackbar_action);
        if (snackbarAction != null) {
            snackbarAction.setTextColor(ContextCompat.getColor(activity, R.color.colorPromptText));
            snackbarAction.setTextSize(14);
        }
    }

    public static void showToast(Context context, int messageResId) {
        if (context != null) {
            showToast(context, context.getString(messageResId));
        }
    }

    @SuppressWarnings("deprecation")
    public static void showToast(Context context, CharSequence message) {
        if (context == null || message == null) {
            return;
        }
        try {
            View toastView = LayoutInflater.from(context).inflate(R.layout.toast_status, null, false);
            TextView textView = toastView.findViewById(R.id.textview_toast_status);
            textView.setText(message);
            Toast toast = new Toast(context.getApplicationContext());
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            // Custom toast views are used only for foreground app feedback.
            toast.setView(toastView);
            toast.show();
        } catch (Exception ignored) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void DoubleClickCloseSnackBar(final Activity mActivity, boolean isDoubleClick) {
        if (isDoubleClick && waitDoubleClick) {
            CloseMainUiOrApplication(mActivity);
        } else {
            CoordinatorLayout coordinatorLayout = mActivity.findViewById(R.id.main_layout_content);
            int messageResId = isPureOverlayModeEnabled(mActivity)
                    ? R.string.action_warn_double_click_close_management
                    : R.string.action_warn_double_click_close_application;
            Snackbar snackbar = makeStyledSnackbar(
                    mActivity,
                    coordinatorLayout,
                    messageResId,
                    DOUBLE_CLICK_SNACKBAR_DURATION_MS
            );
            snackbar.setAction(R.string.action_back_to_launcher, v -> mActivity.moveTaskToBack(true));
            snackbar.setActionTextColor(ContextCompat.getColor(mActivity, R.color.colorPrimary));
            snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<>() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    waitDoubleClick = false;
                    super.onDismissed(transientBottomBar, event);
                }
            });
            waitDoubleClick = true;
            snackbar.show();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void ClearUselessTemp(final Context mContext) {
        Context appContext = mContext.getApplicationContext() != null ? mContext.getApplicationContext() : mContext;
        AppExecutors.io().execute(() -> clearUselessTempSync(appContext));
    }

    public static void runStartupMaintenance(final Context context) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!STARTUP_MAINTENANCE_RUNNING.compareAndSet(false, true)) {
            return;
        }
        AppExecutors.io().execute(() -> {
            try {
                clearUselessTempSync(appContext);
                migrateDisplayCacheIfNeeded(appContext);
            } finally {
                STARTUP_MAINTENANCE_RUNNING.set(false);
            }
        });
    }

    public static void releaseMemory(Context context, MemoryReleaseCallback callback) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        AtomicInteger releasedWindowCount = new AtomicInteger(0);
        AtomicInteger deletedTempFileCount = new AtomicInteger(0);
        AtomicBoolean releaseComplete = new AtomicBoolean(false);
        AtomicBoolean cleanupComplete = new AtomicBoolean(false);
        AtomicBoolean resultDelivered = new AtomicBoolean(false);

        OverlayRuntimeController.releaseMemory(appContext, count -> {
            releasedWindowCount.set(count);
            releaseComplete.set(true);
            completeMemoryReleaseIfReady(
                    releasedWindowCount,
                    deletedTempFileCount,
                    releaseComplete,
                    cleanupComplete,
                    resultDelivered,
                    callback
            );
        });
        AppExecutors.io().execute(() -> {
            int deletedCount = clearUselessTempSync(appContext);
            MAIN_HANDLER.post(() -> {
                deletedTempFileCount.set(deletedCount);
                cleanupComplete.set(true);
                completeMemoryReleaseIfReady(
                        releasedWindowCount,
                        deletedTempFileCount,
                        releaseComplete,
                        cleanupComplete,
                        resultDelivered,
                        callback
                );
            });
        });
    }

    private static void completeMemoryReleaseIfReady(AtomicInteger releasedWindowCount,
                                                     AtomicInteger deletedTempFileCount,
                                                     AtomicBoolean releaseComplete,
                                                     AtomicBoolean cleanupComplete,
                                                     AtomicBoolean resultDelivered,
                                                     MemoryReleaseCallback callback) {
        if (!releaseComplete.get() || !cleanupComplete.get() || !resultDelivered.compareAndSet(false, true)) {
            return;
        }
        callback.onComplete(new MemoryReleaseResult(releasedWindowCount.get(), deletedTempFileCount.get()));
    }

    private static int clearUselessTempSync(Context mContext) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictureList = pictureData.getListArray();
        HashSet<String> validIds = new HashSet<>();
        if (pictureList != null) {
            validIds.addAll(pictureList.keySet());
        }
        int deletedCount = recoverPictureWorkFiles(validIds);
        deletedCount += clearOrphanFiles(
                new File(Config.getOriginalPictureDir()),
                buildValidRelatedFileNames(validIds, ORIGINAL_PICTURE_RELATED_SUFFIXES)
        );
        deletedCount += clearOrphanFiles(new File(Config.getPictureDir()), validIds);
        deletedCount += clearOrphanFiles(
                new File(Config.getPictureTempDir()),
                buildValidRelatedFileNames(validIds, DISPLAY_CACHE_RELATED_SUFFIXES)
        );
        return deletedCount;
    }

    private static void migrateDisplayCacheIfNeeded(Context context) {
        int targetVersion = getTargetDisplayCacheVersion();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int cachedVersion = sharedPreferences.getInt(PREF_DISPLAY_CACHE_VERSION, DISPLAY_CACHE_VERSION_PNG);
        if (cachedVersion >= targetVersion) {
            return;
        }

        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictureList = pictureData.getListArray();
        boolean completed = false;
        int rebuiltCount = 0;
        try {
            if (pictureList != null) {
                for (String pictureId : pictureList.keySet()) {
                    if (pictureId == null || pictureId.isEmpty() || !ImageMethods.isPictureFileExist(pictureId)) {
                        continue;
                    }
                    pictureData.setDataControl(pictureId);
                    float defaultZoom = pictureData.getFloat(
                            Config.DATA_PICTURE_DEFAULT_ZOOM,
                            ImageMethods.getDefaultZoom(context, pictureId, false)
                    );
                    float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom);
                    float degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
                    float cornerRadiusRatio = pictureData.getFloat(
                            Config.DATA_PICTURE_CORNER_RADIUS_RATIO,
                            Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO
                    );
                    int cornerRadiusMask = pictureData.getInt(
                            Config.DATA_PICTURE_CORNER_RADIUS_MASK,
                            Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK
                    );
                    float edgeFeatherRatio = pictureData.getFloat(
                            Config.DATA_PICTURE_EDGE_FEATHER_RATIO,
                            Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
                    );
                    int edgeFeatherMask = pictureData.getInt(
                            Config.DATA_PICTURE_EDGE_FEATHER_MASK,
                            Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
                    );
                    Bitmap rebuiltBitmap = ImageMethods.createAndSaveDisplayBitmap(
                            pictureId,
                            zoom,
                            degree,
                            cornerRadiusRatio,
                            cornerRadiusMask,
                            edgeFeatherRatio,
                            edgeFeatherMask
                    );
                    if (rebuiltBitmap == null) {
                        Log.w("ApplicationMethods", "Failed to rebuild display cache for picture: " + pictureId);
                        continue;
                    }
                    rebuiltCount++;
                    ImageMethods.recycleBitmap(rebuiltBitmap);
                }
            }
            completed = true;
        } catch (Exception e) {
            Log.e("ApplicationMethods", "Startup display cache migration failed", e);
        }

        if (completed) {
            sharedPreferences.edit().putInt(PREF_DISPLAY_CACHE_VERSION, targetVersion).commit();
            Log.i("ApplicationMethods", "Display cache migration completed: " + rebuiltCount + " item(s)");
        }
    }

    private static int getTargetDisplayCacheVersion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return DISPLAY_CACHE_VERSION_WEBP_LOSSLESS;
        }
        return DISPLAY_CACHE_VERSION_PNG;
    }

    private static int clearOrphanFiles(File directory, Set<String> validIds) {
        if (!directory.exists()) {
            return 0;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        int deletedCount = 0;
        for (File file : files) {
            // 目录只做保护性跳过，不递归删除
            if (file.isDirectory()) {
                continue;
            }
            if (!validIds.contains(file.getName())) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }

    private static int recoverPictureWorkFiles(Set<String> validIds) {
        if (validIds == null || validIds.isEmpty()) {
            return 0;
        }
        int cleanedCount = 0;
        for (String id : validIds) {
            cleanedCount += PictureFileStore.recoverPictureWorkFiles(id);
        }
        return cleanedCount;
    }

    private static HashSet<String> buildValidRelatedFileNames(Set<String> validIds, String[] relatedSuffixes) {
        HashSet<String> validFileNames = new HashSet<>();
        if (validIds == null || validIds.isEmpty() || relatedSuffixes == null || relatedSuffixes.length == 0) {
            return validFileNames;
        }
        for (String id : validIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            for (String suffix : relatedSuffixes) {
                validFileNames.add(id + suffix);
            }
        }
        return validFileNames;
    }
}
