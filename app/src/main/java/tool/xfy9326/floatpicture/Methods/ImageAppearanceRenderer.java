package tool.xfy9326.floatpicture.Methods;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

import tool.xfy9326.floatpicture.Utils.Config;

final class ImageAppearanceRenderer {
    private static final String TAG = "ImageAppearanceRenderer";
    private static final float OUTLINE_OUTER_STROKE_MIN_PX = 3f;
    private static final float OUTLINE_OUTER_STROKE_MAX_PX = 8f;
    private static final float OUTLINE_INNER_STROKE_MIN_PX = 1.5f;
    private static final float OUTLINE_INNER_STROKE_MAX_PX = 4f;
    private static final int OUTLINE_OUTER_COLOR = 0xB0000000;
    private static final int OUTLINE_INNER_COLOR = 0xF2FFFFFF;

    private ImageAppearanceRenderer() {
    }

    static Bitmap createOutlinePreviewBitmap(Bitmap bitmap,
                                             float zoom,
                                             float degree,
                                             float cornerRadiusRatio,
                                             int cornerRadiusMask) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int targetWidth = getDisplayTargetSize(bitmap.getWidth(), zoom);
        int targetHeight = getDisplayTargetSize(bitmap.getHeight(), zoom);
        float shortEdge = Math.max(Math.min(targetWidth, targetHeight), 1);
        float outerStrokeWidth = clampValue(shortEdge * 0.01f, OUTLINE_OUTER_STROKE_MIN_PX, OUTLINE_OUTER_STROKE_MAX_PX);
        float innerStrokeWidth = clampValue(shortEdge * 0.005f, OUTLINE_INNER_STROKE_MIN_PX, OUTLINE_INNER_STROKE_MAX_PX);
        float normalizedDegree = normalizeDegree(degree);

        RectF sourceRect = new RectF(-targetWidth / 2f, -targetHeight / 2f, targetWidth / 2f, targetHeight / 2f);
        RectF boundsRect = new RectF();
        Matrix matrix = new Matrix();
        matrix.setRotate(normalizedDegree);
        matrix.mapRect(boundsRect, sourceRect);

