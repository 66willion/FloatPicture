package tool.xfy9326.floatpicture.Activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

final class MainActionController {
    interface ImportCallback {
        void onComplete(ArrayList<String> importedPictureIds);
    }

    interface RandomWindowCallback {
        void onComplete(int result);
    }

    interface ReleaseMemoryCallback {
        void onComplete(ApplicationMethods.MemoryReleaseResult result);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private MainActionController() {
    }

    static void importPictures(Context context,
                              List<Uri> selectedUris,
                              String defaultPictureName,
                              ImportCallback callback) {
        Context appContext = getAppContext(context);
        ArrayList<Uri> snapshotUris = new ArrayList<>(selectedUris);
        AppExecutors.io().execute(() -> {
            ArrayList<String> importedPictureIds = new ArrayList<>();
            for (Uri uri : snapshotUris) {
                if (uri == null) {
                    continue;
                }
                String pictureId = ImageMethods.setNewImage(appContext, uri);
                if (pictureId == null) {
                    continue;
                }
                if (importedPictureIds.contains(pictureId)) {
                    ImageMethods.clearAllTemp(appContext, pictureId);
                    continue;
                }
                if (initializeImportedPicture(appContext, pictureId, defaultPictureName)) {
                    importedPictureIds.add(pictureId);
                }
            }
            MAIN_HANDLER.post(() -> callback.onComplete(importedPictureIds));
        });
    }

    static void cleanupImportedPictures(Context context, ArrayList<String> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        Context appContext = getAppContext(context);
        AppExecutors.io().execute(() -> {
            for (String pictureId : pictureIds) {
                if (pictureId == null || pictureId.isEmpty()) {
                    continue;
                }
                try {
                    OverlayRuntimeController.deletePicture(appContext, pictureId);
                } catch (RuntimeException e) {
                    PictureData importedPictureData = new PictureData();
                    importedPictureData.setDataControl(pictureId);
                    if (importedPictureData.remove()) {
                        ImageMethods.clearAllTemp(appContext, pictureId);
                    }
                }
            }
        });
    }

    static void showRandomWindow(Context context, RandomWindowCallback callback) {
        Context appContext = getAppContext(context);
        OverlayRuntimeController.showRandomWindow(appContext, callback::onComplete);
    }

    static void releaseMemory(Context context, ReleaseMemoryCallback callback) {
        Context appContext = getAppContext(context);
        ApplicationMethods.releaseMemory(appContext, callback::onComplete);
    }

    private static boolean initializeImportedPicture(Context context, String pictureId, String pictureName) {
        Bitmap sourceBitmap = null;
        Bitmap displayBitmap = null;
        try {
            sourceBitmap = ImageMethods.getEditSourceBitmapOrNull(pictureId);
            if (sourceBitmap == null) {
                ImageMethods.clearAllTemp(context, pictureId);
                return false;
            }
            float defaultZoom = ImageMethods.getDefaultZoom(context, sourceBitmap, false);
            displayBitmap = ImageMethods.createAndSaveDisplayBitmap(
                    pictureId,
                    sourceBitmap,
                    defaultZoom,
                    Config.DATA_DEFAULT_PICTURE_DEGREE
            );
            if (displayBitmap == null) {
                ImageMethods.clearAllTemp(context, pictureId);
                return false;
            }
            if (initializeImportedPictureData(pictureId, pictureName, defaultZoom)) {
                return true;
            }
            ImageMethods.clearAllTemp(context, pictureId);
            return false;
        } catch (RuntimeException e) {
            e.printStackTrace();
            ImageMethods.clearAllTemp(context, pictureId);
            return false;
        } finally {
            if (displayBitmap != null && displayBitmap != sourceBitmap) {
                ImageMethods.recycleBitmap(displayBitmap);
            }
            ImageMethods.recycleBitmap(sourceBitmap);
        }
    }

    private static boolean initializeImportedPictureData(String pictureId, String pictureName, float defaultZoom) {
        PictureData importedPictureData = new PictureData();
        importedPictureData.setDataControl(pictureId);
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
        return importedPictureData.commit(pictureName);
    }

    private static Context getAppContext(Context context) {
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }
}
