package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

final class PictureSettingsLoader {
    private PictureSettingsLoader() {
    }

    @Nullable
    static LoadedSettings loadSingle(@NonNull Context appContext,
                                     @NonNull Intent intent,
                                     @NonNull PictureData pictureData,
                                     boolean editMode,
                                     @NonNull String newPictureName) {
        if (editMode) {
            return loadEdit(appContext, intent, pictureData);
        }
        return loadNew(appContext, intent.getData(), pictureData, newPictureName);
    }

    @Nullable
    static LoadedSettings loadBatch(@NonNull Intent intent,
                                    @NonNull PictureData pictureData,
                                    @NonNull String batchLabel) {
        ArrayList<String> requestedIds = intent.getStringArrayListExtra(Config.INTENT_PICTURE_BATCH_EDIT_IDS);
        LinkedHashMap<String, String> listArray = pictureData.getListArray();
        ArrayList<String> batchPictureIds = new ArrayList<>();
        if (requestedIds != null && listArray != null) {
            for (String pictureId : requestedIds) {
                if (pictureId != null && listArray.containsKey(pictureId) && !batchPictureIds.contains(pictureId)) {
                    batchPictureIds.add(pictureId);
                }
            }
        }
        if (batchPictureIds.isEmpty()) {
            return null;
        }
        pictureData.setDataControl(batchPictureIds.get(0));
        float defaultZoom = 1f;
        return LoadedSettings.batch(
                batchLabel,
                batchPictureIds,
                readSnapshot(pictureData, defaultZoom, pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom))
        );
    }

    @Nullable
    static Bitmap loadCurrentSourceBitmap(@NonNull Context context, boolean editMode, @Nullable String pictureId) {
        if (editMode && pictureId != null) {
            Bitmap pendingBitmap = ImageMethods.getPendingEditSourceBitmap(pictureId);
            if (pendingBitmap != null) {
                return pendingBitmap;
            }
        }
        return ImageMethods.getEditSourceBitmap(context, pictureId);
    }

    @Nullable
    private static LoadedSettings loadEdit(@NonNull Context appContext,
                                           @NonNull Intent intent,
                                           @NonNull PictureData pictureData) {
        String pictureId = intent.getStringExtra(Config.INTENT_PICTURE_EDIT_ID);
        if (pictureId == null) {
            return null;
        }
        pictureData.setDataControl(pictureId);
        LinkedHashMap<String, String> listArray = pictureData.getListArray();
        if (listArray == null || !listArray.containsKey(pictureId)) {
            return null;
        }
        Bitmap bitmap = loadCurrentSourceBitmap(appContext, true, pictureId);
        if (bitmap == null) {
            return null;
        }
        float defaultZoom = ImageMethods.getDefaultZoom(appContext, bitmap, false);
        SettingsSnapshot snapshot = readSnapshot(
                pictureData,
                defaultZoom,
                pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom)
        );
        return LoadedSettings.single(pictureId, listArray.get(pictureId), bitmap, snapshot, false);
    }

    @Nullable
    private static LoadedSettings loadNew(@NonNull Context appContext,
                                          @Nullable Uri pictureUri,
                                          @NonNull PictureData pictureData,
                                          @NonNull String newPictureName) {
        String pictureId = ImageMethods.setNewImage(appContext, pictureUri);
        if (pictureId == null) {
            return null;
        }
        pictureData.setDataControl(pictureId);
        Bitmap bitmap = ImageMethods.getEditSourceBitmap(appContext, pictureId);
        if (bitmap == null) {
            ImageMethods.clearAllTemp(appContext, pictureId);
            return null;
        }
        float defaultZoom = ImageMethods.getDefaultZoom(appContext, bitmap, false);
        SettingsSnapshot snapshot = SettingsSnapshot.defaults(defaultZoom);
        return LoadedSettings.single(pictureId, newPictureName, bitmap, snapshot, true);
    }

    @NonNull
    private static SettingsSnapshot readSnapshot(@NonNull PictureData pictureData, float defaultZoom, float zoom) {
        return new SettingsSnapshot(
                pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X),
                pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y),
                pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE),
                pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA),
                pictureData.getFloat(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO),
                pictureData.getInt(Config.DATA_PICTURE_CORNER_RADIUS_MASK, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK),
                pictureData.getFloat(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO),
                pictureData.getInt(Config.DATA_PICTURE_EDGE_FEATHER_MASK, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK),
                pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE),
                pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT),
                defaultZoom,
                zoom
        );
    }

    static final class LoadedSettings {
        @Nullable
        final String pictureId;
        @NonNull
        final String pictureName;
        @Nullable
        final Bitmap bitmap;
        @NonNull
        final SettingsSnapshot snapshot;
        @Nullable
        final ArrayList<String> batchPictureIds;
        final boolean newPicture;

        private LoadedSettings(@Nullable String pictureId,
                               @NonNull String pictureName,
                               @Nullable Bitmap bitmap,
                               @NonNull SettingsSnapshot snapshot,
                               @Nullable ArrayList<String> batchPictureIds,
                               boolean newPicture) {
            this.pictureId = pictureId;
            this.pictureName = pictureName;
            this.bitmap = bitmap;
            this.snapshot = snapshot;
            this.batchPictureIds = batchPictureIds;
            this.newPicture = newPicture;
        }

        @NonNull
        static LoadedSettings single(@NonNull String pictureId,
                                     @NonNull String pictureName,
                                     @NonNull Bitmap bitmap,
                                     @NonNull SettingsSnapshot snapshot,
                                     boolean newPicture) {
            return new LoadedSettings(pictureId, pictureName, bitmap, snapshot, null, newPicture);
        }

        @NonNull
        static LoadedSettings batch(@NonNull String pictureName,
                                    @NonNull ArrayList<String> batchPictureIds,
                                    @NonNull SettingsSnapshot snapshot) {
            return new LoadedSettings(null, pictureName, null, snapshot, batchPictureIds, false);
        }
    }

    static final class SettingsSnapshot {
        final int positionX;
        final int positionY;
        final float pictureDegree;
        final float pictureAlpha;
        final float cornerRadiusRatio;
        final int cornerRadiusMask;
        final float edgeFeatherRatio;
        final int edgeFeatherMask;
        final boolean touchAndMove;
        final boolean allowPictureOverLayout;
        final float defaultZoom;
        final float zoom;

        private SettingsSnapshot(int positionX,
                                 int positionY,
                                 float pictureDegree,
                                 float pictureAlpha,
                                 float cornerRadiusRatio,
                                 int cornerRadiusMask,
                                 float edgeFeatherRatio,
                                 int edgeFeatherMask,
                                 boolean touchAndMove,
                                 boolean allowPictureOverLayout,
                                 float defaultZoom,
                                 float zoom) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.pictureDegree = pictureDegree;
            this.pictureAlpha = pictureAlpha;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
            this.touchAndMove = touchAndMove;
            this.allowPictureOverLayout = allowPictureOverLayout;
            this.defaultZoom = defaultZoom;
            this.zoom = zoom;
        }

        @NonNull
        static SettingsSnapshot defaults(float defaultZoom) {
            return new SettingsSnapshot(
                    Config.DATA_DEFAULT_PICTURE_POSITION_X,
                    Config.DATA_DEFAULT_PICTURE_POSITION_Y,
                    Config.DATA_DEFAULT_PICTURE_DEGREE,
                    Config.DATA_DEFAULT_PICTURE_ALPHA,
                    Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                    Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                    Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO,
                    Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK,
                    Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE,
                    Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT,
                    defaultZoom,
                    defaultZoom
            );
        }
    }
}
