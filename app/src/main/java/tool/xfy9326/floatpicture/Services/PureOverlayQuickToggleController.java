package tool.xfy9326.floatpicture.Services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

public final class PureOverlayQuickToggleController {
    private static final String TAG = "PureOverlayQuickToggle";
    private static final int TOUCH_TARGET_DP = 40;
    private static final int DOT_SIZE_DP = 14;
    private static final int PREVIEW_TOUCH_TARGET_DP = TOUCH_TARGET_DP;
    private static final int PREVIEW_DOT_SIZE_DP = 18;
    private static final int DEFAULT_MARGIN_DP = 16;
    private static final long TRUSTED_ATTACH_RETRY_DELAY_MS = 250L;
    private static final int TRUSTED_ATTACH_MAX_RETRY = 4;
    private static final int POSITION_CORRECTION_MAX_ATTEMPTS = 3;
    private static final int POSITION_CORRECTION_TOLERANCE_PX = 1;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int trustedAttachRetryCount = 0;
    @Nullable
    private FrameLayout quickToggleView;
    @Nullable
    private WindowManager quickToggleWindowManager;
    @Nullable
    private FrameLayout previewView;
    @Nullable
    private WindowManager previewWindowManager;

    public PureOverlayQuickToggleController(@NonNull Context context) {
        Context applicationContext = context.getApplicationContext();
        appContext = applicationContext != null ? applicationContext : context;
    }

