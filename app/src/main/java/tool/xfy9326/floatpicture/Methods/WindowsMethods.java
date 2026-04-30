package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Services.TrustedOverlayAccessibilityService;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;

public class WindowsMethods {
    private static final float ALPHA_SAFETY_MARGIN = 0.01f;
    private static final float ALPHA_BUDGET_EPSILON = 0.000001f;
    private static final double BUDGET_COMPARISON_EPSILON = 1e-9;

    public static WindowManager getWindowManager(Context context) {
        Context windowContext = TrustedOverlayAccessibilityService.getWindowContext(getSafeContext(context));
        return (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean createWindow(WindowManager windowManager, View pictureView, boolean touchable, boolean overLayout, float pictureAlpha, int layoutPositionX, int layoutPositionY) {
        return createWindow(windowManager, pictureView, touchable, overLayout, pictureAlpha, layoutPositionX, layoutPositionY, true);
    }

    public static boolean createWindow(WindowManager windowManager, View pictureView, boolean touchable, boolean overLayout, float pictureAlpha, int layoutPositionX, int layoutPositionY, boolean syncAfterCreate) {
        Context safeContext = getSafeContext(pictureView.getContext());
        WindowManager activeWindowManager = getWindowManager(safeContext);
        WindowManager.LayoutParams layoutParams = getLayoutWithPerWindowAlpha(
                safeContext,
                layoutPositionX,
                layoutPositionY,
                touchable,
                overLayout,
                pictureAlpha,
                getSingleWindowSafeAlpha(safeContext)
        );
        if (pictureView.isAttachedToWindow()) {
            WindowManager.LayoutParams currentLayoutParams = getCurrentLayoutParams(pictureView);
            if (currentLayoutParams != null && canUpdateWindowInPlace(currentLayoutParams, layoutParams)) {
                try {
                    activeWindowManager.updateViewLayout(pictureView, layoutParams);
                    syncAttachedWindowManager(pictureView, activeWindowManager);
                    syncLayoutAlpha(pictureView, layoutParams);
                    if (syncAfterCreate) {
                        syncAllWindows(safeContext);
                    }
                    return true;
                } catch (Exception e) {
                    Log.w("WindowsMethods", "createWindow updateViewLayout failed: " + e.getMessage());
                }
            }
            if (!detachWindowIfAttached(activeWindowManager, pictureView)) {
                Log.w("WindowsMethods", "createWindow failed to detach existing window before recreation");
                return false;
            }
        }
        if (!tryAddWindow(activeWindowManager, pictureView, layoutParams)) {
            WindowManager.LayoutParams fallbackLayoutParams = getLayoutWithPerWindowAlpha(
                    safeContext,
                    layoutPositionX,
                    layoutPositionY,
                    touchable,
                    overLayout,
                    pictureAlpha,
                    getSingleWindowSafeAlpha(safeContext),
                    getFallbackWindowType()
            );
            if (!tryAddWindow(activeWindowManager, pictureView, fallbackLayoutParams)) {
                return false;
            }
            layoutParams = fallbackLayoutParams;
        }
        syncLayoutAlpha(pictureView, layoutParams);
        if (syncAfterCreate) {
            syncAllWindows(safeContext);
        }
        return true;
    }

    public static WindowManager.LayoutParams getDefaultLayout(Context context, int layoutPositionX, int layoutPositionY, boolean touchable, boolean overLayout, float pictureAlpha) {
        Context safeContext = getSafeContext(context);
        return getLayoutWithPerWindowAlpha(safeContext, layoutPositionX, layoutPositionY, touchable, overLayout, pictureAlpha, getSingleWindowSafeAlpha(safeContext));
    }

    private static WindowManager.LayoutParams getLayoutWithPerWindowAlpha(Context context, int layoutPositionX, int layoutPositionY, boolean touchable, boolean overLayout, float pictureAlpha, float perWindowMaxAlpha) {
        return getLayoutWithPerWindowAlpha(context, layoutPositionX, layoutPositionY, touchable, overLayout, pictureAlpha, perWindowMaxAlpha, resolveWindowType(context));
    }

    private static WindowManager.LayoutParams getLayoutWithPerWindowAlpha(Context context, int layoutPositionX, int layoutPositionY, boolean touchable, boolean overLayout, float pictureAlpha, float perWindowMaxAlpha, int windowType) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = windowType;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (overLayout) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        } else {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        if (!touchable) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        float layoutAlpha = clampAlpha(pictureAlpha);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !touchable && !isTrustedOverlayType(layoutParams.type)) {
            layoutAlpha = Math.min(layoutAlpha, perWindowMaxAlpha);
        }
        layoutParams.alpha = layoutAlpha;
        layoutParams.x = layoutPositionX;
        layoutParams.y = layoutPositionY;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        return layoutParams;
    }