        int padding = Math.max(4, (int) Math.ceil(outerStrokeWidth) + 2);
        int bitmapWidth = resolveBitmapDimension(boundsRect.width(), padding);
        int bitmapHeight = resolveBitmapDimension(boundsRect.height(), padding);
        Bitmap outlineBitmap = createBitmapSafely(bitmapWidth, bitmapHeight, "createOutlinePreviewBitmap");
        if (outlineBitmap == null) {
            return null;
        }
        try {
            Canvas canvas = new Canvas(outlineBitmap);
            canvas.translate(bitmapWidth / 2f, bitmapHeight / 2f);
            if (normalizedDegree != 0f) {
                canvas.rotate(normalizedDegree);
            }

            RectF drawRect = new RectF(
                    sourceRect.left + (outerStrokeWidth / 2f),
                    sourceRect.top + (outerStrokeWidth / 2f),
                    sourceRect.right - (outerStrokeWidth / 2f),
                    sourceRect.bottom - (outerStrokeWidth / 2f)
            );
            float cornerRadiusPx = clampAppearanceRatio(cornerRadiusRatio) * shortEdge;
            float maxCornerRadius = Math.min(drawRect.width(), drawRect.height()) / 2f;
            cornerRadiusPx = Math.min(cornerRadiusPx, maxCornerRadius);
            float[] radii = buildCornerRadii(cornerRadiusPx, cornerRadiusMask);

            Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            outerPaint.setStyle(Paint.Style.STROKE);
            outerPaint.setStrokeWidth(outerStrokeWidth);
            outerPaint.setColor(OUTLINE_OUTER_COLOR);
            outerPaint.setStrokeJoin(Paint.Join.ROUND);

            Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            innerPaint.setStyle(Paint.Style.STROKE);
            innerPaint.setStrokeWidth(innerStrokeWidth);
            innerPaint.setColor(OUTLINE_INNER_COLOR);
            innerPaint.setStrokeJoin(Paint.Join.ROUND);

            Path outlinePath = buildRoundRectPath(drawRect, radii);
            canvas.drawPath(outlinePath, outerPaint);
            canvas.drawPath(outlinePath, innerPaint);
            return outlineBitmap;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to draw outline preview", e);
            ImageMethods.recycleBitmap(outlineBitmap);
            return null;
        }
    }

    static Bitmap applyAppearanceEffects(Bitmap bitmap,
                                         float cornerRadiusRatio,
                                         int cornerRadiusMask,
                                         float edgeFeatherRatio,
                                         int edgeFeatherMask) {
        if (bitmap == null || bitmap.isRecycled()) {
            return bitmap;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return bitmap;
        }
        float shortEdge = Math.min(width, height);
        float cornerRadiusPx = clampAppearanceRatio(cornerRadiusRatio) * shortEdge;
        float edgeFeatherPx = clampAppearanceRatio(edgeFeatherRatio) * shortEdge;
        float maxShapeRadius = shortEdge / 2f;
        cornerRadiusPx = Math.min(cornerRadiusPx, maxShapeRadius);
        edgeFeatherPx = Math.min(edgeFeatherPx, maxShapeRadius);
        float[] cornerRadii = buildCornerRadii(cornerRadiusPx, cornerRadiusMask);
        if (!hasAnyCornerRadius(cornerRadii) && (edgeFeatherPx <= 0f || edgeFeatherMask == 0)) {
            return bitmap;
        }
        if (edgeFeatherPx > 0f && edgeFeatherMask != 0) {
            return applyFeatheredShapeMask(bitmap, cornerRadii, edgeFeatherPx, edgeFeatherMask);
        }
        return applyRoundCorners(bitmap, cornerRadii);
    }

    static float normalizeDegree(float degree) {
        float normalizedDegree = degree % 360f;
        if (normalizedDegree < 0f) {
            normalizedDegree += 360f;
        }
        if (Math.abs(normalizedDegree) < 0.01f || Math.abs(normalizedDegree - 360f) < 0.01f) {
            return 0f;
        }
        return normalizedDegree;
    }

    private static Bitmap applyRoundCorners(Bitmap bitmap, float[] cornerRadii) {
        if (!hasAnyCornerRadius(cornerRadii)) {
            return bitmap;
        }
        Bitmap roundedBitmap = createBitmapSafely(bitmap.getWidth(), bitmap.getHeight(), "applyRoundCorners");
        if (roundedBitmap == null) {
            return null;
        }
        try {
            Canvas canvas = new Canvas(roundedBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            RectF rect = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
            canvas.drawPath(buildRoundRectPath(rect, cornerRadii), paint);
            return roundedBitmap;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to draw rounded bitmap", e);
            ImageMethods.recycleBitmap(roundedBitmap);
            return null;
        }
    }

    private static Bitmap applyFeatheredShapeMask(Bitmap bitmap,
                                                  float[] cornerRadii,
                                                  float edgeFeatherPx,
                                                  int edgeFeatherMask) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long pixelCount = (long) width * height;
        if (pixelCount <= 0L || pixelCount > Integer.MAX_VALUE) {
            Log.w(TAG, "applyFeatheredShapeMask rejected bitmap size: " + width + "x" + height);
            return null;
        }
        int[] pixels;
        try {
            pixels = new int[(int) pixelCount];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "applyFeatheredShapeMask ran out of memory: " + width + "x" + height, e);
            return null;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to read bitmap pixels for feathering", e);
            return null;
        }

        int index = 0;
        for (int y = 0; y < height; y++) {
            float sampleY = y + 0.5f;
            for (int x = 0; x < width; x++) {
                int color = pixels[index];
                int sourceAlpha = color >>> 24;
                if (sourceAlpha == 0) {
                    index++;
                    continue;
                }
                float sampleX = x + 0.5f;
                if (!isInsideSelectiveRoundRect(sampleX, sampleY, width, height, cornerRadii)) {
                    pixels[index] = 0;
                    index++;
                    continue;
                }
                float featherAlpha = resolveDirectionalFeatherAlpha(
                        sampleX,
                        sampleY,
                        width,
                        height,
                        cornerRadii,
                        edgeFeatherPx,
                        edgeFeatherMask
                );
                int maskedAlpha = Math.round(sourceAlpha * featherAlpha);
                pixels[index] = (maskedAlpha << 24) | (color & 0x00FFFFFF);
                index++;
            }
        }

        Bitmap featheredBitmap = createBitmapSafely(width, height, "applyFeatheredShapeMask");
        if (featheredBitmap == null) {
            return null;
        }
        try {
            featheredBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return featheredBitmap;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to write feathered bitmap pixels", e);
            ImageMethods.recycleBitmap(featheredBitmap);
            return null;
        }
    }

    private static float[] buildCornerRadii(float cornerRadiusPx, int cornerRadiusMask) {
        return new float[]{
                (cornerRadiusMask & Config.MASK_CORNER_TOP_LEFT) != 0 ? cornerRadiusPx : 0f,
                (cornerRadiusMask & Config.MASK_CORNER_TOP_RIGHT) != 0 ? cornerRadiusPx : 0f,
                (cornerRadiusMask & Config.MASK_CORNER_BOTTOM_RIGHT) != 0 ? cornerRadiusPx : 0f,
                (cornerRadiusMask & Config.MASK_CORNER_BOTTOM_LEFT) != 0 ? cornerRadiusPx : 0f
        };
    }

    private static boolean hasAnyCornerRadius(float[] cornerRadii) {
        return cornerRadii[0] > 0f || cornerRadii[1] > 0f || cornerRadii[2] > 0f || cornerRadii[3] > 0f;
    }

    private static Path buildRoundRectPath(RectF rect, float[] cornerRadii) {
        Path path = new Path();
        if (!hasAnyCornerRadius(cornerRadii)) {
            path.addRect(rect, Path.Direction.CW);
        } else {
            path.addRoundRect(rect, buildAndroidCornerRadii(cornerRadii), Path.Direction.CW);
        }
        path.close();
        return path;
    }

    private static float[] buildAndroidCornerRadii(float[] cornerRadii) {
        return new float[]{
                cornerRadii[0], cornerRadii[0],
                cornerRadii[1], cornerRadii[1],
                cornerRadii[2], cornerRadii[2],
                cornerRadii[3], cornerRadii[3]
        };
    }

    private static boolean isInsideSelectiveRoundRect(float x,
                                                      float y,
                                                      int width,
                                                      int height,
                                                      float[] cornerRadii) {
        float topLeftRadius = cornerRadii[0];
        if (topLeftRadius > 0f && x < topLeftRadius && y < topLeftRadius) {
            return resolveCornerInsideDistance(x, y, topLeftRadius, topLeftRadius, topLeftRadius) >= 0f;
        }
        float topRightRadius = cornerRadii[1];
        if (topRightRadius > 0f && x > (width - topRightRadius) && y < topRightRadius) {
            return resolveCornerInsideDistance(x, y, width - topRightRadius, topRightRadius, topRightRadius) >= 0f;
        }
        float bottomRightRadius = cornerRadii[2];
        if (bottomRightRadius > 0f && x > (width - bottomRightRadius) && y > (height - bottomRightRadius)) {
            return resolveCornerInsideDistance(x, y, width - bottomRightRadius, height - bottomRightRadius, bottomRightRadius) >= 0f;
        }
        float bottomLeftRadius = cornerRadii[3];
        if (bottomLeftRadius > 0f && x < bottomLeftRadius && y > (height - bottomLeftRadius)) {
            return resolveCornerInsideDistance(x, y, bottomLeftRadius, height - bottomLeftRadius, bottomLeftRadius) >= 0f;
        }
        return true;
    }

    private static float resolveDirectionalFeatherAlpha(float x,
                                                        float y,
                                                        int width,
                                                        int height,
                                                        float[] cornerRadii,
                                                        float edgeFeatherPx,
                                                        int edgeFeatherMask) {
        if (edgeFeatherPx <= 0f || edgeFeatherMask == 0) {
            return 1f;
        }
        float featherAlpha = 1f;
        if ((edgeFeatherMask & Config.MASK_EDGE_TOP) != 0) {
            featherAlpha = Math.min(featherAlpha, smoothStep(y / edgeFeatherPx));
        }
        if ((edgeFeatherMask & Config.MASK_EDGE_BOTTOM) != 0) {
            featherAlpha = Math.min(featherAlpha, smoothStep((height - y) / edgeFeatherPx));
        }
        if ((edgeFeatherMask & Config.MASK_EDGE_LEFT) != 0) {
            featherAlpha = Math.min(featherAlpha, smoothStep(x / edgeFeatherPx));
        }
        if ((edgeFeatherMask & Config.MASK_EDGE_RIGHT) != 0) {
            featherAlpha = Math.min(featherAlpha, smoothStep((width - x) / edgeFeatherPx));
        }
        featherAlpha = applyCornerFeather(featherAlpha, x, y, width, height, cornerRadii, edgeFeatherPx, edgeFeatherMask);
        return featherAlpha;
    }

    private static float applyCornerFeather(float currentAlpha,
                                            float x,
                                            float y,
                                            int width,
                                            int height,
                                            float[] cornerRadii,
                                            float edgeFeatherPx,
                                            int edgeFeatherMask) {
        if (cornerRadii[0] > 0f
                && (edgeFeatherMask & Config.MASK_EDGE_TOP) != 0
                && (edgeFeatherMask & Config.MASK_EDGE_LEFT) != 0
                && x < cornerRadii[0]
                && y < cornerRadii[0]) {
            currentAlpha = Math.min(currentAlpha, smoothStep(resolveCornerInsideDistance(x, y, cornerRadii[0], cornerRadii[0], cornerRadii[0]) / edgeFeatherPx));
        }
        if (cornerRadii[1] > 0f
                && (edgeFeatherMask & Config.MASK_EDGE_TOP) != 0
                && (edgeFeatherMask & Config.MASK_EDGE_RIGHT) != 0
                && x > (width - cornerRadii[1])
                && y < cornerRadii[1]) {
            currentAlpha = Math.min(currentAlpha, smoothStep(resolveCornerInsideDistance(x, y, width - cornerRadii[1], cornerRadii[1], cornerRadii[1]) / edgeFeatherPx));
        }
        if (cornerRadii[2] > 0f
                && (edgeFeatherMask & Config.MASK_EDGE_BOTTOM) != 0
                && (edgeFeatherMask & Config.MASK_EDGE_RIGHT) != 0
                && x > (width - cornerRadii[2])
                && y > (height - cornerRadii[2])) {
            currentAlpha = Math.min(currentAlpha, smoothStep(resolveCornerInsideDistance(x, y, width - cornerRadii[2], height - cornerRadii[2], cornerRadii[2]) / edgeFeatherPx));
        }
        if (cornerRadii[3] > 0f
                && (edgeFeatherMask & Config.MASK_EDGE_BOTTOM) != 0
                && (edgeFeatherMask & Config.MASK_EDGE_LEFT) != 0
                && x < cornerRadii[3]
                && y > (height - cornerRadii[3])) {
            currentAlpha = Math.min(currentAlpha, smoothStep(resolveCornerInsideDistance(x, y, cornerRadii[3], height - cornerRadii[3], cornerRadii[3]) / edgeFeatherPx));
        }
        return currentAlpha;
    }

    private static float resolveCornerInsideDistance(float x, float y, float centerX, float centerY, float radius) {
        return radius - (float) Math.hypot(x - centerX, y - centerY);
    }

    private static int getDisplayTargetSize(int sourceSize, float zoom) {
        if (sourceSize <= 0 || Float.isNaN(zoom)) {
            return 1;
        }
        float safeZoom = Math.max(zoom, 0.01f);
        if (Float.isInfinite(safeZoom) || safeZoom > Integer.MAX_VALUE / (float) sourceSize) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.round(sourceSize * safeZoom), 1);
    }

    private static int resolveBitmapDimension(float dimension, int padding) {
        if (Float.isNaN(dimension) || dimension <= 0f) {
            return 1;
        }
        double paddedDimension = Math.ceil(dimension) + ((double) padding * 2d);
        if (Double.isInfinite(paddedDimension) || paddedDimension > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max((int) paddedDimension, 1);
    }

    private static Bitmap createBitmapSafely(int width, int height, String operationName) {
        try {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, operationName + " ran out of memory: " + width + "x" + height, e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, operationName + " rejected bitmap size: " + width + "x" + height, e);
            return null;
        }
    }

    private static float smoothStep(float value) {
        float clampedValue = clampToUnit(value);
        return clampedValue * clampedValue * (3f - (2f * clampedValue));
    }

    private static float clampAppearanceRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private static float clampValue(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private static float clampToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