    public void refresh() {
        trustedAttachRetryCount = 0;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshNow();
            return;
        }
        mainHandler.post(this::refreshNow);
    }

    public void release() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseNow();
            return;
        }
        mainHandler.post(this::releaseNow);
    }

    public void showPreview(int x, int y) {
        Point clampedPosition = clampPosition(appContext, x, y);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showPreviewNow(clampedPosition.x, clampedPosition.y);
            return;
        }
        mainHandler.post(() -> showPreviewNow(clampedPosition.x, clampedPosition.y));
    }

    public void hidePreview() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hidePreviewNow();
            return;
        }
        mainHandler.post(this::hidePreviewNow);
    }

    public static boolean isEnabled(@NonNull Context context) {
        OverlayRuntimeStateStore.PureOverlayQuickToggleSettings runtimeSettings =
                OverlayRuntimeStateStore.getPureOverlayQuickToggleSettings(context);
        if (runtimeSettings != null) {
            return runtimeSettings.isEnabled();
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_ENABLED, false);
    }

    public static void saveSettings(@NonNull Context context, boolean enabled, int x, int y) {
        Point clampedPosition = clampPosition(context, x, y);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_ENABLED, enabled);
        editor.putInt(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_X, clampedPosition.x);
        editor.putInt(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_Y, clampedPosition.y);
        editor.apply();
        OverlayRuntimeStateStore.savePureOverlayQuickToggleSettings(
                context,
                enabled,
                clampedPosition.x,
                clampedPosition.y
        );
    }

    @NonNull
    public static Point resolveSavedPosition(@NonNull Context context) {
        OverlayRuntimeStateStore.PureOverlayQuickToggleSettings runtimeSettings =
                OverlayRuntimeStateStore.getPureOverlayQuickToggleSettings(context);
        if (runtimeSettings != null) {
            return clampPosition(context, runtimeSettings.getX(), runtimeSettings.getY());
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        Point maxPosition = getPositionBounds(context);
        int defaultX = Math.max(0, maxPosition.x - dp(context, DEFAULT_MARGIN_DP));
        int defaultY = Math.max(0, maxPosition.y - dp(context, DEFAULT_MARGIN_DP));
        int x = sharedPreferences.getInt(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_X, defaultX);
        int y = sharedPreferences.getInt(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_Y, defaultY);
        return clampPosition(context, x, y);
    }

    @NonNull
    public static Point getPositionBounds(@NonNull Context context) {
        Point displaySize = getDisplaySize(context);
        int touchTargetSize = dp(context, PREVIEW_TOUCH_TARGET_DP);
        return new Point(
                Math.max(0, displaySize.x - touchTargetSize),
                Math.max(0, displaySize.y - touchTargetSize)
        );
    }

    @NonNull
    public static String buildSummary(@NonNull Context context) {
        Point position = resolveSavedPosition(context);
        int summaryResId = isEnabled(context)
                ? R.string.settings_global_pure_overlay_quick_toggle_summary_enabled
                : R.string.settings_global_pure_overlay_quick_toggle_summary_disabled;
        return context.getString(summaryResId, position.x, position.y);
    }

    private void refreshNow() {
        if (!shouldShowQuickToggle(appContext)) {
            releaseQuickToggleNow();
            return;
        }
        FrameLayout toggleView = ensureQuickToggleView();
        Point position = resolveSavedPosition(appContext);
        showView(toggleView, position.x, position.y, true, TOUCH_TARGET_DP);
    }

    private void releaseNow() {
        hidePreviewNow();
        releaseQuickToggleNow();
    }

    private void releaseQuickToggleNow() {
        trustedAttachRetryCount = 0;
        if (quickToggleView == null) {
            return;
        }
        FrameLayout toggleView = quickToggleView;
        removeView(toggleView, quickToggleWindowManager);
        if (!toggleView.isAttachedToWindow()) {
            quickToggleView = null;
            quickToggleWindowManager = null;
        }
    }

    private void showPreviewNow(int x, int y) {
        if (!canCreateOverlayWindow(appContext)) {
            return;
        }
        FrameLayout currentPreviewView = ensurePreviewView();
        showView(currentPreviewView, x, y, false, PREVIEW_TOUCH_TARGET_DP);
    }

    private void hidePreviewNow() {
        if (previewView == null) {
            return;
        }
        FrameLayout currentPreviewView = previewView;
        removeView(currentPreviewView, previewWindowManager);
        if (!currentPreviewView.isAttachedToWindow()) {
            previewView = null;
            previewWindowManager = null;
        }
    }

    @NonNull
    private FrameLayout ensureQuickToggleView() {
        if (requiresViewRebuild(quickToggleView)) {
            releaseQuickToggleNow();
        }
        if (quickToggleView != null) {
            return quickToggleView;
        }
        FrameLayout container = createToggleView(true, false);
        container.setOnClickListener(v -> toggleManagedWindows());
        container.setOnLongClickListener(v -> {
            openMainActivity();
            return true;
        });
        quickToggleView = container;
        return container;
    }

    @NonNull
    private FrameLayout ensurePreviewView() {
        if (requiresViewRebuild(previewView)) {
            hidePreviewNow();
        }
        if (previewView != null) {
            return previewView;
        }
        previewView = createToggleView(false, true);
        return previewView;
    }

    @NonNull
    private FrameLayout createToggleView(boolean interactive, boolean preview) {
        Context viewContext = getViewContext();
        int touchTargetSize = dp(appContext, preview ? PREVIEW_TOUCH_TARGET_DP : TOUCH_TARGET_DP);
        int dotSize = dp(appContext, preview ? PREVIEW_DOT_SIZE_DP : DOT_SIZE_DP);
        FrameLayout container = new FrameLayout(viewContext);
        container.setClickable(interactive);
        container.setLongClickable(interactive);
        container.setFocusable(false);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        container.setLayoutParams(new FrameLayout.LayoutParams(touchTargetSize, touchTargetSize));

        View dotView = new View(viewContext);
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(Color.BLACK);
        if (preview) {
            dotDrawable.setStroke(Math.max(1, dp(appContext, 1)), 0x66FFFFFF);
        }
        dotView.setBackground(dotDrawable);
        FrameLayout.LayoutParams dotLayoutParams = new FrameLayout.LayoutParams(dotSize, dotSize);
        dotLayoutParams.gravity = Gravity.CENTER;
        container.addView(dotView, dotLayoutParams);
        return container;
    }

    @NonNull
    private WindowManager.LayoutParams buildLayoutParams(int x, int y, boolean touchable, int touchTargetSizeDp) {
        int touchTargetSize = dp(appContext, touchTargetSizeDp);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = resolveWindowType();
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        layoutParams.alpha = 1f;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.x = x;
        layoutParams.y = y;
        layoutParams.width = touchTargetSize;
        layoutParams.height = touchTargetSize;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        return layoutParams;
    }

    private int resolveWindowType() {
        if (hasActiveTrustedOverlayService()) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        @SuppressWarnings("deprecation")
        int legacyWindowType = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        return legacyWindowType;
    }

    private void showView(@NonNull FrameLayout toggleView,
                          int x,
                          int y,
                          boolean touchable,
                          int touchTargetSizeDp) {
        WindowManager windowManager = getWindowManagerForCurrentMode();
        WindowManager.LayoutParams layoutParams = buildLayoutParams(x, y, touchable, touchTargetSizeDp);
        if (toggleView.isAttachedToWindow()) {
            try {
                windowManager.updateViewLayout(toggleView, layoutParams);
                saveWindowManager(toggleView, windowManager);
                schedulePositionCorrection(toggleView, windowManager, x, y);
                return;
            } catch (Exception e) {
                Log.w(TAG, "updateViewLayout failed, recreating quick toggle: " + e.getMessage());
                removeView(toggleView, getSavedWindowManager(toggleView));
            }
        }
        if (attachView(windowManager, toggleView, layoutParams)) {
            schedulePositionCorrection(toggleView, windowManager, x, y);
        }
    }

    private boolean attachView(@NonNull WindowManager windowManager,
                               @NonNull FrameLayout toggleView,
                               @NonNull WindowManager.LayoutParams layoutParams) {
        try {
            windowManager.addView(toggleView, layoutParams);
            saveWindowManager(toggleView, windowManager);
            trustedAttachRetryCount = 0;
            return true;
        } catch (Exception ignored) {
            Log.w(TAG, "addView failed for type " + layoutParams.type + ": " + ignored.getMessage());
            scheduleTrustedAttachRetry(layoutParams.type);
            return false;
        }
    }

    private void scheduleTrustedAttachRetry(int windowType) {
        if (windowType != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                || trustedAttachRetryCount >= TRUSTED_ATTACH_MAX_RETRY
                || !shouldShowQuickToggle(appContext)) {
            return;
        }
        trustedAttachRetryCount++;
        mainHandler.postDelayed(this::refreshNow, TRUSTED_ATTACH_RETRY_DELAY_MS);
    }

    private void schedulePositionCorrection(@NonNull FrameLayout view,
                                            @NonNull WindowManager windowManager,
                                            int targetX,
                                            int targetY) {
        view.post(() -> correctPositionIfNeeded(view, windowManager, targetX, targetY, 0));
    }

    private void correctPositionIfNeeded(@NonNull FrameLayout view,
                                         @NonNull WindowManager fallbackWindowManager,
                                         int targetX,
                                         int targetY,
                                         int attempt) {
        if (!view.isAttachedToWindow()) {
            return;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int offsetX = targetX - location[0];
        int offsetY = targetY - location[1];
        if (Math.abs(offsetX) <= POSITION_CORRECTION_TOLERANCE_PX
                && Math.abs(offsetY) <= POSITION_CORRECTION_TOLERANCE_PX) {
            return;
        }
        WindowManager.LayoutParams layoutParams = getCurrentLayoutParams(view);
        if (layoutParams == null) {
            return;
        }
        layoutParams.x += offsetX;
        layoutParams.y += offsetY;
        WindowManager windowManager = getSavedWindowManager(view);
        if (windowManager == null) {
            windowManager = fallbackWindowManager;
        }
        try {
            windowManager.updateViewLayout(view, layoutParams);
            saveWindowManager(view, windowManager);
        } catch (Exception e) {
            Log.w(TAG, "position correction failed: " + e.getMessage());
            return;
        }
        if (attempt + 1 < POSITION_CORRECTION_MAX_ATTEMPTS) {
            WindowManager nextWindowManager = windowManager;
            view.post(() -> correctPositionIfNeeded(view, nextWindowManager, targetX, targetY, attempt + 1));
        }
    }

    private void removeView(@NonNull FrameLayout view, @Nullable WindowManager windowManager) {
        if (!view.isAttachedToWindow()) {
            return;
        }
        if (windowManager != null) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (Exception e) {
                Log.w(TAG, "removeViewImmediate failed: " + e.getMessage());
            }
        }
        if (view.isAttachedToWindow()) {
            WindowsMethods.removeWindowIfAttached(view);
        }
    }

    @Nullable
    private WindowManager getSavedWindowManager(@NonNull FrameLayout view) {
        if (view == quickToggleView) {
            return quickToggleWindowManager;
        }
        if (view == previewView) {
            return previewWindowManager;
        }
        return null;
    }

    private void saveWindowManager(@NonNull FrameLayout view, @NonNull WindowManager windowManager) {
        if (view == quickToggleView) {
            quickToggleWindowManager = windowManager;
            return;
        }
        if (view == previewView) {
            previewWindowManager = windowManager;
        }
    }

    @Nullable
    private WindowManager.LayoutParams getCurrentLayoutParams(@NonNull View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof WindowManager.LayoutParams windowLayoutParams) {
            return windowLayoutParams;
        }
        return null;
    }

    private void toggleManagedWindows() {
        if (!shouldShowQuickToggle(appContext)) {
            releaseQuickToggleNow();
            return;
        }
        java.util.Set<String> targetIds = OverlayRuntimeStateStore.getPureOverlayManagedPictureIds(appContext);
        if (targetIds.isEmpty()) {
            return;
        }
        boolean visible = !ManageMethods.hasVisibleWindowsConfigured(appContext, targetIds);
        ManageMethods.setWindowsVisible(appContext, targetIds, visible);
    }

    private void openMainActivity() {
        Intent intent = new Intent(appContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        appContext.startActivity(intent);
    }

    private static boolean shouldShowQuickToggle(@NonNull Context context) {
        return canCreateOverlayWindow(context)
                && ApplicationMethods.isPureOverlayModeEnabled(context)
                && isEnabled(context)
                && !OverlayRuntimeStateStore.getPureOverlayManagedPictureIds(context).isEmpty();
    }

    private static boolean canCreateOverlayWindow(@NonNull Context context) {
        return PermissionMethods.hasOverlayPermission(context) || hasActiveTrustedOverlayService();
    }

    private boolean requiresViewRebuild(@Nullable FrameLayout view) {
        if (view == null) {
            return false;
        }
        boolean trustedContextExpected = shouldUseTrustedContext();
        boolean trustedContextActual = view.getContext() instanceof TrustedOverlayAccessibilityService;
        return trustedContextExpected != trustedContextActual;
    }

    @NonNull
    private Context getViewContext() {
        TrustedOverlayAccessibilityService service = TrustedOverlayAccessibilityService.getInstance();
        if (service != null) {
            return service;
        }
        return appContext;
    }

    private boolean shouldUseTrustedContext() {
        return hasActiveTrustedOverlayService();
    }

    @NonNull
    private WindowManager getWindowManagerForCurrentMode() {
        Context windowContext = getViewContext();
        return (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
    }

    private static boolean hasActiveTrustedOverlayService() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && TrustedOverlayAccessibilityService.getInstance() != null;
    }

    @NonNull
    private static Point clampPosition(@NonNull Context context, int x, int y) {
        Point maxPosition = getPositionBounds(context);
        int clampedX = Math.max(0, Math.min(x, maxPosition.x));
        int clampedY = Math.max(0, Math.min(y, maxPosition.y));
        return new Point(clampedX, clampedY);
    }

    @NonNull
    private static Point getDisplaySize(@NonNull Context context) {
        WindowManager windowManager = WindowsMethods.getWindowManager(context);
        if (windowManager != null) {
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            @SuppressWarnings("deprecation")
            android.view.Display display = windowManager.getDefaultDisplay();
            if (display != null) {
                display.getRealMetrics(displayMetrics);
                return new Point(displayMetrics.widthPixels, displayMetrics.heightPixels);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
                return new Point(bounds.width(), bounds.height());
            }
        }
        android.util.DisplayMetrics fallbackMetrics = context.getResources().getDisplayMetrics();
        return new Point(fallbackMetrics.widthPixels, fallbackMetrics.heightPixels);
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
