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
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int WINDOW_VISIBILITY_BATCH_SIZE = 1;
    private static final long WINDOW_VISIBILITY_BATCH_DELAY_MS = 16L;
    private static final AtomicInteger RUN_WIN_SEQUENCE = new AtomicInteger();
    private static String lastRandomPictureId = null;

    public static void RunWin(Context mContext) {
        if (!PermissionMethods.hasOverlayPermission(mContext)) {
            return;
        }
        Context appContext = mContext.getApplicationContext() != null ? mContext.getApplicationContext() : mContext;
        int runToken = RUN_WIN_SEQUENCE.incrementAndGet();
        AppExecutors.io().execute(() -> {
            LinkedHashMap<String, String> list = null;
            ArrayList<StartupWindowRequest> visibleRequests = new ArrayList<>();
            try {
                PictureData pictureData = new PictureData();
                list = pictureData.getListArray();
                visibleRequests = buildStartupWindowRequests(appContext, runToken, pictureData, list);
            } catch (OutOfMemoryError e) {
                Log.e("ManageMethods", "RunWin ran out of memory while reading startup window data", e);
            } catch (RuntimeException e) {
                Log.e("ManageMethods", "RunWin failed to read startup window data", e);
            }
            LinkedHashMap<String, String> listSnapshot = list;
            ArrayList<StartupWindowRequest> requestSnapshot = visibleRequests;
            MAIN_HANDLER.post(() -> startWindowRebuildBatch(appContext, runToken, listSnapshot, requestSnapshot, null));
        });
    }

    private static ArrayList<StartupWindowRequest> buildStartupWindowRequests(Context context,
                                                                              int runToken,
                                                                              PictureData pictureData,
                                                                              LinkedHashMap<String, String> list) {
        ArrayList<StartupWindowRequest> requests = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return requests;
        }
        for (Map.Entry<?, ?> entry : list.entrySet()) {
            if (!isRunWinCurrent(runToken)) {
                return requests;
            }
            if (entry.getKey() == null) {
                continue;
            }
            String id = entry.getKey().toString();
            if (id.isEmpty()) {
                continue;
            }
            pictureData.setDataControl(id);
            if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                continue;
            }
            if (!ImageMethods.hasAvailablePictureContent(id)) {
                requests.add(StartupWindowRequest.missingContent(id));
                continue;
            }
            Point position = OverlayRuntimeStateStore.getWindowPosition(
                    context,
                    id,
                    pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X),
                    pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y)
            );
            requests.add(new StartupWindowRequest(
                    id,
                    position.x,
                    position.y,
                    pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE),
                    pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT),
                    pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA),
                    getConfiguredZoom(context, pictureData, id),
                    pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE),
                    pictureData.getFloat(
                            Config.DATA_PICTURE_CORNER_RADIUS_RATIO,
                            Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO
                    ),
                    pictureData.getInt(
                            Config.DATA_PICTURE_CORNER_RADIUS_MASK,
                            Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK
                    ),
                    pictureData.getFloat(
                            Config.DATA_PICTURE_EDGE_FEATHER_RATIO,
                            Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
                    ),
                    pictureData.getInt(
                            Config.DATA_PICTURE_EDGE_FEATHER_MASK,
                            Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
                    )
            ));
        }
        return requests;
    }

    private static void startWindowRebuildBatch(Context context,
                                                int runToken,
                                                LinkedHashMap<String, String> list,
                                                ArrayList<StartupWindowRequest> requests,
                                                Runnable completionCallback) {
        if (!isRunWinCurrent(runToken)) {
            return;
        }
        cleanupRegisteredWindows(context, list);
        if (list == null || list.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            runCompletionCallback(completionCallback);
            return;
        }
        if (requests == null || requests.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            NotificationService.refresh(context);
            runCompletionCallback(completionCallback);
            return;
        }
        processRunWinBatch(
                context,
                runToken,
                getWindowManager(context),
                requests,
                0,
                false,
                completionCallback
        );
    }

    private static void processRunWinBatch(Context context,
                                           int runToken,
                                           WindowManager windowManager,
                                           ArrayList<StartupWindowRequest> requests,
                                           int index,
                                           boolean anyVisible,
                                           Runnable completionCallback) {
        if (!isRunWinCurrent(runToken)) {
            return;
        }
        if (index >= requests.size()) {
            finishRunWinBatch(context, runToken, anyVisible, completionCallback);
            return;
        }
        StartupWindowRequest request = requests.get(index);
        if (!request.contentAvailable) {
            releaseWindowById(context, request.id, false);
            Log.w("ManageMethods", "missing picture content for window rebuild: " + request.id);
            scheduleNextRunWinBatch(context, runToken, windowManager, requests, index + 1, anyVisible, completionCallback);
            return;
        }
        FloatImageView existingView = ImageMethods.getFloatImageViewById(context, request.id);
        long displayBitmapVersion = ImageMethods.getDisplayBitmapVersion(request.id);
        if (canReuseDisplayBitmap(existingView, displayBitmapVersion)) {
            boolean created = attachStartupWindow(
                    context,
                    windowManager,
                    request,
                    existingView,
                    null,
                    displayBitmapVersion
            );
            scheduleNextRunWinBatch(context, runToken, windowManager, requests, index + 1, anyVisible || created, completionCallback);
            return;
        }

        AppExecutors.io().execute(() -> {
            Bitmap displayBitmap = null;
            long preparedBitmapVersion = 0L;
            try {
                if (isRunWinCurrent(runToken) && ImageMethods.hasAvailablePictureContent(request.id)) {
                    displayBitmap = ImageMethods.getDisplayBitmapOrNull(
                            request.id,
                            request.zoom,
                            request.degree,
                            request.cornerRadiusRatio,
                            request.cornerRadiusMask,
                            request.edgeFeatherRatio,
                            request.edgeFeatherMask
                    );
                    if (displayBitmap != null) {
                        preparedBitmapVersion = ImageMethods.getDisplayBitmapVersion(request.id);
                    }
                }
            } catch (OutOfMemoryError e) {
                Log.e("ManageMethods", "Window rebuild failed to load bitmap: " + request.id, e);
            } catch (RuntimeException e) {
                Log.e("ManageMethods", "Window rebuild failed to prepare window: " + request.id, e);
            }
            Bitmap preparedBitmap = displayBitmap;
            long versionSnapshot = preparedBitmapVersion;
            MAIN_HANDLER.post(() -> {
                if (!isRunWinCurrent(runToken)) {
                    ImageMethods.recycleBitmap(preparedBitmap);
                    return;
                }
                boolean created = false;
                if (preparedBitmap != null) {
                    created = attachStartupWindow(
                            context,
                            windowManager,
                            request,
                            ImageMethods.getFloatImageViewById(context, request.id),
                            preparedBitmap,
                            versionSnapshot
                    );
                } else {
                    releaseWindowById(context, request.id, false);
                    Log.w("ManageMethods", "Window rebuild failed to load picture content: " + request.id);
                }
                scheduleNextRunWinBatch(context, runToken, windowManager, requests, index + 1, anyVisible || created, completionCallback);
            });
        });
    }

    private static void scheduleNextRunWinBatch(Context context,
                                                int runToken,
                                                WindowManager windowManager,
                                                ArrayList<StartupWindowRequest> requests,
                                                int nextIndex,
                                                boolean anyVisible,
                                                Runnable completionCallback) {
        MAIN_HANDLER.postDelayed(() -> processRunWinBatch(
                context,
                runToken,
                windowManager,
                requests,
                nextIndex,
                anyVisible,
                completionCallback
        ), WINDOW_VISIBILITY_BATCH_DELAY_MS);
    }

    private static void finishRunWinBatch(Context context, int runToken, boolean anyVisible, Runnable completionCallback) {
        if (!isRunWinCurrent(runToken)) {
            return;
        }
        if (anyVisible) {
            WindowsMethods.syncAllWindows(context);
        }
        ((MainApplication) context.getApplicationContext()).setWinVisible(anyVisible);
        NotificationService.refresh(context);
        runCompletionCallback(completionCallback);
    }

    private static boolean attachStartupWindow(Context context,
                                               WindowManager windowManager,
                                               StartupWindowRequest request,
                                               FloatImageView floatImageView,
                                               Bitmap displayBitmap,
                                               long displayBitmapVersion) {
        if (displayBitmap != null) {
            if (floatImageView == null) {
                floatImageView = ImageMethods.createPictureView(
                        context,
                        displayBitmap,
                        request.touchAndMove,
                        request.overLayout,
                        request.pictureAlpha
                );
                ImageMethods.saveFloatImageViewById(context, request.id, floatImageView);
            } else {
                ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
            }
            floatImageView.setDisplayBitmapVersion(displayBitmapVersion);
        }
        if (floatImageView == null) {
            return false;
        }
        syncWindowViewState(floatImageView, request.touchAndMove, request.overLayout, request.pictureAlpha);
        return WindowsMethods.createWindow(
                windowManager,
                floatImageView,
                request.touchAndMove,
                request.overLayout,
                request.pictureAlpha,
                request.positionX,
                request.positionY,
                false
        );
    }

    private static boolean isRunWinCurrent(int runToken) {
        return RUN_WIN_SEQUENCE.get() == runToken;
    }

    public static boolean DeleteWin(Context mContext, String id) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        if (!releaseWindowById(mContext, id, true)) {
            return false;
        }
        if (!pictureData.remove()) {
            Log.w("ManageMethods", "DeleteWin failed to remove picture data: " + id);
            syncWindowFromDisk(mContext, id, true);
            return false;
        }
        OverlayRuntimeStateStore.removePureOverlayManagedPictureId(mContext, id);
        ImageMethods.clearAllTemp(mContext, id);
        updateGlobalVisibleState(mContext);
        NotificationService.refresh(mContext);
        return true;
    }

    public static LinkedHashSet<String> CloseAllWindows(Context mContext) {
        RUN_WIN_SEQUENCE.incrementAndGet();
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        Map<String, View> hashMap = mainApplication.getRegisteredViewsSnapshot();
        LinkedHashSet<String> failedIds = new LinkedHashSet<>();
        if (!hashMap.isEmpty()) {
            for (Map.Entry<String, View> entry : hashMap.entrySet()) {
                String id = entry.getKey();
                if (entry.getValue() instanceof FloatImageView) {
                    if (!releaseWindowById(mContext, id, false)) {
                        failedIds.add(id);
                    }
                } else {
                    mainApplication.unregisterView(id);
                }
            }
        }
        WindowsMethods.syncAllWindows(mContext);
        if (failedIds.isEmpty()) {
            mainApplication.setWinVisible(false);
        } else {
            updateGlobalVisibleState(mContext);
        }
        return failedIds;
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
        recreateVisibleWindows(context, null);
    }

    public static void recreateVisibleWindows(Context context, Runnable completionCallback) {
        if (!PermissionMethods.hasOverlayPermission(context)) {
            runCompletionCallback(completionCallback);
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        int runToken = RUN_WIN_SEQUENCE.incrementAndGet();
        AppExecutors.io().execute(() -> {
            LinkedHashMap<String, String> linkedHashMap = null;
            ArrayList<StartupWindowRequest> visibleRequests = new ArrayList<>();
            try {
                PictureData pictureData = new PictureData();
                linkedHashMap = pictureData.getListArray();
                visibleRequests = buildStartupWindowRequests(appContext, runToken, pictureData, linkedHashMap);
            } catch (OutOfMemoryError e) {
                Log.e("ManageMethods", "recreateVisibleWindows ran out of memory while reading window data", e);
            } catch (RuntimeException e) {
                Log.e("ManageMethods", "recreateVisibleWindows failed to read window data", e);
            }
            LinkedHashMap<String, String> listSnapshot = linkedHashMap;
            ArrayList<StartupWindowRequest> requestSnapshot = visibleRequests;
            MAIN_HANDLER.post(() -> startWindowRebuildBatch(
                    appContext,
                    runToken,
                    listSnapshot,
                    requestSnapshot,
                    completionCallback
            ));
        });
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

    public static boolean hasVisibleRuntimeWindows(Context context, Set<String> filterIds) {
        if (filterIds != null && filterIds.isEmpty()) {
            return false;
        }
        Map<String, View> registeredViews = ((MainApplication) context.getApplicationContext()).getRegisteredViewsSnapshot();
        if (registeredViews.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, View> entry : registeredViews.entrySet()) {
            if (filterIds != null && !filterIds.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof FloatImageView floatImageView
                    && floatImageView.isAttachedToWindow()
                    && floatImageView.getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
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
        boolean targetShown;
        if (!pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
            targetShown = showWindowById(context, pictureData, targetPictureId, false);
            if (targetShown) {
                visibilityChanges.put(targetPictureId, true);
            }
        } else {
            targetShown = showWindowById(context, pictureData, targetPictureId, visibilityChanges.isEmpty());
        }
        if (!targetShown) {
            if (visibilityChanges.isEmpty()) {
                updateGlobalVisibleState(context);
                NotificationService.refresh(context);
                OverlayRuntimeController.notifyRuntimeStateChanged(context);
            } else {
                finishVisibilityChangesAsync(context, visibilityChanges);
            }
            return null;
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
            LinkedHashSet<String> failedIds = new LinkedHashSet<>();
            if (!visible) {
                failedIds = CloseAllWindows(context);
            }
            ((MainApplication) context.getApplicationContext()).setWinVisible(!failedIds.isEmpty());
            return;
        }
        if (!visible) {
            LinkedHashSet<String> failedIds = CloseAllWindows(context);
            LinkedHashSet<String> closedIds = new LinkedHashSet<>(linkedHashMap.keySet());
            closedIds.removeAll(failedIds);
            if (!closedIds.isEmpty()) {
                finishVisibilityChangesAsync(context, closedIds, false);
            } else {
                updateGlobalVisibleState(context);
                NotificationService.refresh(context);
                OverlayRuntimeController.notifyRuntimeStateChanged(context);
            }
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
            if (visible) {
                if (showWindowById(context, pictureData, pictureId, false)) {
                    visualStateChanged = true;
                    changedIds.add(pictureId);
                }
            } else {
                boolean detached = detachWindowById(context, pictureId, false);
                if (detached) {
                    visualStateChanged = true;
                    changedIds.add(pictureId);
                }
            }
        }
        finishWindowsVisibleChange(context, changedIds, visible, visualStateChanged, null);
    }

    public static void setWindowsVisibleAsync(Context context, Set<String> targetIds, boolean visible, Runnable completionCallback) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashSet<String> targetIdSnapshot = targetIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(targetIds);
        MAIN_HANDLER.post(() -> startWindowsVisibleBatch(appContext, targetIdSnapshot, visible, completionCallback));
    }

    private static void startWindowsVisibleBatch(Context context,
                                                 LinkedHashSet<String> targetIds,
                                                 boolean visible,
                                                 Runnable completionCallback) {
        if (targetIds == null || targetIds.isEmpty()) {
            updateGlobalVisibleState(context);
            runCompletionCallback(completionCallback);
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            runCompletionCallback(completionCallback);
            return;
        }
        processWindowsVisibleBatch(
                context,
                new ArrayList<>(targetIds),
                visible,
                linkedHashMap,
                pictureData,
                new LinkedHashSet<>(),
                0,
                false,
                completionCallback
        );
    }

    private static void processWindowsVisibleBatch(Context context,
                                                   ArrayList<String> targetIds,
                                                   boolean visible,
                                                   LinkedHashMap<String, String> linkedHashMap,
                                                   PictureData pictureData,
                                                   LinkedHashSet<String> changedIds,
                                                   int startIndex,
                                                   boolean visualStateChanged,
                                                   Runnable completionCallback) {
        int index = startIndex;
        boolean batchVisualStateChanged = visualStateChanged;
        int processedCount = 0;
        while (index < targetIds.size() && processedCount < WINDOW_VISIBILITY_BATCH_SIZE) {
            String pictureId = targetIds.get(index);
            index++;
            processedCount++;
            if (pictureId == null || pictureId.isEmpty() || !linkedHashMap.containsKey(pictureId)) {
                continue;
            }
            pictureData.setDataControl(pictureId);
            if (visible) {
                if (showWindowById(context, pictureData, pictureId, false)) {
                    batchVisualStateChanged = true;
                    changedIds.add(pictureId);
                }
            } else {
                boolean detached = detachWindowById(context, pictureId, false);
                if (detached) {
                    batchVisualStateChanged = true;
                    changedIds.add(pictureId);
                }
            }
        }
        if (index < targetIds.size()) {
            int nextIndex = index;
            boolean nextVisualStateChanged = batchVisualStateChanged;
            MAIN_HANDLER.postDelayed(() -> processWindowsVisibleBatch(
                    context,
                    targetIds,
                    visible,
                    linkedHashMap,
                    pictureData,
                    changedIds,
                    nextIndex,
                    nextVisualStateChanged,
                    completionCallback
            ), WINDOW_VISIBILITY_BATCH_DELAY_MS);
            return;
        }
        finishWindowsVisibleChange(context, changedIds, visible, batchVisualStateChanged, completionCallback);
    }

    private static void finishWindowsVisibleChange(Context context,
                                                   Set<String> changedIds,
                                                   boolean visible,
                                                   boolean visualStateChanged,
                                                   Runnable completionCallback) {
        if (changedIds != null && !changedIds.isEmpty()) {
            finishVisibilityChangesAsync(context, changedIds, visible, completionCallback);
            return;
        }
        if (visualStateChanged) {
            WindowsMethods.syncAllWindows(context);
        }
        updateGlobalVisibleState(context);
        NotificationService.refresh(context);
        OverlayRuntimeController.notifyRuntimeStateChanged(context);
        runCompletionCallback(completionCallback);
    }

    public static void syncWindowFromDisk(Context context, String id) {
        syncWindowFromDisk(context, id, true);
    }

    public static void syncWindowFromDisk(Context context, String id, boolean createIfVisible) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        syncWindowFromDisk(context, pictureData, linkedHashMap, id, createIfVisible, true);
        updateGlobalVisibleState(context);
    }

    public static void syncWindowsFromDiskAsync(Context context,
                                                ArrayList<String> pictureIds,
                                                boolean createIfVisible,
                                                Runnable completionCallback) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        ArrayList<String> pictureIdSnapshot = pictureIds == null ? new ArrayList<>() : new ArrayList<>(pictureIds);
        MAIN_HANDLER.post(() -> startSyncWindowsFromDiskBatch(appContext, pictureIdSnapshot, createIfVisible, completionCallback));
    }

    private static void startSyncWindowsFromDiskBatch(Context context,
                                                      ArrayList<String> pictureIds,
                                                      boolean createIfVisible,
                                                      Runnable completionCallback) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            runCompletionCallback(completionCallback);
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        processSyncWindowsFromDiskBatch(
                context,
                pictureIds,
                createIfVisible,
                pictureData,
                linkedHashMap,
                0,
                false,
                completionCallback
        );
    }

    private static void processSyncWindowsFromDiskBatch(Context context,
                                                       ArrayList<String> pictureIds,
                                                       boolean createIfVisible,
                                                       PictureData pictureData,
                                                       LinkedHashMap<String, String> linkedHashMap,
                                                       int startIndex,
                                                       boolean visualStateChanged,
                                                       Runnable completionCallback) {
        int index = startIndex;
        boolean batchVisualStateChanged = visualStateChanged;
        int processedCount = 0;
        while (index < pictureIds.size() && processedCount < WINDOW_VISIBILITY_BATCH_SIZE) {
            String pictureId = pictureIds.get(index);
            index++;
            processedCount++;
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            if (syncWindowFromDisk(context, pictureData, linkedHashMap, pictureId, createIfVisible, false)) {
                batchVisualStateChanged = true;
            }
        }
        if (index < pictureIds.size()) {
            int nextIndex = index;
            boolean nextVisualStateChanged = batchVisualStateChanged;
            MAIN_HANDLER.postDelayed(() -> processSyncWindowsFromDiskBatch(
                    context,
                    pictureIds,
                    createIfVisible,
                    pictureData,
                    linkedHashMap,
                    nextIndex,
                    nextVisualStateChanged,
                    completionCallback
            ), WINDOW_VISIBILITY_BATCH_DELAY_MS);
            return;
        }
        if (batchVisualStateChanged) {
            WindowsMethods.syncAllWindows(context);
        }
        updateGlobalVisibleState(context);
        runCompletionCallback(completionCallback);
    }

    private static boolean syncWindowFromDisk(Context context,
                                              PictureData pictureData,
                                              LinkedHashMap<String, String> linkedHashMap,
                                              String id,
                                              boolean createIfVisible,
                                              boolean syncAfterOperation) {
        if (linkedHashMap == null || !linkedHashMap.containsKey(id)) {
            if (ImageMethods.getFloatImageViewById(context, id) == null) {
                return false;
            }
            return releaseWindowById(context, id, syncAfterOperation);
        }
        pictureData.setDataControl(id);
        boolean visible = pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
        FloatImageView existingView = ImageMethods.getFloatImageViewById(context, id);
        if (visible) {
            if (createIfVisible || existingView != null) {
                return showWindowById(context, pictureData, id, syncAfterOperation);
            }
            return false;
        }
        if (existingView == null) {
            return false;
        }
        return detachWindowById(context, id, syncAfterOperation);
    }

    public static void setWindowVisible(Context context, PictureData pictureData, String id, boolean visible) {
        pictureData.setDataControl(id);
        if (visible) {
            if (!showWindowById(context, pictureData, id, false)) {
                updateGlobalVisibleState(context);
                NotificationService.refresh(context);
                OverlayRuntimeController.notifyRuntimeStateChanged(context);
                return;
            }
            pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
            finishVisibilityChangeAsync(context, id, true);
        } else {
            if (!detachWindowById(context, id, false)) {
                updateGlobalVisibleState(context);
                NotificationService.refresh(context);
                OverlayRuntimeController.notifyRuntimeStateChanged(context);
                return;
            }
            pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
            finishVisibilityChangeAsync(context, id, false);
        }
    }

    private static boolean hideWindowById(Context mContext, String id) {
        return detachWindowById(mContext, id, true);
    }

    private static boolean showWindowById(Context mContext, String id) {
        return showWindowById(mContext, id, true);
    }

    private static boolean showWindowById(Context mContext, String id, boolean syncAfterCreate) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        return showWindowById(mContext, pictureData, id, syncAfterCreate);
    }

    private static boolean showWindowById(Context mContext, PictureData pictureData, String id, boolean syncAfterCreate) {
        if (pictureData == null) {
            return showWindowById(mContext, id, syncAfterCreate);
        }
        pictureData.setDataControl(id);
        if (!ensurePictureContentForWindow(mContext, id, syncAfterCreate)) {
            return false;
        }
        Point position = OverlayRuntimeStateStore.getWindowPosition(
                mContext,
                id,
                pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X),
                pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y)
        );
        int positionX = position.x;
        int positionY = position.y;
        float zoom = getConfiguredZoom(mContext, pictureData, id);
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
            Bitmap displayBitmap = ImageMethods.getDisplayBitmapOrNull(
                    id,
                    zoom,
                    pictureDegree,
                    cornerRadiusRatio,
                    cornerRadiusMask,
                    edgeFeatherRatio,
                    edgeFeatherMask
            );
            if (displayBitmap == null) {
                releaseWindowById(mContext, id, syncAfterCreate);
                Log.w("ManageMethods", "showWindowById failed to load picture content: " + id);
                return false;
            }
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
        return WindowsMethods.createWindow(getWindowManager(mContext), floatImageView, touch_and_move, over_layout, pictureAlpha, positionX, positionY, syncAfterCreate);
    }

    private static void finishVisibilityChangeAsync(Context context, String id, boolean visible) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        AppExecutors.io().execute(() -> {
            PictureData asyncPictureData = new PictureData();
            asyncPictureData.setDataControl(id);
            asyncPictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
            boolean saved = asyncPictureData.commitData();
            ArrayList<String> changedIds = new ArrayList<>();
            changedIds.add(id);
            MAIN_HANDLER.post(() -> finishVisibilityWrite(appContext, saved, changedIds, null));
        });
    }

    private static void finishAllWindowsVisibilityChangeAsync(Context context,
                                                              LinkedHashMap<String, String> pictureList,
                                                              boolean visible) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashMap<String, String> pictureListSnapshot = new LinkedHashMap<>(pictureList);
        AppExecutors.io().execute(() -> {
            boolean saved = new PictureData().setAllPictureShowEnabled(pictureListSnapshot, visible);
            MAIN_HANDLER.post(() -> finishVisibilityWrite(appContext, saved, new ArrayList<>(pictureListSnapshot.keySet()), null));
        });
    }

    private static void finishVisibilityChangesAsync(Context context, Set<String> pictureIds, boolean visible) {
        finishVisibilityChangesAsync(context, pictureIds, visible, null);
    }

    private static void finishVisibilityChangesAsync(Context context, Set<String> pictureIds, boolean visible, Runnable completionCallback) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            runCompletionCallback(completionCallback);
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashSet<String> pictureIdSnapshot = new LinkedHashSet<>(pictureIds);
        AppExecutors.io().execute(() -> {
            boolean saved = new PictureData().setPictureShowEnabled(pictureIdSnapshot, visible);
            MAIN_HANDLER.post(() -> finishVisibilityWrite(appContext, saved, new ArrayList<>(pictureIdSnapshot), completionCallback));
        });
    }

    private static void finishVisibilityChangesAsync(Context context, LinkedHashMap<String, Boolean> visibilityById) {
        if (visibilityById == null || visibilityById.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        LinkedHashMap<String, Boolean> visibilitySnapshot = new LinkedHashMap<>(visibilityById);
        AppExecutors.io().execute(() -> {
            boolean saved = new PictureData().setPicturesShowEnabled(visibilitySnapshot);
            MAIN_HANDLER.post(() -> finishVisibilityWrite(appContext, saved, new ArrayList<>(visibilitySnapshot.keySet()), null));
        });
    }

    private static void finishVisibilityWrite(Context context,
                                              boolean saved,
                                              ArrayList<String> pictureIds,
                                              Runnable completionCallback) {
        if (saved) {
            finishBatchVisibilityChange(context);
            runCompletionCallback(completionCallback);
            return;
        }
        Log.w("ManageMethods", "Failed to persist window visibility change");
        syncWindowsFromDiskAsync(context, pictureIds, true, () -> {
            finishBatchVisibilityChange(context);
            runCompletionCallback(completionCallback);
        });
    }

    private static void finishBatchVisibilityChange(Context context) {
        WindowsMethods.syncAllWindows(context);
        updateGlobalVisibleState(context);
        NotificationService.refresh(context);
        OverlayRuntimeController.notifyRuntimeStateChanged(context);
    }

    private static void runCompletionCallback(Runnable completionCallback) {
        if (completionCallback == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            completionCallback.run();
            return;
        }
        MAIN_HANDLER.post(completionCallback);
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
        if (!ensurePictureContentForWindow(mContext, id, false)) {
            return null;
        }
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        long displayBitmapVersion = ImageMethods.getDisplayBitmapVersion(id);
        if (canReuseDisplayBitmap(floatImageView, displayBitmapVersion)) {
            return floatImageView;
        }
        float zoom = getConfiguredZoom(mContext, pictureData, id);
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
        Bitmap bitmap = ImageMethods.getDisplayBitmapOrNull(
                id,
                zoom,
                picture_degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (bitmap == null) {
            releaseWindowById(mContext, id, false);
            Log.w("ManageMethods", "ensureWindowView failed to load picture content: " + id);
            return null;
        }
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

    private static float getConfiguredZoom(Context context, PictureData pictureData, String id) {
        if (pictureData.has(Config.DATA_PICTURE_ZOOM)) {
            return pictureData.getFloat(Config.DATA_PICTURE_ZOOM, 1f);
        }
        return getDefaultZoom(context, pictureData, id);
    }

    private static boolean ensurePictureContentForWindow(Context context, String id, boolean syncAllWindows) {
        if (ImageMethods.hasAvailablePictureContent(id)) {
            return true;
        }
        releaseWindowById(context, id, syncAllWindows);
        Log.w("ManageMethods", "missing picture content for window: " + id);
        return false;
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

    private static final class StartupWindowRequest {
        private final String id;
        private final boolean contentAvailable;
        private final int positionX;
        private final int positionY;
        private final boolean touchAndMove;
        private final boolean overLayout;
        private final float pictureAlpha;
        private final float zoom;
        private final float degree;
        private final float cornerRadiusRatio;
        private final int cornerRadiusMask;
        private final float edgeFeatherRatio;
        private final int edgeFeatherMask;

        private StartupWindowRequest(String id,
                                     int positionX,
                                     int positionY,
                                     boolean touchAndMove,
                                     boolean overLayout,
                                     float pictureAlpha,
                                     float zoom,
                                     float degree,
                                     float cornerRadiusRatio,
                                     int cornerRadiusMask,
                                     float edgeFeatherRatio,
                                     int edgeFeatherMask) {
            this.id = id;
            this.contentAvailable = true;
            this.positionX = positionX;
            this.positionY = positionY;
            this.touchAndMove = touchAndMove;
            this.overLayout = overLayout;
            this.pictureAlpha = pictureAlpha;
            this.zoom = zoom;
            this.degree = degree;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
        }

        private StartupWindowRequest(String id) {
            this.id = id;
            this.contentAvailable = false;
            this.positionX = Config.DATA_DEFAULT_PICTURE_POSITION_X;
            this.positionY = Config.DATA_DEFAULT_PICTURE_POSITION_Y;
            this.touchAndMove = Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE;
            this.overLayout = Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT;
            this.pictureAlpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
            this.zoom = 1f;
            this.degree = Config.DATA_DEFAULT_PICTURE_DEGREE;
            this.cornerRadiusRatio = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO;
            this.cornerRadiusMask = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK;
            this.edgeFeatherRatio = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO;
            this.edgeFeatherMask = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK;
        }

        private static StartupWindowRequest missingContent(String id) {
            return new StartupWindowRequest(id);
        }
    }

}
