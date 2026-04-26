package tool.xfy9326.floatpicture.Methods;


import static tool.xfy9326.floatpicture.Methods.WindowsMethods.getWindowManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Services.NotificationService;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;


public class ManageMethods {
    private static final Random RANDOM = new Random();
    private static final ArrayList<String> RANDOM_BAG = new ArrayList<>();
    private static final LinkedHashSet<String> RANDOM_BAG_SNAPSHOT = new LinkedHashSet<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static String lastRandomPictureId = null;

    public static void RunWin(Context mContext) {
        if (!PermissionMethods.hasOverlayPermission(mContext)) {
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> list = pictureData.getListArray();
        cleanupRegisteredWindows(mContext, list);
        WindowManager windowManager = getWindowManager(mContext);
        boolean anyVisible = false;
        if (list != null && !list.isEmpty()) {
            for (LinkedHashMap.Entry<?, ?> entry : list.entrySet()) {
                String id = entry.getKey().toString();
                pictureData.setDataControl(id);
                boolean visible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
                anyVisible = anyVisible || visible;
                if (visible) {
                    StartWin(mContext, windowManager, pictureData, id);
                }
            }
            if (anyVisible) {
                WindowsMethods.syncAllWindows(mContext);
            }
            ((MainApplication) mContext.getApplicationContext()).setWinVisible(anyVisible);
            if (getWindowCount() > 0) {
                NotificationService.refresh(mContext);
            }
        } else {
            ((MainApplication) mContext.getApplicationContext()).setWinVisible(false);
        }
    }

    private static void StartWin(Context mContext, WindowManager windowManager, PictureData pictureData, String id) {
        FloatImageView floatImageView = ensureWindowView(mContext, pictureData, id);
        int position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        float picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        syncWindowViewState(floatImageView, touch_and_move, over_layout, picture_alpha);
        if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
            WindowsMethods.createWindow(windowManager, floatImageView, touch_and_move, over_layout, picture_alpha, position_x, position_y);
        }
    }

