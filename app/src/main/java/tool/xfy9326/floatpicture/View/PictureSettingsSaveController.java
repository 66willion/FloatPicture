package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
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
        PictureData listPictureData = new PictureData();
        LinkedHashMap<String, String> listArray = listPictureData.getListArray();
        if (listArray == null) {
            return false;
        }

        LinkedHashMap<String, LinkedHashMap<String, Object>> dirtyValuesById = new LinkedHashMap<>();
        LinkedHashMap<String, BatchDisplayCacheRequest> displayCacheRequests = new LinkedHashMap<>();
        ArrayList<String> positionResetIds = new ArrayList<>();
        ArrayList<String> syncPictureIds = new ArrayList<>();
        for (String pictureId : request.pictureIds) {
            if (pictureId == null || !listArray.containsKey(pictureId)) {
                continue;
            }
            PictureData itemPictureData = new PictureData();
            itemPictureData.setDataControl(pictureId);
            LinkedHashMap<String, Object> dirtyValues = new LinkedHashMap<>();
            boolean needsDisplayCacheRebuild = request.needsDisplayCacheRebuild();
            float itemZoom = (request.zoomChanged || request.fitScreenHeightChanged || !needsDisplayCacheRebuild)
                    ? 1f
                    : resolveItemZoom(appContext, itemPictureData, pictureId);
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
                dirtyValues.put(Config.DATA_PICTURE_DEGREE, itemDegree);
            }
            if (request.zoomChanged) {
                itemZoom = request.zoom;
                dirtyValues.put(Config.DATA_PICTURE_ZOOM, itemZoom);
            }
            if (request.fitScreenHeightChanged) {
                Point sourceSize = ImageMethods.getSourceBitmapSize(pictureId);
                if (sourceSize == null || sourceSize.x <= 0 || sourceSize.y <= 0) {
                    return false;
                }
                float fittedZoom = resolveScreenHeightZoom(sourceSize, itemDegree, request.windowSize);
                if (fittedZoom > 0f) {
                    itemZoom = fittedZoom;
                    itemPositionY = 0;
                    dirtyValues.put(Config.DATA_PICTURE_ZOOM, itemZoom);
                    dirtyValues.put(Config.DATA_PICTURE_POSITION_Y, itemPositionY);
                } else {
                    return false;
                }
            }
            if (request.alphaChanged) {
                itemAlpha = request.alpha;
                dirtyValues.put(Config.DATA_PICTURE_ALPHA, itemAlpha);
            }
            if (request.cornerRadiusChanged) {
                itemCornerRadiusRatio = request.cornerRadiusRatio;
                itemCornerRadiusMask = request.cornerRadiusMask;
                dirtyValues.put(Config.DATA_PICTURE_CORNER_RADIUS_RATIO, itemCornerRadiusRatio);
                dirtyValues.put(Config.DATA_PICTURE_CORNER_RADIUS_MASK, itemCornerRadiusMask);
            }
            if (request.edgeFeatherChanged) {
                itemEdgeFeatherRatio = request.edgeFeatherRatio;
                itemEdgeFeatherMask = request.edgeFeatherMask;
                dirtyValues.put(Config.DATA_PICTURE_EDGE_FEATHER_RATIO, itemEdgeFeatherRatio);
                dirtyValues.put(Config.DATA_PICTURE_EDGE_FEATHER_MASK, itemEdgeFeatherMask);
            }
            if (request.positionChanged) {
                itemPositionX = request.positionX;
                itemPositionY = request.positionY;
                dirtyValues.put(Config.DATA_PICTURE_POSITION_X, itemPositionX);
                dirtyValues.put(Config.DATA_PICTURE_POSITION_Y, itemPositionY);
            }
            if (request.touchAndMoveChanged) {
                dirtyValues.put(Config.DATA_PICTURE_TOUCH_AND_MOVE, request.touchAndMove);
            }
            if (request.overLayoutChanged) {
                dirtyValues.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, request.overLayout);
            }
            if (!dirtyValues.isEmpty()) {
                dirtyValuesById.put(pictureId, dirtyValues);
            }
            if (needsDisplayCacheRebuild) {
                if (!request.fitScreenHeightChanged && ImageMethods.getSourceBitmapSize(pictureId) == null) {
                    return false;
                }
                displayCacheRequests.put(pictureId, new BatchDisplayCacheRequest(
                        pictureId,
                        itemZoom,
                        itemDegree,
                        itemCornerRadiusRatio,
                        itemCornerRadiusMask,
                        itemEdgeFeatherRatio,
                        itemEdgeFeatherMask
                ));
            }
            if (request.positionChanged || request.fitScreenHeightChanged) {
                positionResetIds.add(pictureId);
            }
            syncPictureIds.add(pictureId);
        }

        if (!clearDisplayCaches(displayCacheRequests.values())) {
            return false;
        }
        if (!PictureData.updatePictureValues(dirtyValuesById)) {
            return false;
        }

        for (String pictureId : positionResetIds) {
            OverlayRuntimeStateStore.clearWindowPosition(appContext, pictureId);
        }
        if (displayCacheRequests.isEmpty()) {
            OverlayRuntimeController.syncPictures(appContext, syncPictureIds, false);
        } else {
            scheduleDisplayCacheRebuilds(appContext, displayCacheRequests.values());
        }
        return true;
    }

    static boolean saveSingle(@NonNull Context appContext, @NonNull SingleSaveRequest request) {
        if (ImageMethods.hasPendingReplacementImage(request.pictureId)) {
            Bitmap pendingBitmap = ImageMethods.getPendingEditSourceBitmap(request.pictureId);
            if (pendingBitmap == null) {
                return false;
            }
            ImageMethods.recycleBitmap(pendingBitmap);
        }
        if (!ImageMethods.commitPendingReplacementImage(request.pictureId)) {
            return false;
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
        if (displayBitmap == null) {
            return false;
        }
        try {
            request.applyToPictureData();
            if (!request.pictureData.commit(request.pictureName)) {
                ImageMethods.clearDisplayBitmapCache(request.pictureId);
                return false;
            }
            if (request.positionChanged) {
                OverlayRuntimeStateStore.clearWindowPosition(appContext, request.pictureId);
            }
            OverlayRuntimeController.finishPreview(appContext, request.pictureId);
            return true;
        } finally {
            ImageMethods.recycleBitmap(displayBitmap);
        }
    }

    static float resolveScreenHeightZoom(@NonNull Bitmap sourceBitmap, float degreeValue, @NonNull Point windowSize) {
        return resolveScreenHeightZoom(sourceBitmap.getWidth(), sourceBitmap.getHeight(), degreeValue, windowSize);
    }

    static float resolveScreenHeightZoom(@NonNull Point sourceSize, float degreeValue, @NonNull Point windowSize) {
        return resolveScreenHeightZoom(sourceSize.x, sourceSize.y, degreeValue, windowSize);
    }

    private static float resolveScreenHeightZoom(int sourceWidth,
                                                 int sourceHeight,
                                                 float degreeValue,
                                                 @NonNull Point windowSize) {
        if (windowSize.y <= 0) {
            return 0f;
        }
        double radians = Math.toRadians(degreeValue);
        double rotatedBaseHeight = (Math.abs(sourceHeight * Math.cos(radians))
                + Math.abs(sourceWidth * Math.sin(radians)));
        if (rotatedBaseHeight <= 0d) {
            return 0f;
        }
        return Math.max(roundToThreeDecimals((float) (windowSize.y / rotatedBaseHeight)), 0.01f);
    }

    private static float roundToThreeDecimals(float value) {
        return Math.round(value * THREE_DECIMAL_SCALE) / (float) THREE_DECIMAL_SCALE;
    }

    private static float resolveItemZoom(@NonNull Context appContext,
                                         @NonNull PictureData itemPictureData,
                                         @NonNull String pictureId) {
        float itemDefaultZoom = itemPictureData.has(Config.DATA_PICTURE_DEFAULT_ZOOM)
                ? itemPictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, 1f)
                : ImageMethods.getDefaultZoom(appContext, pictureId, false);
        return itemPictureData.getFloat(Config.DATA_PICTURE_ZOOM, itemDefaultZoom);
    }

    private static boolean clearDisplayCaches(Collection<BatchDisplayCacheRequest> displayCacheRequests) {
        for (BatchDisplayCacheRequest displayCacheRequest : displayCacheRequests) {
            if (!ImageMethods.clearDisplayBitmapCache(displayCacheRequest.pictureId)) {
                return false;
            }
        }
        return true;
    }

    private static void scheduleDisplayCacheRebuilds(@NonNull Context appContext,
                                                     Collection<BatchDisplayCacheRequest> displayCacheRequests) {
        for (BatchDisplayCacheRequest displayCacheRequest : displayCacheRequests) {
            ImageMethods.rebuildDisplayBitmapAsync(
                    appContext,
                    displayCacheRequest.pictureId,
                    displayCacheRequest.zoom,
                    displayCacheRequest.degree,
                    displayCacheRequest.cornerRadiusRatio,
                    displayCacheRequest.cornerRadiusMask,
                    displayCacheRequest.edgeFeatherRatio,
                    displayCacheRequest.edgeFeatherMask
            );
        }
    }

    private static final class BatchDisplayCacheRequest {
        private final String pictureId;
        private final float zoom;
        private final float degree;
        private final float cornerRadiusRatio;
        private final int cornerRadiusMask;
        private final float edgeFeatherRatio;
        private final int edgeFeatherMask;

        private BatchDisplayCacheRequest(String pictureId,
                                         float zoom,
                                         float degree,
                                         float cornerRadiusRatio,
                                         int cornerRadiusMask,
                                         float edgeFeatherRatio,
                                         int edgeFeatherMask) {
            this.pictureId = pictureId;
            this.zoom = zoom;
            this.degree = degree;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
        }
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
