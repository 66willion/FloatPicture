package tool.xfy9326.floatpicture.Methods;


import static tool.xfy9326.floatpicture.Methods.WindowsMethods.getWindowManager;
import android.content.Context;
import android.util.Log;
import android.graphics.Bitmap;
import android.view.View;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Services.NotificationService;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;


public class ManageMethods {
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
        pictureData.remove();
        ImageMethods.clearAllTemp(mContext, id);
        updateGlobalVisibleState(mContext);
        NotificationService.refresh(mContext);
        return true;
    }

    public static void CloseAllWindows(Context mContext) {
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        Map<String, View> hashMap = new LinkedHashMap<>(mainApplication.getRegister());
        if (!hashMap.isEmpty()) {
            WindowManager windowManager = getWindowManager(mContext);
            for (HashMap.Entry<?, ?> entry : hashMap.entrySet()) {
                if (entry.getValue() instanceof FloatImageView floatImageView) {
                    if (WindowsMethods.removeWindowIfAttached(floatImageView)) {
                        ImageMethods.releasePictureView(floatImageView);
                        mainApplication.unregisterView(entry.getKey().toString());
                    }
                }
            }
        }
        mainApplication.setWinVisible(false);
    }

    public static int releaseInactiveWindowMemory(Context context) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        Map<String, View> registeredViews = new LinkedHashMap<>(mainApplication.getRegister());
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
                } else {
                    pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                    pictureData.commit(null);
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
        LinkedHashMap<String, String> list = new PictureData().getListArray();
        return list != null ? list.size() : 0;
    }

    public static boolean hasVisibleWindowsConfigured(Context context) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : linkedHashMap.entrySet()) {
            pictureData.setDataControl(entry.getKey().toString());
            if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                return true;
            }
        }
        return false;
    }

    public static void setAllWindowsVisible(Context context, boolean visible) {
        String id;
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        if (linkedHashMap == null || linkedHashMap.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return;
        }
        for (Map.Entry<?, ?> o : linkedHashMap.entrySet()) {
            id = o.getKey().toString();
            setWindowVisible(context, pictureData, id, visible);
        }
    }

    public static void syncWindowFromDisk(Context context, String id) {
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
            showWindowById(context, id);
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
                pictureData.commit(null);
                showWindowById(context, id);
                updateGlobalVisibleState(context);
                NotificationService.refresh(context);
            }
        } else {
            if (data_visible) {
                if (hideWindowById(context, id)) {
                    pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
                    pictureData.commit(null);
                    updateGlobalVisibleState(context);
                    NotificationService.refresh(context);
                }
            }
        }
    }

    private static boolean hideWindowById(Context mContext, String id) {
        return releaseWindowById(mContext, id, true);
    }

    private static void showWindowById(Context mContext, String id) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        int positionX = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int positionY = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        float defaultZoom = pictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, ImageMethods.getDefaultZoom(mContext, id, false));
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom);
        float pictureDegree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        Bitmap displayBitmap = ImageMethods.getDisplayBitmap(mContext, id, zoom, pictureDegree);
        if (floatImageView == null) {
            float pictureAlpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
            boolean touchAndMove = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
            boolean overLayout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
            floatImageView = ImageMethods.createPictureView(mContext, displayBitmap, touchAndMove, overLayout, pictureAlpha);
            ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
        } else {
            ImageMethods.setPictureBitmap(floatImageView, displayBitmap);
        }
        float pictureAlpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        syncWindowViewState(floatImageView, touch_and_move, over_layout, pictureAlpha);
        OverlayRuntimeStateStore.saveWindowPosition(mContext, id, positionX, positionY);
        // createWindow 内部会自动调用 syncAllWindows 重新平衡所有窗口的联合透明度
        WindowsMethods.createWindow(getWindowManager(mContext), floatImageView, touch_and_move, over_layout, pictureAlpha, positionX, positionY);
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
        if (floatImageView != null) {
            return floatImageView;
        }
        float default_zoom = pictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, ImageMethods.getDefaultZoom(mContext, id, false));
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
        float picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        float picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        Bitmap bitmap = ImageMethods.getDisplayBitmap(mContext, id, zoom, picture_degree);
        floatImageView = ImageMethods.createPictureView(mContext, bitmap, touch_and_move, over_layout, picture_alpha);
        ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
        return floatImageView;
    }

    private static void syncWindowViewState(FloatImageView floatImageView, boolean touchAndMove, boolean overLayout, float pictureAlpha) {
        if (floatImageView == null) {
            return;
        }
        floatImageView.setMoveable(touchAndMove);
        floatImageView.setOverLayout(overLayout);
        floatImageView.setPictureAlpha(pictureAlpha);
    }

    private static void cleanupRegisteredWindows(Context context, LinkedHashMap<String, String> pictureList) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        Map<String, View> registeredViews = new LinkedHashMap<>(mainApplication.getRegister());
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
                if (!releaseWindowById(context, id, false)) {
                    pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                    pictureData.commit(null);
                }
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
            return false;
        }
        ImageMethods.releasePictureView(floatImageView);
        mainApplication.unregisterView(id);
        if (syncAllWindows) {
            WindowsMethods.syncAllWindows(context);
        }
        return true;
    }

}
