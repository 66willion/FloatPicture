package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

final class PictureSettingsPreviewController {
    private PictureSettingsPreviewController() {
    }

    static void showPreview(@NonNull Context context,
                            @NonNull String pictureId,
                            @NonNull PreviewRequest request) {
        Point previewPosition = request.useRuntimePosition
                ? getPreviewPosition(context, pictureId, request.positionX, request.positionY)
                : new Point(request.positionX, request.positionY);
        OverlayRuntimeController.updatePreview(
                context,
                pictureId,
                request.zoom,
                request.degree,
                request.alpha,
                request.cornerRadiusRatio,
                request.cornerRadiusMask,
                request.edgeFeatherRatio,
                request.edgeFeatherMask,
                previewPosition.x,
                previewPosition.y,
                request.touchAndMove,
                request.overLayout,
                request.previewMode,
                request.reloadSource
        );
    }

    @NonNull
    static Point getPreviewPosition(@NonNull Context context,
                                    @Nullable String pictureId,
                                    int fallbackX,
                                    int fallbackY) {
        if (pictureId == null || pictureId.isEmpty()) {
            return new Point(fallbackX, fallbackY);
        }
        return OverlayRuntimeStateStore.getWindowPosition(context, pictureId, fallbackX, fallbackY);
    }

    static final class PreviewRequest {
        final float zoom;
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
        final int previewMode;
        final boolean reloadSource;
        final boolean useRuntimePosition;

        PreviewRequest(float zoom,
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
                       int previewMode,
                       boolean reloadSource,
                       boolean useRuntimePosition) {
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
            this.previewMode = previewMode;
            this.reloadSource = reloadSource;
            this.useRuntimePosition = useRuntimePosition;
        }
    }
}