    private static int resolveWindowType(Context context) {
        if (shouldUseTrustedOverlay(context)) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return getLegacyWindowType();
    }

    @SuppressWarnings("deprecation")
    private static int getLegacyWindowType() {
        return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private static int getFallbackWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return getLegacyWindowType();
    }

    public static void updateWindow(WindowManager windowManager, FloatImageView pictureView, boolean touchable, boolean overLayout, float pictureAlpha, int layoutPositionX, int layoutPositionY) {
        Context safeContext = getSafeContext(pictureView.getContext());
        WindowManager activeWindowManager = getWindowManager(safeContext);
        pictureView.setPictureAlpha(pictureAlpha);
        WindowManager.LayoutParams layoutParams = getLayoutWithPerWindowAlpha(
                safeContext,
                layoutPositionX,
                layoutPositionY,
                touchable,
                overLayout,
                pictureAlpha,
                getSingleWindowSafeAlpha(safeContext)
        );
        WindowManager.LayoutParams currentLayoutParams = getCurrentLayoutParams(pictureView);
        if (!pictureView.isAttachedToWindow() || currentLayoutParams == null || !canUpdateWindowInPlace(currentLayoutParams, layoutParams)) {
            createWindow(activeWindowManager, pictureView, touchable, overLayout, pictureAlpha, layoutPositionX, layoutPositionY);
            return;
        }
        try {
            activeWindowManager.updateViewLayout(pictureView, layoutParams);
            syncAttachedWindowManager(pictureView, activeWindowManager);
        } catch (Exception e) {
            Log.w("WindowsMethods", "updateWindow updateViewLayout failed: " + e.getMessage());
            createWindow(activeWindowManager, pictureView, touchable, overLayout, pictureAlpha, layoutPositionX, layoutPositionY);
            return;
        }
        syncLayoutAlpha(pictureView, layoutParams);
        syncAllWindows(safeContext);
    }

    public static void updateWindow(WindowManager windowManager, FloatImageView pictureView, Bitmap bitmap, boolean touchable, boolean overLayout, float pictureAlpha, float zoom, float degree, int layoutPositionX, int layoutPositionY) {
        Bitmap resizedBitmap = ImageMethods.resizeBitmap(bitmap, zoom, degree);
        if (resizedBitmap != null) {
            ImageMethods.setPictureBitmap(pictureView, resizedBitmap);
        }
        updateWindow(windowManager, pictureView, touchable, overLayout, pictureAlpha, layoutPositionX, layoutPositionY);
    }

    public static void syncAllWindows(Context context) {
        Map<String, View> register = ((MainApplication) context.getApplicationContext()).getRegisteredViewsSnapshot();
        if (register.isEmpty()) {
            return;
        }

        ArrayList<WindowSnapshot> visibleWindows = collectVisibleWindows(register);
        if (visibleWindows.isEmpty()) {
            return;
        }

        float[] resolvedAlphas = resolveWindowAlphas(context, visibleWindows);
        WindowManager windowManager = getWindowManager(context);
        for (int index = 0; index < visibleWindows.size(); index++) {
            WindowSnapshot window = visibleWindows.get(index);
            float newAlpha = resolvedAlphas[index];
            if (Float.compare(window.layoutParams.alpha, newAlpha) == 0) {
                syncLayoutAlpha(window.view, window.layoutParams);
                continue;
            }
            window.layoutParams.alpha = newAlpha;
            try {
                windowManager.updateViewLayout(window.view, window.layoutParams);
                syncAttachedWindowManager(window.view, windowManager);
                syncLayoutAlpha(window.view, window.layoutParams);
            } catch (Exception e) {
                Log.w("WindowsMethods", "syncAllWindows updateViewLayout failed: " + e.getMessage());
            }
        }
    }

