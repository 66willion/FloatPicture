package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;

final class PictureSettingsSaveController {
    private static final int THREE_DECIMAL_SCALE = 1000;

    private PictureSettingsSaveController() {
    }

    static boolean saveBatch(@NonNull Context appContext, @NonNull BatchSaveRequest request) {
        boolean saveFailed = false;
        PictureData listPictureData = new PictureData();
        LinkedHashMap<String, String> listArray = listPictureData.getListArray();
        if (listArray == null) {
            return false;
        }

        for (String pictureId : request.pictureIds) {
            if (pictureId == null || !listArray.containsKey(pictureId)) {
                continue;
            }
            PictureData itemPictureData = new PictureData();
            itemPictureData.setDataControl(pictureId);
            float itemDefaultZoom = itemPictureData.getFloat(
                    Config.DATA_PICTURE_DEFAULT_ZOOM,
                    ImageMethods.getDefaultZoom(appContext, pictureId, false)
            );
            float itemZoom = itemPictureData.getFloat(Config.DATA_PICTURE_ZOOM, itemDefaultZoom);
            float itemDegree = itemPictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
            float itemAlpha = itemPictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
            float itemCornerRadiusRatio = itemPictureData.getFloat(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO);
            int itemCornerRadiusMask = itemPictureData.getInt(Config.DATA_PICTURE_CORNER_RADIUS_MASK, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK);
            float itemEdgeFeatherRatio = itemPictureData.getFloat(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO);
            int itemEdgeFeatherMask = itemPictureData.getInt(Config.DATA_PICTURE_EDGE_FEATHER_MASK, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK);
            int itemPositionX = itemPictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
            int itemPositionY = itemPictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);

            if (request.degreeChanged) {
                itemDegree = request.degree;
                itemPictureData.put(Config.DATA_PICTURE_DEGREE, itemDegree);
            }
            if (request.zoomChanged) {
                itemZoom = request.zoom;
                itemPictureData.put(Config.DATA_PICTURE_ZOOM, itemZoom);
            }
            if (request.fitScreenHeightChanged) {
                Bitmap sourceBitmap = ImageMethods.getEditSourceBitmap(appContext, pictureId);
                if (sourceBitmap == null || sourceBitmap.isRecycled()) {
                    saveFailed = true;
                } else {
                    float fittedZoom = resolveScreenHeightZoom(sourceBitmap, itemDegree, request.windowSize);
                    ImageMethods.recycleBitmap(sourceBitmap);
                    if (fittedZoom > 0f) {
                        itemZoom = fittedZoom;
                        itemPositionY = 0;
                        itemPictureData.put(Config.DATA_PICTURE_ZOOM, itemZoom);
                        itemPictureData.put(Config.DATA_PICTURE_POSITION_Y, itemPositionY);
                    } else {
                        saveFailed = true;
                    }
                }
            }
            if (request.alphaChanged) {
                itemAlpha = request.alpha;
                itemPictureData.put(Config.DATA_PICTURE_ALPHA, itemAlpha);
            }
            if (request.cornerRadiusChanged) {
                itemCornerRadiusRatio = request.cornerRadiusRatio;
                itemCornerRadiusMask = request.cornerRadiusMask;
                itemPictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, itemCornerRadiusRatio);
                itemPictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_MASK, itemCornerRadiusMask);
            }
            if (request.edgeFeatherChanged) {
                itemEdgeFeatherRatio = request.edgeFeatherRatio;
                itemEdgeFeatherMask = request.edgeFeatherMask;
                itemPictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, itemEdgeFeatherRatio);
                itemPictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_MASK, itemEdgeFeatherMask);
            }
            if (request.positionChanged) {
                itemPositionX = request.positionX;
                itemPositionY = request.positionY;
                itemPictureData.put(Config.DATA_PICTURE_POSITION_X, itemPositionX);
                itemPictureData.put(Config.DATA_PICTURE_POSITION_Y, itemPositionY);
            }
            if (request.touchAndMoveChanged) {
                itemPictureData.put(Config.DATA_PICTURE_TOUCH_AND_MOVE, request.touchAndMove);
            }
            if (request.overLayoutChanged) {
                itemPictureData.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, request.overLayout);
            }
            itemPictureData.commit(null);

            if (request.needsDisplayCacheRebuild()) {
                Bitmap displayBitmap = ImageMethods.createAndSaveDisplayBitmap(
                        pictureId,
                        itemZoom,
                        itemDegree,
                        itemCornerRadiusRatio,
                        itemCornerRadiusMask,
                        itemEdgeFeatherRatio,
                        itemEdgeFeatherMask
                );
                ImageMethods.recycleBitmap(displayBitmap);
            }
            if (request.positionChanged || request.fitScreenHeightChanged) {
                OverlayRuntimeStateStore.clearWindowPosition(appContext, pictureId);
            }
            OverlayRuntimeController.syncPicture(appContext, pictureId, false);
        }
        return !saveFailed;
    }

    static boolean saveSingle(@NonNull Context appContext, @NonNull SingleSaveRequest request) {
        if (!ImageMethods.commitPendingReplacementImage(request.pictureId)) {
            return false;
        }
        request.applyToPictureData();
        request.pictureData.commit(request.pictureName);
        if (request.positionChanged) {
            OverlayRuntimeStateStore.clearWindowPosition(appContext, request.pictureId);
        }
        Bitmap displayBitmap = ImageMethods.createAndSaveDisplayBitmap(
                request.pictureId,
                request.zoom,
                request.degree,
                request.cornerRadiusRatio,
                request.cornerRadiusMask,
                request.edgeFeatherRatio,
                request.edgeFeatherMask
        );
        ImageMethods.recycleBitmap(displayBitmap);
        OverlayRuntimeController.finishPreview(appContext, request.pictureId);
        return true;
    }

    static float resolveScreenHeightZoom(@NonNull Bitmap sourceBitmap, float degreeValue, @NonNull Point windowSize) {
        if (windowSize.y <= 0) {
            return 0f;
        }
        double radians = Math.toRadians(degreeValue);
        double rotatedBaseHeight = (Math.abs(sourceBitmap.getHeight() * Math.cos(radians))
                + Math.abs(sourceBitmap.getWidth() * Math.sin(radians)));
        if (rotatedBaseHeight <= 0d) {
            return 0f;
        }
        return Math.max(roundToThreeDecimals((float) (windowSize.y / rotatedBaseHeight)), 0.01f);
    }

    private static float roundToThreeDecimals(float value) {
        return Math.round(value * THREE_DECIMAL_SCALE) / (float) THREE_DECIMAL_SCALE;
    }

    static final class BatchSaveRequest {
        private final ArrayList<String> pictureIds;
        private final boolean zoomChanged;
        private final boolean fitScreenHeightChanged;
        private final boolean degreeChanged;
        private final boolean alphaChanged;
        private final boolean cornerRadiusChanged;
        private final boolean edgeFeatherChanged;
        private final boolean positionChanged;
        private final boolean touchAndMoveChanged;
        private final boolean overLayoutChanged;
        private final float zoom;
        private final float degree;
        private final float alpha;
        private final float cornerRadiusRatio;
        private final int cornerRadiusMask;
        private final float edgeFeatherRatio;
        private final int edgeFeatherMask;
        private final int positionX;
        private final int positionY;
        private final boolean touchAndMove;
        private final boolean overLayout;
        private final Point windowSize;

        BatchSaveRequest(ArrayList<String> pictureIds,
                         boolean zoomChanged,
                         boolean fitScreenHeightChanged,
                         boolean degreeChanged,
                         boolean alphaChanged,
                         boolean cornerRadiusChanged,
                         boolean edgeFeatherChanged,
                         boolean positionChanged,
                         boolean touchAndMoveChanged,
                         boolean overLayoutChanged,
                         float zoom,
                         float degree,
                         float alpha,
                         float cornerRadiusRatio,
                         int cornerRadiusMask,
                         float edgeFeatherRatio,
                         int edgeFeatherMask,
                         int positionX,
                         int positionY,
                         boolean touchAndMove,
                         boolean overLayout,
                         Point windowSize) {
            this.pictureIds = pictureIds;
            this.zoomChanged = zoomChanged;
            this.fitScreenHeightChanged = fitScreenHeightChanged;
            this.degreeChanged = degreeChanged;
            this.alphaChanged = alphaChanged;
            this.cornerRadiusChanged = cornerRadiusChanged;
            this.edgeFeatherChanged = edgeFeatherChanged;
            this.positionChanged = positionChanged;
            this.touchAndMoveChanged = touchAndMoveChanged;
            this.overLayoutChanged = overLayoutChanged;
            this.zoom = zoom;
            this.degree = degree;
            this.alpha = alpha;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
            this.positionX = positionX;
            this.positionY = positionY;
            this.touchAndMove = touchAndMove;
            this.overLayout = overLayout;
            this.windowSize = windowSize;
        }

        private boolean needsDisplayCacheRebuild() {
            return zoomChanged
                    || fitScreenHeightChanged
                    || degreeChanged
                    || cornerRadiusChanged
                    || edgeFeatherChanged;
        }
    }

    static final class SingleSaveRequest {
        private final PictureData pictureData;
        private final String pictureId;
        private final String pictureName;
        final float zoom;
        final float defaultZoom;
        final float degree;
        final float alpha;
        final float cornerRadiusRatio;
        final int cornerRadiusMask;
        final float edgeFeatherRatio;
        final int edgeFeatherMask;
        final int positionX;
        final int positionY;
        final boolean touchAndMove;
        final boolean overLayout;
        final boolean wasHidden;
        final boolean positionChanged;

        SingleSaveRequest(PictureData pictureData,
                          String pictureId,
                          String pictureName,
                          float zoom,
                          float defaultZoom,
                          float degree,
                          float alpha,
                          float cornerRadiusRatio,
                          int cornerRadiusMask,
                          float edgeFeatherRatio,
                          int edgeFeatherMask,
                          int positionX,
                          int positionY,
                          boolean touchAndMove,
                          boolean overLayout,
                          boolean wasHidden,
                          boolean positionChanged) {
            this.pictureData = pictureData;
            this.pictureId = pictureId;
            this.pictureName = pictureName;
            this.zoom = zoom;
            this.defaultZoom = defaultZoom;
            this.degree = degree;
            this.alpha = alpha;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
            this.positionX = positionX;
            this.positionY = positionY;
            this.touchAndMove = touchAndMove;
            this.overLayout = overLayout;
            this.wasHidden = wasHidden;
            this.positionChanged = positionChanged;
        }

        private void applyToPictureData() {
            pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, !wasHidden);
            pictureData.put(Config.DATA_PICTURE_ZOOM, zoom);
            pictureData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, defaultZoom);
            pictureData.put(Config.DATA_PICTURE_ALPHA, alpha);
            pictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, cornerRadiusRatio);
            pictureData.put(Config.DATA_PICTURE_CORNER_RADIUS_MASK, cornerRadiusMask);
            pictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, edgeFeatherRatio);
            pictureData.put(Config.DATA_PICTURE_EDGE_FEATHER_MASK, edgeFeatherMask);
            pictureData.put(Config.DATA_PICTURE_POSITION_X, positionX);
            pictureData.put(Config.DATA_PICTURE_POSITION_Y, positionY);
            pictureData.put(Config.DATA_PICTURE_DEGREE, degree);
            pictureData.put(Config.DATA_PICTURE_TOUCH_AND_MOVE, touchAndMove);
            pictureData.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, overLayout);
        }
    }
}
