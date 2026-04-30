package tool.xfy9326.floatpicture.Methods;

import static android.graphics.Bitmap.createBitmap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;
import android.util.LruCache;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;

import tool.xfy9326.floatpicture.Utils.Config;

final class ImageBitmapProcessor {
    private static final String TAG = "ImageBitmapProcessor";
    private static final int DISPLAY_DECODE_MULTIPLIER = 2;
    private static final int TEMP_PREVIEW_SOURCE_MAX_SIDE = 512;
    private static final int SOURCE_SIZE_CACHE_SIZE = 256;
    private static final float MIN_ZOOM = 0.01f;
    private static final Object BITMAP_LOCK = new Object();
    private static final Object SOURCE_SIZE_CACHE_LOCK = new Object();
    private static final LruCache<String, SourceBitmapSizeCacheEntry> SOURCE_SIZE_CACHE =
            new LruCache<>(SOURCE_SIZE_CACHE_SIZE);

    private ImageBitmapProcessor() {
    }

    static Bitmap getBitmapFromFile(File imageFile) {
        return getBitmapFromFile(imageFile, null);
    }

    static Bitmap getBitmapFromFile(File imageFile, BitmapFactory.Options options) {
        if (imageFile.exists() && imageFile.isFile() && imageFile.canRead()) {
            try {
                return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "decodeFile ran out of memory: " + imageFile.getAbsolutePath(), e);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "decodeFile rejected image: " + imageFile.getAbsolutePath(), e);
            }
        }
        return null;
    }

    static Bitmap resizeBitmap(Bitmap bitmap,
                               float zoom,
                               float degree,
                               float cornerRadiusRatio,
                               int cornerRadiusMask,
                               float edgeFeatherRatio,
                               int edgeFeatherMask) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();
        float safeZoom = Math.max(zoom, MIN_ZOOM);
        if (safeZoom != 1.0f) {
            matrix.postScale(safeZoom, safeZoom);
        }
        if (degree != -1) {
            matrix.postRotate(degree);
        }
        synchronized (BITMAP_LOCK) {
            Bitmap transformedBitmap = createTransformedBitmapSafely(
                    bitmap,
                    0,
                    0,
                    width,
                    height,
                    matrix,
                    "resizeBitmap"
            );
            if (transformedBitmap == null) {
                return null;
            }
            Bitmap appearanceBitmap = applyAppearanceEffects(
                    transformedBitmap,
                    cornerRadiusRatio,
                    cornerRadiusMask,
                    edgeFeatherRatio,
                    edgeFeatherMask
            );
            if (appearanceBitmap == null) {
                if (transformedBitmap != bitmap) {
                    ImageMethods.recycleBitmap(transformedBitmap);
                }
                return null;
            }
            if (appearanceBitmap != transformedBitmap && transformedBitmap != bitmap) {
                ImageMethods.recycleBitmap(transformedBitmap);
            }
            return appearanceBitmap;
        }
    }

    static Bitmap createPreviewSourceBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxSide <= TEMP_PREVIEW_SOURCE_MAX_SIDE) {
            return bitmap;
        }
        float scale = TEMP_PREVIEW_SOURCE_MAX_SIDE / (float) maxSide;
        int previewWidth = Math.max(Math.round(bitmap.getWidth() * scale), 1);
        int previewHeight = Math.max(Math.round(bitmap.getHeight() * scale), 1);
        return createScaledBitmapHighQuality(bitmap, previewWidth, previewHeight);
    }

    static Bitmap decodeSampledBitmap(File imageFile, int reqWidth, int reqHeight, boolean decodeAtLeastTarget) {
        if (reqWidth <= 0 || reqHeight <= 0) {
            return getBitmapFromFile(imageFile);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        getBitmapFromFile(imageFile, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight, decodeAtLeastTarget);
        return getBitmapFromFile(imageFile, options);
    }

    static Bitmap decodeSourceBitmap(File sourceFile, int reqWidth, int reqHeight, boolean decodeAtLeastTarget) {
        Bitmap bitmap = decodeSampledBitmap(sourceFile, reqWidth, reqHeight, decodeAtLeastTarget);
        if (bitmap == null) {
            return null;
        }
        if (PictureFileStore.isOriginalStorageFile(sourceFile)) {
            return applyExifOrientation(bitmap, sourceFile);
        }
        return bitmap;
    }

    static Point getSourceBitmapSize(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        File sourceFile = PictureFileStore.getAvailableSourceFile(id);
        if (sourceFile == null) {
            return null;
        }
        String sourcePath = sourceFile.getAbsolutePath();
        long sourceVersion = PictureFileStore.buildFileVersion(sourceFile);
        synchronized (SOURCE_SIZE_CACHE_LOCK) {
            SourceBitmapSizeCacheEntry cachedEntry = SOURCE_SIZE_CACHE.get(id);
            if (cachedEntry != null && cachedEntry.matches(sourcePath, sourceVersion)) {
                return cachedEntry.toPoint();
            }
        }
        Point size = getBitmapSize(sourceFile);
        if (size == null) {
            return null;
        }
        Point orientedSize;
        if (isRotateSizeSwapped(getExifRotationDegrees(sourceFile))) {
            orientedSize = new Point(size.y, size.x);
        } else {
            orientedSize = size;
        }
        synchronized (SOURCE_SIZE_CACHE_LOCK) {
            SOURCE_SIZE_CACHE.put(id, new SourceBitmapSizeCacheEntry(sourcePath, sourceVersion, orientedSize));
        }
        return new Point(orientedSize);
    }

    static void invalidateSourceBitmapSize(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        synchronized (SOURCE_SIZE_CACHE_LOCK) {
            SOURCE_SIZE_CACHE.remove(id);
        }
    }

    static Bitmap buildDisplayBitmap(String id,
                                     float zoom,
                                     float degree,
                                     float cornerRadiusRatio,
                                     int cornerRadiusMask,
                                     float edgeFeatherRatio,
                                     int edgeFeatherMask) {
        File sourceFile = PictureFileStore.getAvailableSourceFile(id);
        Point sourceSize = getSourceBitmapSize(id);
        if (sourceFile == null || sourceSize == null) {
            return null;
        }
        int targetWidth = getDisplayTargetSize(sourceSize.x, zoom);
        int targetHeight = getDisplayTargetSize(sourceSize.y, zoom);
        int decodeWidth = getDecodeTargetSize(targetWidth);
        int decodeHeight = getDecodeTargetSize(targetHeight);
        Bitmap sourceBitmap = decodeSourceBitmap(sourceFile, decodeWidth, decodeHeight, true);
        if (sourceBitmap == null) {
            return null;
        }
        Bitmap renderedBitmap = renderDisplayBitmap(
                sourceBitmap,
                targetWidth,
                targetHeight,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (renderedBitmap != sourceBitmap) {
            ImageMethods.recycleBitmap(sourceBitmap);
        }
        return renderedBitmap;
    }

    static Bitmap renderDisplayBitmap(Bitmap sourceBitmap,
                                      float zoom,
                                      float degree,
                                      float cornerRadiusRatio,
                                      int cornerRadiusMask,
                                      float edgeFeatherRatio,
                                      int edgeFeatherMask) {
        int targetWidth = getDisplayTargetSize(sourceBitmap.getWidth(), zoom);
        int targetHeight = getDisplayTargetSize(sourceBitmap.getHeight(), zoom);
        return renderDisplayBitmap(
                sourceBitmap,
                targetWidth,
                targetHeight,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth,
                                             int reqHeight,
                                             boolean decodeAtLeastTarget) {
        int width = options.outWidth;
        int height = options.outHeight;
        if (reqWidth <= 0 || reqHeight <= 0 || width <= 0 || height <= 0) {
            return 1;
        }
        if (decodeAtLeastTarget) {
            int widthRatio = Math.max(width / reqWidth, 1);
            int heightRatio = Math.max(height / reqHeight, 1);
            int minRatio = Math.min(widthRatio, heightRatio);
            if (minRatio <= 1) {
                return 1;
            }
            return Math.max(Integer.highestOneBit(minRatio), 1);
        }
        int inSampleSize = 1;
        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return Math.max(inSampleSize, 1);
    }

    private static Point getBitmapSize(File imageFile) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        getBitmapFromFile(imageFile, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        return new Point(options.outWidth, options.outHeight);
    }

    private static Bitmap renderDisplayBitmap(Bitmap sourceBitmap,
                                              int targetWidth,
                                              int targetHeight,
                                              float degree,
                                              float cornerRadiusRatio,
                                              int cornerRadiusMask,
                                              float edgeFeatherRatio,
                                              int edgeFeatherMask) {
        Bitmap scaledBitmap = scaleBitmapMultiPass(sourceBitmap, targetWidth, targetHeight);
        if (scaledBitmap == null) {
            return null;
        }
        Bitmap rotatedBitmap = applyUserRotation(scaledBitmap, degree);
        if (rotatedBitmap == null) {
            if (scaledBitmap != sourceBitmap) {
                ImageMethods.recycleBitmap(scaledBitmap);
            }
            return null;
        }
        if (rotatedBitmap != scaledBitmap) {
            ImageMethods.recycleBitmap(scaledBitmap);
        }
        Bitmap appearanceBitmap = applyAppearanceEffects(
                rotatedBitmap,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (appearanceBitmap == null) {
            if (rotatedBitmap != sourceBitmap) {
                ImageMethods.recycleBitmap(rotatedBitmap);
            }
            return null;
        }
        if (appearanceBitmap != rotatedBitmap && rotatedBitmap != sourceBitmap) {
            ImageMethods.recycleBitmap(rotatedBitmap);
        }
        return appearanceBitmap;
    }

    private static Bitmap scaleBitmapMultiPass(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            return bitmap;
        }
        Bitmap currentBitmap = bitmap;
        while ((currentBitmap.getWidth() / 2) >= targetWidth && (currentBitmap.getHeight() / 2) >= targetHeight) {
            int nextWidth = Math.max(targetWidth, currentBitmap.getWidth() / 2);
            int nextHeight = Math.max(targetHeight, currentBitmap.getHeight() / 2);
            Bitmap nextBitmap = createScaledBitmapHighQuality(currentBitmap, nextWidth, nextHeight);
            if (nextBitmap == null) {
                if (currentBitmap != bitmap) {
                    ImageMethods.recycleBitmap(currentBitmap);
                }
                return null;
            }
            if (currentBitmap != bitmap) {
                ImageMethods.recycleBitmap(currentBitmap);
            }
            currentBitmap = nextBitmap;
        }
        if (currentBitmap.getWidth() != targetWidth || currentBitmap.getHeight() != targetHeight) {
            Bitmap exactBitmap = createScaledBitmapHighQuality(currentBitmap, targetWidth, targetHeight);
            if (exactBitmap == null) {
                if (currentBitmap != bitmap) {
                    ImageMethods.recycleBitmap(currentBitmap);
                }
                return null;
            }
            if (currentBitmap != bitmap) {
                ImageMethods.recycleBitmap(currentBitmap);
            }
            currentBitmap = exactBitmap;
        }
        return currentBitmap;
    }

    private static Bitmap createScaledBitmapHighQuality(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null || bitmap.isRecycled() || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        Bitmap scaledBitmap = createBitmapSafely(targetWidth, targetHeight, "createScaledBitmapHighQuality");
        if (scaledBitmap == null) {
            return null;
        }
        try {
            Canvas canvas = new Canvas(scaledBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            canvas.drawBitmap(bitmap, null, new android.graphics.Rect(0, 0, targetWidth, targetHeight), paint);
            return scaledBitmap;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to draw scaled bitmap", e);
            ImageMethods.recycleBitmap(scaledBitmap);
            return null;
        }
    }

    private static Bitmap applyUserRotation(Bitmap bitmap, float degree) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        float normalizedDegree = ImageAppearanceRenderer.normalizeDegree(degree);
        if (normalizedDegree == 0f) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(normalizedDegree);
        return createTransformedBitmapSafely(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                "applyUserRotation"
        );
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, File imageFile) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int rotationDegrees = getExifRotationDegrees(imageFile);
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap rotatedBitmap = createTransformedBitmapSafely(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                "applyExifOrientation"
        );
        if (rotatedBitmap == null) {
            ImageMethods.recycleBitmap(bitmap);
            return null;
        }
        if (rotatedBitmap != bitmap) {
            ImageMethods.recycleBitmap(bitmap);
        }
        return rotatedBitmap;
    }

    private static int getExifRotationDegrees(File imageFile) {
        if (!PictureFileStore.isOriginalStorageFile(imageFile) || !imageFile.exists()) {
            return 0;
        }
        try {
            ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90 -> 90;
                case ExifInterface.ORIENTATION_ROTATE_180 -> 180;
                case ExifInterface.ORIENTATION_ROTATE_270 -> 270;
                default -> 0;
            };
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static boolean isRotateSizeSwapped(int rotationDegrees) {
        return rotationDegrees == 90 || rotationDegrees == 270;
    }

    private static int getDisplayTargetSize(int sourceSize, float zoom) {
        if (sourceSize <= 0 || Float.isNaN(zoom)) {
            return 1;
        }
        float safeZoom = Math.max(zoom, MIN_ZOOM);
        if (Float.isInfinite(safeZoom) || safeZoom > Integer.MAX_VALUE / (float) sourceSize) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.round(sourceSize * safeZoom), 1);
    }

    private static int getDecodeTargetSize(int targetSize) {
        if (targetSize <= 0) {
            return 1;
        }
        long decodeTargetSize = (long) targetSize * DISPLAY_DECODE_MULTIPLIER;
        return decodeTargetSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) decodeTargetSize;
    }

    private static Bitmap applyAppearanceEffects(Bitmap bitmap,
                                                 float cornerRadiusRatio,
                                                 int cornerRadiusMask,
                                                 float edgeFeatherRatio,
                                                 int edgeFeatherMask) {
        return ImageAppearanceRenderer.applyAppearanceEffects(
                bitmap,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
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

    private static Bitmap createTransformedBitmapSafely(Bitmap bitmap,
                                                        int x,
                                                        int y,
                                                        int width,
                                                        int height,
                                                        Matrix matrix,
                                                        String operationName) {
        try {
            return createBitmap(bitmap, x, y, width, height, matrix, true);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, operationName + " ran out of memory", e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, operationName + " rejected transform", e);
            return null;
        }
    }

    private static final class SourceBitmapSizeCacheEntry {
        private final String sourcePath;
        private final long sourceVersion;
        private final int width;
        private final int height;

        private SourceBitmapSizeCacheEntry(String sourcePath, long sourceVersion, Point sourceSize) {
            this.sourcePath = sourcePath;
            this.sourceVersion = sourceVersion;
            this.width = sourceSize.x;
            this.height = sourceSize.y;
        }

        private boolean matches(String sourcePath, long sourceVersion) {
            return this.sourceVersion == sourceVersion && this.sourcePath.equals(sourcePath);
        }

        private Point toPoint() {
            return new Point(width, height);
        }
    }
}