    public static boolean DeleteWin(Context mContext, String id) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        if (!releaseWindowById(mContext, id, true)) {
            return false;
        }
        OverlayRuntimeStateStore.removePureOverlayManagedPictureId(mContext, id);
        pictureData.remove();
        ImageMethods.clearAllTemp(mContext, id);
        updateGlobalVisibleState(mContext);
        NotificationService.refresh(mContext);
        return true;
    }

    public static void CloseAllWindows(Context mContext) {
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        Map<String, View> hashMap = mainApplication.getRegisteredViewsSnapshot();
        if (!hashMap.isEmpty()) {
            for (Map.Entry<String, View> entry : hashMap.entrySet()) {
                String id = entry.getKey();
                if (entry.getValue() instanceof FloatImageView) {
                    releaseWindowById(mContext, id, false);
                } else {
                    mainApplication.unregisterView(id);
                }
            }
        }
        WindowsMethods.syncAllWindows(mContext);
        mainApplication.setWinVisible(false);
    }

    public static int releaseInactiveWindowMemory(Context context) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        Map<String, View> registeredViews = mainApplication.getRegisteredViewsSnapshot();
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictureList = pictureData.getListArray();
        int releasedCount = 0;
        for (Map.Entry<String, View> entry : registeredViews.entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof FloatImageView floatImageView)) {
                if (mainApplication.unregisterView(id)) {
                    releasedCount++;
                }
                continue;
            }
            if (pictureList == null || !pictureList.containsKey(id)) {
                if (releaseWindowById(context, id, false)) {
                    releasedCount++;
                }
                continue;
            }
            pictureData.setDataControl(id);
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                if (releaseWindowById(context, id, false)) {
                    releasedCount++;
                }
                continue;
            }
            if (floatImageView.isAttachedToWindow()) {
                continue;
            }
            ImageMethods.releasePictureView(floatImageView);
            if (mainApplication.unregisterView(id)) {
                releasedCount++;
            }
        }
        if (releasedCount > 0) {
            WindowsMethods.syncAllWindows(context);
            updateGlobalVisibleState(context);
            NotificationService.refresh(context);
        }
        return releasedCount;
    }

    public static void updateNotificationCount(Context context) {
        NotificationService.refresh(context);
    }

    public static void recreateVisibleWindows(Context context) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        cleanupRegisteredWindows(context, linkedHashMap);
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return;
        }

        WindowManager windowManager = getWindowManager(context);
        boolean anyVisible = false;
        for (Map.Entry<?, ?> entry : linkedHashMap.entrySet()) {
            String id = entry.getKey().toString();
            pictureData.setDataControl(id);
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                continue;
            }
            anyVisible = true;
            FloatImageView floatImageView = ensureWindowView(context, pictureData, id);
            WindowsMethods.removeWindowIfAttached(floatImageView);
            int positionX = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
            int positionY = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
            float pictureAlpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
            boolean touchAndMove = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
            boolean overLayout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
            syncWindowViewState(floatImageView, touchAndMove, overLayout, pictureAlpha);
            WindowsMethods.createWindow(windowManager, floatImageView, touchAndMove, overLayout, pictureAlpha, positionX, positionY);
        }
        ((MainApplication) context.getApplicationContext()).setWinVisible(anyVisible);
        if (anyVisible) {
            WindowsMethods.syncAllWindows(context);
            NotificationService.refresh(context);
        }
    }

    public static int getWindowCount() {
        return getWindowCount(null);
    }

    public static int getWindowCount(Set<String> filterIds) {
        if (filterIds != null && filterIds.isEmpty()) {
            return 0;
        }
        LinkedHashMap<String, String> list = new PictureData().getListArray();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        if (filterIds == null) {
            return list.size();
        }
        int count = 0;
        for (String pictureId : filterIds) {
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            if (list.containsKey(pictureId)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasVisibleWindowsConfigured(Context context) {
        return hasVisibleWindowsConfigured(context, null);
    }

    public static boolean hasVisibleWindowsConfigured(Context context, Set<String> filterIds) {
        return !getVisibleConfiguredPictureIds(context, filterIds).isEmpty();
    }

    public static LinkedHashSet<String> getVisibleConfiguredPictureIds(Context context) {
        return getVisibleConfiguredPictureIds(context, null);
    }

    public static LinkedHashSet<String> getVisibleConfiguredPictureIds(Context context, Set<String> filterIds) {
        if (filterIds != null && filterIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        LinkedHashSet<String> visiblePictureIds = new LinkedHashSet<>();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            return visiblePictureIds;
        }
        for (Map.Entry<?, ?> entry : linkedHashMap.entrySet()) {
            String pictureId = entry.getKey().toString();
            if (filterIds != null && !filterIds.contains(pictureId)) {
                continue;
            }
            pictureData.setDataControl(pictureId);
            if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                visiblePictureIds.add(pictureId);
            }
        }
        return visiblePictureIds;
    }

    public static LinkedHashSet<String> getConfiguredPictureIds() {
        LinkedHashMap<String, String> linkedHashMap = new PictureData().getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(linkedHashMap.keySet());
    }

    public static LinkedHashSet<String> getValidConfiguredPictureIds() {
        LinkedHashSet<String> configuredPictureIds = getConfiguredPictureIds();
        LinkedHashSet<String> validPictureIds = new LinkedHashSet<>();
        for (String pictureId : configuredPictureIds) {
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            if (ImageMethods.hasAvailablePictureContent(pictureId)) {
                validPictureIds.add(pictureId);
            }
        }
        return validPictureIds;
    }

    public static String showOnlyRandomWindow(Context context) {
        LinkedHashSet<String> validPictureIds = getValidConfiguredPictureIds();
        if (validPictureIds.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> visiblePictureIds = getVisibleConfiguredPictureIds(context);
        String targetPictureId = pickRandomPictureId(validPictureIds, visiblePictureIds);
        if (targetPictureId == null || targetPictureId.isEmpty()) {
            return null;
        }

        PictureData pictureData = new PictureData();
        LinkedHashMap<String, Boolean> visibilityChanges = new LinkedHashMap<>();
        for (String visiblePictureId : visiblePictureIds) {
            if (visiblePictureId == null || visiblePictureId.isEmpty()) {
                continue;
            }
            if (visiblePictureId.equals(targetPictureId)) {
                continue;
            }
            pictureData.setDataControl(visiblePictureId);
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                continue;
            }
            if (detachWindowById(context, visiblePictureId, false)) {
                visibilityChanges.put(visiblePictureId, false);
            }
        }

        pictureData.setDataControl(targetPictureId);
        if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
            visibilityChanges.put(targetPictureId, true);
            showWindowById(context, pictureData, targetPictureId, false);
        } else {
            showWindowById(context, pictureData, targetPictureId, visibilityChanges.isEmpty());
        }
        if (visibilityChanges.isEmpty()) {
            updateGlobalVisibleState(context);
        } else {
            finishVisibilityChangesAsync(context, visibilityChanges);
        }
        return targetPictureId;
    }

    public static void setAllWindowsVisible(Context context, boolean visible) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            if (!visible) {
                CloseAllWindows(context);
            }
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return;
        }
        if (!visible) {
            CloseAllWindows(context);
            finishAllWindowsVisibilityChangeAsync(context, linkedHashMap, false);
            return;
        }
        setWindowsVisible(context, new LinkedHashSet<>(linkedHashMap.keySet()), visible);
    }

    public static void setWindowsVisible(Context context, Set<String> targetIds, boolean visible) {
        if (targetIds == null || targetIds.isEmpty()) {
            updateGlobalVisibleState(context);
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return;
        }
        LinkedHashSet<String> changedIds = new LinkedHashSet<>();
        boolean visualStateChanged = false;
        for (String pictureId : targetIds) {
            if (pictureId == null || pictureId.isEmpty() || !linkedHashMap.containsKey(pictureId)) {
                continue;
            }
            pictureData.setDataControl(pictureId);
            boolean dataVisible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
            if (visible) {
                showWindowById(context, pictureData, pictureId, false);
                visualStateChanged = true;
                if (!dataVisible) {
                    changedIds.add(pictureId);
                }
            } else {
                if (detachWindowById(context, pictureId, false)) {
                    visualStateChanged = true;
                }
                if (dataVisible) {
                    changedIds.add(pictureId);
                }
            }
        }
        if (!changedIds.isEmpty()) {
            finishVisibilityChangesAsync(context, changedIds, visible);
            return;
        }
        if (visualStateChanged) {
            WindowsMethods.syncAllWindows(context);
        }
        updateGlobalVisibleState(context);
        NotificationService.refresh(context);
        OverlayRuntimeController.notifyRuntimeStateChanged(context);
    }

    public static void syncWindowFromDisk(Context context, String id) {
        syncWindowFromDisk(context, id, true);
    }

    public static void syncWindowFromDisk(Context context, String id, boolean createIfVisible) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || !linkedHashMap.containsKey(id)) {
            releaseWindowById(context, id, true);
            updateGlobalVisibleState(context);
            return;
        }
        pictureData.setDataControl(id);
        boolean visible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
        if (visible) {
            FloatImageView existingView = ImageMethods.getFloatImageViewById(context, id);
            if (createIfVisible || existingView != null) {
                showWindowById(context, pictureData, id, true);
            }
        } else {
            hideWindowById(context, id);
        }
        updateGlobalVisibleState(context);
    }

    public static void setWindowVisible(Context context, PictureData pictureData, String id, boolean visible) {
        pictureData.setDataControl(id);
        boolean data_visible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
        if (visible) {
            if (!data_visible) {
                pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                showWindowById(context, pictureData, id, false);
                finishVisibilityChangeAsync(context, id, true);
            }
        } else {
            if (data_visible) {
                detachWindowById(context, id, false);
                pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
                finishVisibilityChangeAsync(context, id, false);
            } else {
                if (detachWindowById(context, id, false)) {
                    updateGlobalVisibleState(context);
                    NotificationService.refresh(context);
                }
            }
        }
    }

    private static boolean hideWindowById(Context mContext, String id) {
        return detachWindowById(mContext, id, true);
    }

    private static void showWindowById(Context mContext, String id) {
        showWindowById(mContext, id, true);
    }

    private static void showWindowById(Context mContext, String id, boolean syncAfterCreate) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        showWindowById(mContext, pictureData, id, syncAfterCreate);
    }

    private static void showWindowById(Context mContext, PictureData pictureData, String id, boolean syncAfterCreate) {
        if (pictureData == null) {
            showWindowById(mContext, id, syncAfterCreate);
            return;
        }
        pictureData.setDataControl(id);
        Point position = OverlayRuntimeStateStore.getWindowPosition(
                mContext,
                id,
                pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X),
                pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y)
        );
        int positionX = position.x;
        int positionY = position.y;
        float defaultZoom = getDefaultZoom(mContext, pictureData, id);
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom);
        float pictureDegree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
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
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        long displayBitmapVersion = ImageMethods.getDisplayBitmapVersion(id);
        boolean canReuseBitmap = canReuseDisplayBitmap(floatImageView, displayBitmapVersion);
        if (floatImageView == null || !canReuseBitmap) {
            Bitmap displayBitmap = ImageMethods.getDisplayBitmap(
                    mContext,
                    id,
                    zoom,
                    pictureDegree,
                    cornerRadiusRatio,
                    cornerRadiusMask,
                    edgeFeatherRatio,
                    edgeFeatherMask
            );
            displayBitmapVersion = ImageMethods.getDisplayBitmapVersion(id);
            float pictureAlpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
            boolean touchAndMove = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
            boolean overLayout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
            if (floatImageView == null) {
                floatImageView = ImageMethods.createPictureView(mContext, displayBitmap, touchAndMove, overLayout, pictureAlpha);
                ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
            } else {
                ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
            }
            floatImageView.setDisplayBitmapVersion(displayBitmapVersion);
        }
        float pictureAlpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        syncWindowViewState(floatImageView, touch_and_move, over_layout, pictureAlpha);
        // syncAfterCreate 为 true 时 createWindow 会重新平衡所有窗口的联合透明度。
        WindowsMethods.createWindow(getWindowManager(mContext), floatImageView, touch_and_move, over_layout, pictureAlpha, positionX, positionY, syncAfterCreate);
    }

    private static void finishVisibilityChangeAsync(Context context, String id, boolean visible) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        AppExecutors.io().execute(() -> {
            PictureData asyncPictureData = new PictureData();
            asyncPictureData.setDataControl(id);
            asyncPictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
            asyncPictureData.commitData();
            MAIN_HANDLER.post(() -> {
                WindowsMethods.syncAllWindows(appContext);
                updateGlobalVisibleState(appContext);
                NotificationService.refresh(appContext);
                OverlayRuntimeController.notifyRuntimeStateChanged(appContext);
            });
        });
    }

    private static void finishAllWindowsVisibilityChangeAsync(Context context,
                                                              LinkedHashMap<String, String> pictureList,
                                                              boolean visible) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashMap<String, String> pictureListSnapshot = new LinkedHashMap<>(pictureList);
        AppExecutors.io().execute(() -> {
            new PictureData().setAllPictureShowEnabled(pictureListSnapshot, visible);
            MAIN_HANDLER.post(() -> finishBatchVisibilityChange(appContext));
        });
    }

    private static void finishVisibilityChangesAsync(Context context, Set<String> pictureIds, boolean visible) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashSet<String> pictureIdSnapshot = new LinkedHashSet<>(pictureIds);
        AppExecutors.io().execute(() -> {
            new PictureData().setPictureShowEnabled(pictureIdSnapshot, visible);
            MAIN_HANDLER.post(() -> finishBatchVisibilityChange(appContext));
        });
    }

    private static void finishVisibilityChangesAsync(Context context, LinkedHashMap<String, Boolean> visibilityById) {
        if (visibilityById == null || visibilityById.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashMap<String, Boolean> visibilitySnapshot = new LinkedHashMap<>(visibilityById);
        AppExecutors.io().execute(() -> {
            new PictureData().setPicturesShowEnabled(visibilitySnapshot);
            MAIN_HANDLER.post(() -> finishBatchVisibilityChange(appContext));
        });
    }

    private static void finishBatchVisibilityChange(Context context) {
        WindowsMethods.syncAllWindows(context);
        updateGlobalVisibleState(context);
        NotificationService.refresh(context);
        OverlayRuntimeController.notifyRuntimeStateChanged(context);
    }

    private static void updateGlobalVisibleState(Context context) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return;
        }
        boolean anyVisible = false;
        for (Map.Entry<?, ?> entry : linkedHashMap.entrySet()) {
            pictureData.setDataControl(entry.getKey().toString());
            anyVisible = anyVisible || pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
            if (anyVisible) {
                break;
            }
        }
        ((MainApplication) context.getApplicationContext()).setWinVisible(anyVisible);
    }

    private static FloatImageView ensureWindowView(Context mContext, PictureData pictureData, String id) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        long displayBitmapVersion = ImageMethods.getDisplayBitmapVersion(id);
        if (canReuseDisplayBitmap(floatImageView, displayBitmapVersion)) {
            return floatImageView;
        }
        float default_zoom = getDefaultZoom(mContext, pictureData, id);
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
        float picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        float picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
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
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        Bitmap bitmap = ImageMethods.getDisplayBitmap(
                mContext,
                id,
                zoom,
                picture_degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (floatImageView == null) {
            floatImageView = ImageMethods.createPictureView(mContext, bitmap, touch_and_move, over_layout, picture_alpha);
            ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
        } else {
            ImageMethods.setPictureBitmap(floatImageView, bitmap);
        }
        floatImageView.setDisplayBitmapVersion(ImageMethods.getDisplayBitmapVersion(id));
        return floatImageView;
    }

    private static float getDefaultZoom(Context context, PictureData pictureData, String id) {
        if (pictureData.has(Config.DATA_PICTURE_DEFAULT_ZOOM)) {
            return pictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, 1f);
        }
        return ImageMethods.getDefaultZoom(context, id, false);
    }

    private static boolean canReuseDisplayBitmap(FloatImageView floatImageView, long displayBitmapVersion) {
        return floatImageView != null
                && displayBitmapVersion > 0L
                && floatImageView.getDisplayBitmapVersion() == displayBitmapVersion
                && ImageMethods.hasActivePictureBitmap(floatImageView);
    }

    private static void syncWindowViewState(FloatImageView floatImageView, boolean touchAndMove, boolean overLayout, float pictureAlpha) {
        if (floatImageView == null) {
            return;
        }
        floatImageView.setMoveable(touchAndMove);
        floatImageView.setOverLayout(overLayout);
        floatImageView.setPictureAlpha(pictureAlpha);
    }

    private static synchronized String pickRandomPictureId(Set<String> candidateIds, Set<String> excludeIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return null;
        }

        ensureRandomBag(candidateIds);

        int selectedIndex = 0;
        if (excludeIds != null && !excludeIds.isEmpty() && RANDOM_BAG.size() > 1) {
            for (int index = 0; index < RANDOM_BAG.size(); index++) {
                if (!excludeIds.contains(RANDOM_BAG.get(index))) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        String selectedPictureId = RANDOM_BAG.remove(selectedIndex);
        lastRandomPictureId = selectedPictureId;
        return selectedPictureId;
    }

    private static void ensureRandomBag(Set<String> candidateIds) {
        if (!RANDOM_BAG.isEmpty() && RANDOM_BAG_SNAPSHOT.equals(candidateIds)) {
            return;
        }

        RANDOM_BAG.clear();
        RANDOM_BAG.addAll(candidateIds);
        Collections.shuffle(RANDOM_BAG, RANDOM);
        if (RANDOM_BAG.size() > 1 && Objects.equals(RANDOM_BAG.get(0), lastRandomPictureId)) {
            int swapIndex = 1 + RANDOM.nextInt(RANDOM_BAG.size() - 1);
            Collections.swap(RANDOM_BAG, 0, swapIndex);
        }

        RANDOM_BAG_SNAPSHOT.clear();
        RANDOM_BAG_SNAPSHOT.addAll(candidateIds);
    }

    private static void cleanupRegisteredWindows(Context context, LinkedHashMap<String, String> pictureList) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        Map<String, View> registeredViews = mainApplication.getRegisteredViewsSnapshot();
        if (registeredViews.isEmpty()) {
            return;
        }
        PictureData pictureData = new PictureData();
        for (Map.Entry<String, View> entry : registeredViews.entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof FloatImageView)) {
                mainApplication.unregisterView(id);
                continue;
            }
            if (pictureList == null || !pictureList.containsKey(id)) {
                releaseWindowById(context, id, false);
                continue;
            }
            pictureData.setDataControl(id);
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                releaseWindowById(context, id, false);
            }
        }
    }

    private static boolean releaseWindowById(Context context, String id, boolean syncAllWindows) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(context, id);
        if (floatImageView == null) {
            return true;
        }
        boolean removed = WindowsMethods.removeWindowIfAttached(floatImageView);
        if (floatImageView.isAttachedToWindow() || !removed) {
            Log.w("ManageMethods", "releaseWindowById failed to detach window: " + id);
            scheduleDetachedWindowCleanup(context, id, floatImageView, syncAllWindows);
            return false;
        }
        releaseRegisteredWindow(mainApplication, id, floatImageView);
        if (syncAllWindows) {
            WindowsMethods.syncAllWindows(context);
        }
        return true;
    }

    private static boolean detachWindowById(Context context, String id, boolean syncAllWindows) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(context, id);
        if (floatImageView == null) {
            return true;
        }
        boolean removed = WindowsMethods.removeWindowIfAttached(floatImageView);
        if (floatImageView.isAttachedToWindow() || !removed) {
            Log.w("ManageMethods", "detachWindowById failed to detach window: " + id);
            scheduleDetachedWindowCleanup(context, id, floatImageView, syncAllWindows);
            return false;
        }
        if (syncAllWindows) {
            WindowsMethods.syncAllWindows(context);
        }
        return true;
    }

    private static void scheduleDetachedWindowCleanup(Context context,
                                                      String id,
                                                      FloatImageView floatImageView,
                                                      boolean syncAllWindows) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        MAIN_HANDLER.postDelayed(() -> cleanupDetachedWindow(appContext, id, floatImageView, syncAllWindows), 250L);
        MAIN_HANDLER.postDelayed(() -> cleanupDetachedWindow(appContext, id, floatImageView, syncAllWindows), 1000L);
    }

    private static void cleanupDetachedWindow(Context context,
                                              String id,
                                              FloatImageView floatImageView,
                                              boolean syncAllWindows) {
        if (floatImageView == null) {
            return;
        }
        if (floatImageView.isAttachedToWindow()) {
            WindowsMethods.removeWindowIfAttached(floatImageView);
        }
        if (floatImageView.isAttachedToWindow()) {
            return;
        }
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        releaseRegisteredWindowIfSame(mainApplication, id, floatImageView);
        if (syncAllWindows) {
            WindowsMethods.syncAllWindows(context);
        }
        updateGlobalVisibleState(context);
        NotificationService.refresh(context);
    }

    private static void releaseRegisteredWindow(MainApplication mainApplication, String id, FloatImageView floatImageView) {
        ImageMethods.releasePictureView(floatImageView);
        mainApplication.unregisterView(id);
    }

    private static void releaseRegisteredWindowIfSame(MainApplication mainApplication, String id, FloatImageView floatImageView) {
        if (mainApplication.unregisterViewIfSame(id, floatImageView)) {
            ImageMethods.releasePictureView(floatImageView);
        }
    }

}