    private static ArrayList<WindowSnapshot> collectVisibleWindows(Map<String, View> register) {
        ArrayList<WindowSnapshot> visibleWindows = new ArrayList<>(register.size());
        PictureData pictureData = new PictureData();
        for (Map.Entry<String, View> entry : register.entrySet()) {
            if (!(entry.getValue() instanceof FloatImageView floatImageView) || !floatImageView.isAttachedToWindow() || floatImageView.getVisibility() != View.VISIBLE) {
                continue;
            }
            pictureData.setDataControl(entry.getKey());
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                continue;
            }
            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) floatImageView.getLayoutParams();
            if (layoutParams == null) {
                continue;
            }
            Rect bounds = getWindowBounds(floatImageView, layoutParams);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                continue;
            }
            visibleWindows.add(new WindowSnapshot(
                    floatImageView,
                    layoutParams,
                    bounds,
                    clampAlpha(floatImageView.getPictureAlpha()),
                    isPassThroughWindow(layoutParams),
                    isTrustedOverlayType(layoutParams.type)
            ));
        }
        return visibleWindows;
    }

    private static float[] resolveWindowAlphas(Context context, ArrayList<WindowSnapshot> visibleWindows) {
        float[] resolvedAlphas = new float[visibleWindows.size()];
        for (int index = 0; index < visibleWindows.size(); index++) {
            resolvedAlphas[index] = visibleWindows.get(index).desiredAlpha;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return resolvedAlphas;
        }

        ArrayList<Integer> passThroughIndices = new ArrayList<>();
        ArrayList<WindowSnapshot> passThroughWindows = new ArrayList<>();
        for (int index = 0; index < visibleWindows.size(); index++) {
            WindowSnapshot window = visibleWindows.get(index);
            if (window.passThroughWindow && !window.trustedOverlayWindow) {
                passThroughIndices.add(index);
                passThroughWindows.add(window);
            }
        }
        if (passThroughWindows.isEmpty()) {
            return resolvedAlphas;
        }

        float targetAlpha = getSingleWindowSafeAlpha(context);
        double maxCombinedBudget = alphaToObscuringBudget(targetAlpha);
        double[] requestedBudgets = new double[passThroughWindows.size()];
        double[] scaleFactors = new double[passThroughWindows.size()];
        Arrays.fill(scaleFactors, 1.0d);

        for (int index = 0; index < passThroughWindows.size(); index++) {
            requestedBudgets[index] = alphaToObscuringBudget(passThroughWindows.get(index).desiredAlpha);
        }

        for (BitSet mask : collectCoverageMasks(passThroughWindows)) {
            double totalBudget = 0.0d;
            for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1)) {
                totalBudget += requestedBudgets[bit];
            }
            if (totalBudget <= maxCombinedBudget + BUDGET_COMPARISON_EPSILON) {
                continue;
            }
            double scale = maxCombinedBudget / totalBudget;
            for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1)) {
                scaleFactors[bit] = Math.min(scaleFactors[bit], scale);
            }
        }

        for (int index = 0; index < passThroughWindows.size(); index++) {
            int originalIndex = passThroughIndices.get(index);
            float resolvedAlpha = obscuringBudgetToAlpha(requestedBudgets[index] * scaleFactors[index]);
            resolvedAlphas[originalIndex] = Math.min(passThroughWindows.get(index).desiredAlpha, resolvedAlpha);
        }
        return resolvedAlphas;
    }

    private static Set<BitSet> collectCoverageMasks(ArrayList<WindowSnapshot> windows) {
        Set<BitSet> masks = new LinkedHashSet<>();
        if (windows.isEmpty()) {
            return masks;
        }

        TreeSet<Integer> xEdges = new TreeSet<>();
        TreeSet<Integer> yEdges = new TreeSet<>();
        for (WindowSnapshot window : windows) {
            xEdges.add(window.bounds.left);
            xEdges.add(window.bounds.right);
            yEdges.add(window.bounds.top);
            yEdges.add(window.bounds.bottom);
        }

        Integer[] xs = xEdges.toArray(new Integer[0]);
        Integer[] ys = yEdges.toArray(new Integer[0]);
        if (xs.length < 2 || ys.length < 2) {
            return masks;
        }

        for (int xIndex = 0; xIndex < xs.length - 1; xIndex++) {
            int left = xs[xIndex];
            int right = xs[xIndex + 1];
            if (right <= left) {
                continue;
            }
            double sampleX = left + ((right - left) / 2.0d);
            for (int yIndex = 0; yIndex < ys.length - 1; yIndex++) {
                int top = ys[yIndex];
                int bottom = ys[yIndex + 1];
                if (bottom <= top) {
                    continue;
                }
                double sampleY = top + ((bottom - top) / 2.0d);
                BitSet mask = new BitSet(windows.size());
                for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
                    if (containsPoint(windows.get(windowIndex).bounds, sampleX, sampleY)) {
                        mask.set(windowIndex);
                    }
                }
                if (mask.nextSetBit(0) >= 0) {
                    masks.add(mask);
                }
            }
        }
        return masks;
    }

    private static boolean containsPoint(Rect bounds, double x, double y) {
        return x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom;
    }

    private static Rect getWindowBounds(FloatImageView view, WindowManager.LayoutParams layoutParams) {
        int width = resolveViewSize(view.getWidth(), view.getMeasuredWidth(), view.getDrawable(), true);
        int height = resolveViewSize(view.getHeight(), view.getMeasuredHeight(), view.getDrawable(), false);
        int left = layoutParams.x;
        int top = layoutParams.y;
        if (view.isAttachedToWindow()) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            left = location[0];
            top = location[1];
        }
        return new Rect(left, top, left + width, top + height);
    }

    private static int resolveViewSize(int actualSize, int measuredSize, Drawable drawable, boolean width) {
        if (actualSize > 0) {
            return actualSize;
        }
        if (measuredSize > 0) {
            return measuredSize;
        }
        if (drawable != null) {
            int intrinsicSize = width ? drawable.getIntrinsicWidth() : drawable.getIntrinsicHeight();
            if (intrinsicSize > 0) {
                return intrinsicSize;
            }
        }
        return 0;
    }

    private static boolean isPassThroughWindow(WindowManager.LayoutParams layoutParams) {
        return (layoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0;
    }

    private static Context getSafeContext(Context context) {
        if (context == null) {
            return MainApplication.getAppContext();
        }
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }

    private static WindowManager.LayoutParams getCurrentLayoutParams(View pictureView) {
        ViewGroup.LayoutParams layoutParams = pictureView.getLayoutParams();
        if (layoutParams instanceof WindowManager.LayoutParams windowLayoutParams) {
            return windowLayoutParams;
        }
        return null;
    }

    private static boolean canUpdateWindowInPlace(WindowManager.LayoutParams currentLayoutParams, WindowManager.LayoutParams desiredLayoutParams) {
        return currentLayoutParams.type == desiredLayoutParams.type;
    }

    public static boolean removeWindowIfAttached(View pictureView) {
        if (pictureView == null || !pictureView.isAttachedToWindow()) {
            syncAttachedWindowManager(pictureView, null);
            return true;
        }
        WindowManager attachedWindowManager = getAttachedWindowManager(pictureView);
        if (tryRemoveWindow(attachedWindowManager, pictureView, true, "attached-immediate")) {
            return true;
        }
        WindowManager originalWindowManager = getContextWindowManager(pictureView.getContext());
        if (tryRemoveWindow(originalWindowManager, pictureView, false, "original")) {
            return true;
        }
        WindowManager activeWindowManager = getWindowManager(pictureView.getContext());
        if (activeWindowManager != originalWindowManager && tryRemoveWindow(activeWindowManager, pictureView, false, "active")) {
            return true;
        }
        if (tryRemoveWindow(originalWindowManager, pictureView, true, "original-immediate")) {
            return true;
        }
        if (activeWindowManager != originalWindowManager && tryRemoveWindow(activeWindowManager, pictureView, true, "active-immediate")) {
            return true;
        }
        return !pictureView.isAttachedToWindow();
    }

    private static boolean detachWindowIfAttached(WindowManager windowManager, View pictureView) {
        return removeWindowIfAttached(pictureView);
    }

    private static boolean tryAddWindow(WindowManager windowManager, View pictureView, WindowManager.LayoutParams layoutParams) {
        try {
            windowManager.addView(pictureView, layoutParams);
            syncAttachedWindowManager(pictureView, windowManager);
            return true;
        } catch (Exception e) {
            Log.w("WindowsMethods", "tryAddWindow addView failed: " + e.getMessage());
            return false;
        }
    }

    private static WindowManager getContextWindowManager(Context context) {
        Context safeContext = getSafeContext(context);
        return safeContext != null ? (WindowManager) safeContext.getSystemService(Context.WINDOW_SERVICE) : null;
    }

    private static boolean tryRemoveWindow(WindowManager windowManager, View pictureView, boolean immediate, String source) {
        if (pictureView == null) {
            return true;
        }
        if (!pictureView.isAttachedToWindow()) {
            syncAttachedWindowManager(pictureView, null);
            return true;
        }
        if (windowManager == null) {
            return false;
        }
        try {
            if (immediate) {
                windowManager.removeViewImmediate(pictureView);
            } else {
                windowManager.removeView(pictureView);
            }
        } catch (Exception e) {
            Log.w("WindowsMethods", "tryRemoveWindow " + source + " failed: " + e.getMessage());
        }
        boolean detached = !pictureView.isAttachedToWindow();
        if (detached) {
            syncAttachedWindowManager(pictureView, null);
        }
        return detached;
    }

    private static boolean shouldUseTrustedOverlay(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && TrustedOverlayAccessibilityService.isAuthorized(context)
                && TrustedOverlayAccessibilityService.getInstance() != null;
    }

    private static boolean isTrustedOverlayType(int windowType) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    }

    private static float getSingleWindowSafeAlpha(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return 1.0f;
        }
        return clampAlpha(getSafeWindowsAlpha(context) - ALPHA_SAFETY_MARGIN);
    }

    private static float getSafeWindowsAlpha(Context context) {
        return ((MainApplication) context.getApplicationContext()).getSafeWindowsAlpha();
    }

    private static void syncLayoutAlpha(View view, WindowManager.LayoutParams lp) {
        if (view instanceof FloatImageView fiv) {
            fiv.setLayoutAlpha(lp.alpha);
        }
    }

    private static void syncAttachedWindowManager(View view, WindowManager windowManager) {
        if (view instanceof FloatImageView fiv) {
            fiv.setAttachedWindowManager(windowManager);
        }
    }

    private static WindowManager getAttachedWindowManager(View view) {
        if (view instanceof FloatImageView fiv) {
            return fiv.getAttachedWindowManager();
        }
        return null;
    }

    private static double alphaToObscuringBudget(float alpha) {
        float boundedAlpha = Math.max(0.0f, Math.min(1.0f - ALPHA_BUDGET_EPSILON, alpha));
        return -Math.log1p(-boundedAlpha);
    }

    private static float obscuringBudgetToAlpha(double obscuringBudget) {
        if (obscuringBudget <= 0.0d) {
            return 0.0f;
        }
        return clampAlpha((float) (1.0d - Math.exp(-obscuringBudget)));
    }

    private static float clampAlpha(float alpha) {
        return Math.max(0f, Math.min(1f, alpha));
    }

    private static final class WindowSnapshot {
        private final FloatImageView view;
        private final WindowManager.LayoutParams layoutParams;
        private final Rect bounds;
        private final float desiredAlpha;
        private final boolean passThroughWindow;
        private final boolean trustedOverlayWindow;

        private WindowSnapshot(FloatImageView view, WindowManager.LayoutParams layoutParams, Rect bounds, float desiredAlpha, boolean passThroughWindow, boolean trustedOverlayWindow) {
            this.view = view;
            this.layoutParams = layoutParams;
            this.bounds = bounds;
            this.desiredAlpha = desiredAlpha;
            this.passThroughWindow = passThroughWindow;
            this.trustedOverlayWindow = trustedOverlayWindow;
        }
    }
}
