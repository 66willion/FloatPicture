package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.FloatImageView;

public class ImageMethods {
    private static final String TAG = "ImageMethods";
    private static final int MANAGE_PREVIEW_WIDTH_DP = 72;
    private static final int MANAGE_PREVIEW_HEIGHT_DP = 96;
    private static final int MANAGE_PREVIEW_CACHE_SIZE = 24;
    private static final AtomicLong DISPLAY_CACHE_REBUILD_SEQUENCE = new AtomicLong();
    private static final ConcurrentHashMap<String, Long> DISPLAY_CACHE_REBUILD_TOKENS = new ConcurrentHashMap<>();
    private static final ExecutorService DISPLAY_CACHE_REBUILD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, "floatpicture-display-cache");
        thread.setDaemon(false);
        return thread;
    });
    private static final Object MANAGE_PREVIEW_CACHE_LOCK = new Object();
    private static final Set<Bitmap> MANAGE_PREVIEW_BITMAP_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<ImageView, Bitmap> MANAGE_PREVIEW_BOUND_BITMAPS = new WeakHashMap<>();
    private static final LruCache<String, Bitmap> MANAGE_PREVIEW_CACHE = new LruCache<String, Bitmap>(MANAGE_PREVIEW_CACHE_SIZE) {
        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
                if (oldValue != null && oldValue != newValue) {
                    MANAGE_PREVIEW_BITMAP_SET.remove(oldValue);
                    recycleManagePreviewBitmapIfUnusedLocked(oldValue);
                }
            }
        }
    };

    private static Bitmap getBitmapFromFile(File imageFile) {
        return ImageBitmapProcessor.getBitmapFromFile(imageFile);
    }

    public static String setNewImage(Context mContext, Uri uri) {
        return PictureFileStore.setNewImage(mContext, uri);
    }

    public static boolean stageReplacementImage(Context context, String id, Uri uri) {
        return PictureFileStore.stageReplacementImage(context, id, uri);
    }

    public static Bitmap getStagedReplacementBitmap(String id) {
        File stagedFile = PictureFileStore.getStagedPendingOriginalFile(id);
        if (!stagedFile.exists()) {
            return null;
        }
        return ImageBitmapProcessor.decodeSourceBitmap(stagedFile, 0, 0, false);
    }

    public static boolean applyStagedReplacementImage(String id) {
        return PictureFileStore.applyStagedReplacementImage(id);
    }

    public static boolean hasPendingReplacementImage(String id) {
        return PictureFileStore.hasPendingReplacementImage(id);
    }

    public static Bitmap getPendingEditSourceBitmap(String id) {
        File pendingFile = PictureFileStore.getPendingOriginalFile(id);
        if (!pendingFile.exists()) {
            return null;
        }
        return ImageBitmapProcessor.decodeSourceBitmap(pendingFile, 0, 0, false);
    }

    public static boolean commitPendingReplacementImage(String id) {
        boolean hadPendingReplacement = PictureFileStore.hasPendingReplacementImage(id);
        boolean committed = PictureFileStore.commitPendingReplacementImage(id);
        if (committed && hadPendingReplacement) {
            ImageBitmapProcessor.invalidateSourceBitmapSize(id);
            invalidateManagePreviewCache(id);
        }
        return committed;
    }

    public static void clearPendingReplacementImage(String id) {
        PictureFileStore.clearPendingReplacementImage(id);
    }

    public static void clearStagedReplacementImage(String id) {
        PictureFileStore.clearStagedReplacementImage(id);
    }

    public static void saveFloatImageViewById(Context mContext, String id, FloatImageView FloatImageView) {
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        FloatImageView.setPictureId(id);
        mainApplication.registerView(id, FloatImageView);
    }

    public static FloatImageView getFloatImageViewById(Context mContext, String id) {
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        return (FloatImageView) mainApplication.getRegisteredView(id);
    }

    public static FloatImageView createPictureView(Context mContext, Bitmap bitmap, boolean touchable, boolean overLayout, float pictureAlpha, float zoom, float degree) {
        return createPictureView(mContext, resizeBitmap(bitmap, zoom, degree), touchable, overLayout, pictureAlpha);
    }

    public static FloatImageView createPictureView(Context mContext,
                                                   Bitmap bitmap,
                                                   boolean touchable,
                                                   boolean overLayout,
                                                   float pictureAlpha,
                                                   float zoom,
                                                   float degree,
                                                   float cornerRadiusRatio,
                                                   float edgeFeatherRatio) {
        return createPictureView(
                mContext,
                bitmap,
                touchable,
                overLayout,
                pictureAlpha,
                zoom,
                degree,
                cornerRadiusRatio,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                edgeFeatherRatio,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static FloatImageView createPictureView(Context mContext,
                                                   Bitmap bitmap,
                                                   boolean touchable,
                                                   boolean overLayout,
                                                   float pictureAlpha,
                                                   float zoom,
                                                   float degree,
                                                   float cornerRadiusRatio,
                                                   int cornerRadiusMask,
                                                   float edgeFeatherRatio,
                                                   int edgeFeatherMask) {
        return createPictureView(
                mContext,
                resizeBitmap(bitmap, zoom, degree, cornerRadiusRatio, cornerRadiusMask, edgeFeatherRatio, edgeFeatherMask),
                touchable,
                overLayout,
                pictureAlpha
        );
    }

    public static FloatImageView createPictureView(Context mContext, Bitmap bitmap, boolean touchable, boolean overLayout, float pictureAlpha) {
        Context viewContext = mContext.getApplicationContext() != null ? mContext.getApplicationContext() : mContext;
        FloatImageView imageView = new FloatImageView(viewContext);
        imageView.setMoveable(touchable);
        imageView.setOverLayout(overLayout);
        imageView.setPictureAlpha(pictureAlpha);
        setPictureBitmap(imageView, bitmap);
        imageView.setBackgroundColor(ContextCompat.getColor(viewContext, android.R.color.transparent));
        imageView.getBackground().setAlpha(0);
        return imageView;
    }

    public static Bitmap getEditBitmap(Context mContext, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return getEditBitmap(mContext, 50, 50);
        }
        return getEditBitmap(mContext, bitmap.getWidth(), bitmap.getHeight());
    }

    private static Bitmap getEditBitmap(Context mContext, int width, int height) {
        int bitmapWidth = Math.max(width, 1);
        int bitmapHeight = Math.max(height, 1);
        Bitmap transparentBitmap;
        try {
            transparentBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Edit placeholder ran out of memory: " + bitmapWidth + "x" + bitmapHeight, e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Edit placeholder rejected bitmap size: " + bitmapWidth + "x" + bitmapHeight, e);
            return null;
        }
        try {
            Canvas canvas = new Canvas(transparentBitmap);
            canvas.drawColor(ContextCompat.getColor(mContext, R.color.colorImageViewEditBackground));
            return transparentBitmap;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to draw edit placeholder", e);
            recycleBitmap(transparentBitmap);
            return null;
        }
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, float zoom, float degree) {
        return resizeBitmap(
                bitmap,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap resizeBitmap(Bitmap bitmap,
                                      float zoom,
                                      float degree,
                                      float cornerRadiusRatio,
                                      float edgeFeatherRatio) {
        return resizeBitmap(
                bitmap,
                zoom,
                degree,
                cornerRadiusRatio,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                edgeFeatherRatio,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap resizeBitmap(Bitmap bitmap,
                                      float zoom,
                                      float degree,
                                      float cornerRadiusRatio,
                                      int cornerRadiusMask,
                                      float edgeFeatherRatio,
                                      int edgeFeatherMask) {
        return ImageBitmapProcessor.resizeBitmap(
                bitmap,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
    }

    public static Bitmap createOutlinePreviewBitmap(Bitmap bitmap,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    int cornerRadiusMask) {
        return ImageAppearanceRenderer.createOutlinePreviewBitmap(bitmap, zoom, degree, cornerRadiusRatio, cornerRadiusMask);
    }

    public static Bitmap createPreviewSourceBitmap(Bitmap bitmap) {
        return ImageBitmapProcessor.createPreviewSourceBitmap(bitmap);
    }

    public static Bitmap resizeBitmapFromScaledSource(Bitmap scaledSourceBitmap,
                                                      int originalWidth,
                                                      int originalHeight,
                                                      float zoom,
                                                      float degree,
                                                      float cornerRadiusRatio,
                                                      int cornerRadiusMask,
                                                      float edgeFeatherRatio,
                                                      int edgeFeatherMask) {
        if (scaledSourceBitmap == null || scaledSourceBitmap.isRecycled()) {
            return null;
        }
        float widthScale = originalWidth > 0 ? originalWidth / (float) scaledSourceBitmap.getWidth() : 1f;
        float heightScale = originalHeight > 0 ? originalHeight / (float) scaledSourceBitmap.getHeight() : 1f;
        float sourceScale = Math.max(1f, Math.max(widthScale, heightScale));
        return ImageBitmapProcessor.resizeBitmap(
                scaledSourceBitmap,
                zoom * sourceScale,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
    }

    public static float getDefaultZoom(Context mContext, Bitmap bitmap, boolean isMax) {
        return getDefaultZoom(mContext, bitmap.getWidth(), bitmap.getHeight(), isMax);
    }

    public static float getDefaultZoom(Context mContext, String id, boolean isMax) {
        Point pictureSize = ImageBitmapProcessor.getSourceBitmapSize(id);
        if (pictureSize != null) {
            return getDefaultZoom(mContext, pictureSize.x, pictureSize.y, isMax);
        }
        Log.w(TAG, "Unable to resolve default zoom because source image size is unavailable: " + id);
        return 0f;
    }

    public static Point getSourceBitmapSize(String id) {
        return ImageBitmapProcessor.getSourceBitmapSize(id);
    }

    @Nullable
    public static Bitmap getDisplayBitmap(Context mContext, String id, float zoom, float degree) {
        return getDisplayBitmap(
                mContext,
                id,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    @Nullable
    public static Bitmap getDisplayBitmap(Context mContext,
                                          String id,
                                          float zoom,
                                          float degree,
                                          float cornerRadiusRatio,
                                          float edgeFeatherRatio) {
        return getDisplayBitmap(
                mContext,
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                edgeFeatherRatio,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    @Nullable
    public static Bitmap getDisplayBitmap(Context mContext,
                                          String id,
                                          float zoom,
                                          float degree,
                                          float cornerRadiusRatio,
                                          int cornerRadiusMask,
                                          float edgeFeatherRatio,
                                          int edgeFeatherMask) {
        return getDisplayBitmapOrNull(
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
    }

    @Nullable
    public static Bitmap getDisplayBitmapOrNull(String id,
                                                float zoom,
                                                float degree,
                                                float cornerRadiusRatio,
                                                int cornerRadiusMask,
                                                float edgeFeatherRatio,
                                                int edgeFeatherMask) {
        Bitmap displayBitmap = getBitmapFromFile(PictureFileStore.getDisplayFile(id));
        if (displayBitmap != null) {
            return displayBitmap;
        }
        return createAndSaveDisplayBitmap(
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
    }

    static long getDisplayBitmapVersion(String id) {
        File displayFile = PictureFileStore.getDisplayFile(id);
        if (!displayFile.exists()) {
            return 0L;
        }
        return PictureFileStore.buildFileVersion(displayFile);
    }

    public static Bitmap createAndSaveDisplayBitmap(String id, Bitmap sourceBitmap, float zoom, float degree) {
        return createAndSaveDisplayBitmap(
                id,
                sourceBitmap,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    Bitmap sourceBitmap,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    float edgeFeatherRatio) {
        return createAndSaveDisplayBitmap(
                id,
                sourceBitmap,
                zoom,
                degree,
                cornerRadiusRatio,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                edgeFeatherRatio,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    Bitmap sourceBitmap,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    int cornerRadiusMask,
                                                    float edgeFeatherRatio,
                                                    int edgeFeatherMask) {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) {
            return null;
        }
        Bitmap renderedBitmap = ImageBitmapProcessor.renderDisplayBitmap(
                sourceBitmap,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (renderedBitmap == null) {
            return null;
        }
        if (!saveDisplayBitmap(id, renderedBitmap, false)) {
            if (renderedBitmap != sourceBitmap) {
                recycleBitmap(renderedBitmap);
            }
            return null;
        }
        return renderedBitmap;
    }

    public static boolean stageDisplayBitmap(String id,
                                             float zoom,
                                             float degree,
                                             float cornerRadiusRatio,
                                             int cornerRadiusMask,
                                             float edgeFeatherRatio,
                                             int edgeFeatherMask) {
        Bitmap renderedBitmap = ImageBitmapProcessor.buildDisplayBitmap(
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (renderedBitmap == null) {
            return false;
        }
        try {
            return PictureFileStore.savePendingDisplayBitmap(id, renderedBitmap, false);
        } finally {
            recycleBitmap(renderedBitmap);
        }
    }

    public static boolean commitStagedDisplayBitmap(String id) {
        boolean committed = PictureFileStore.commitPendingDisplayBitmap(id);
        if (committed) {
            invalidateManagePreviewCache(id);
        }
        return committed;
    }

    public static void clearStagedDisplayBitmap(String id) {
        PictureFileStore.clearPendingDisplayBitmap(id);
    }

    public static boolean clearDisplayBitmapCache(String id) {
        if (id == null || id.isEmpty()) {
            return true;
        }
        DISPLAY_CACHE_REBUILD_TOKENS.remove(id);
        boolean cleared = PictureFileStore.clearDisplayBitmap(id);
        if (cleared) {
            invalidateManagePreviewCache(id);
        }
        return cleared;
    }

    public static void rebuildDisplayBitmapAsync(Context context,
                                                 String id,
                                                 float zoom,
                                                 float degree,
                                                 float cornerRadiusRatio,
                                                 int cornerRadiusMask,
                                                 float edgeFeatherRatio,
                                                 int edgeFeatherMask) {
        if (context == null || id == null || id.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        long rebuildToken = DISPLAY_CACHE_REBUILD_SEQUENCE.incrementAndGet();
        DISPLAY_CACHE_REBUILD_TOKENS.put(id, rebuildToken);
        DISPLAY_CACHE_REBUILD_EXECUTOR.execute(() -> rebuildDisplayBitmap(
                appContext,
                id,
                rebuildToken,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        ));
    }

    public static Bitmap createAndSaveDisplayBitmap(String id, float zoom, float degree) {
        return createAndSaveDisplayBitmap(
                id,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    float edgeFeatherRatio) {
        return createAndSaveDisplayBitmap(
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK,
                edgeFeatherRatio,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    int cornerRadiusMask,
                                                    float edgeFeatherRatio,
                                                    int edgeFeatherMask) {
        Bitmap renderedBitmap = ImageBitmapProcessor.buildDisplayBitmap(
                id,
                zoom,
                degree,
                cornerRadiusRatio,
                cornerRadiusMask,
                edgeFeatherRatio,
                edgeFeatherMask
        );
        if (renderedBitmap == null) {
            return null;
        }
        if (!saveDisplayBitmap(id, renderedBitmap, false)) {
            recycleBitmap(renderedBitmap);
            return null;
        }
        return renderedBitmap;
    }

    private static void rebuildDisplayBitmap(Context appContext,
                                             String id,
                                             long rebuildToken,
                                             float zoom,
                                             float degree,
                                             float cornerRadiusRatio,
                                             int cornerRadiusMask,
                                             float edgeFeatherRatio,
                                             int edgeFeatherMask) {
        boolean committed = false;
        try {
            if (!isDisplayCacheRebuildCurrent(id, rebuildToken)) {
                return;
            }
            boolean staged = stageDisplayBitmap(
                    id,
                    zoom,
                    degree,
                    cornerRadiusRatio,
                    cornerRadiusMask,
                    edgeFeatherRatio,
                    edgeFeatherMask
            );
            if (!staged || !isDisplayCacheRebuildCurrent(id, rebuildToken)) {
                return;
            }
            committed = commitStagedDisplayBitmap(id);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Display cache rebuild ran out of memory: " + id, e);
        } catch (RuntimeException e) {
            Log.e(TAG, "Display cache rebuild failed: " + id, e);
        } finally {
            if (!committed) {
                clearStagedDisplayBitmap(id);
            }
            DISPLAY_CACHE_REBUILD_TOKENS.remove(id, rebuildToken);
        }
        if (committed) {
            OverlayRuntimeController.syncPicture(appContext, id, false);
        }
    }

    private static boolean isDisplayCacheRebuildCurrent(String id, long rebuildToken) {
        Long currentToken = DISPLAY_CACHE_REBUILD_TOKENS.get(id);
        return currentToken != null && currentToken == rebuildToken;
    }

    public static void recycleBitmap(Bitmap bitmap) {
        recycleBitmap(bitmap, null);
    }

    private static float getDefaultZoom(Context mContext, int imageWidth, int imageHeight, boolean isMax) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return 1;
        }
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        float screen_width;
        float screen_height;
        if (isMax) {
            screen_width = displayMetrics.widthPixels;
            screen_height = displayMetrics.heightPixels;
        } else {
            screen_width = displayMetrics.widthPixels / 3.0f;
            screen_height = displayMetrics.heightPixels / 3.0f;
        }
        if (imageHeight <= imageWidth) {
            if (imageHeight > screen_height || isMax) {
                return ((float) Math.round((screen_height / imageHeight) * 100f)) / 100f;
            }
        } else {
            if (imageWidth > screen_width || isMax) {
                return ((float) Math.round((screen_width / imageWidth) * 100f)) / 100f;
            }
        }
        return 1;
    }

    @Nullable
    public static Bitmap getPreviewBitmap(Context mContext, String id) {
        Bitmap cachedPreview = getCachedPreviewBitmap(id);
        if (cachedPreview != null) {
            return cachedPreview;
        }
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int previewWidth = Math.max(Math.round(displayMetrics.density * MANAGE_PREVIEW_WIDTH_DP), 1);
        int previewHeight = Math.max(Math.round(displayMetrics.density * MANAGE_PREVIEW_HEIGHT_DP), 1);
        Bitmap preview = ImageBitmapProcessor.decodeSampledBitmap(PictureFileStore.getDisplayFile(id), previewWidth, previewHeight, true);
        if (preview != null) {
            cachePreviewBitmap(id, preview);
            return preview;
        }
        File sourceFile = PictureFileStore.getAvailableSourceFile(id);
        if (sourceFile != null) {
            preview = ImageBitmapProcessor.decodeSourceBitmap(sourceFile, previewWidth, previewHeight, true);
            if (preview != null) {
                cachePreviewBitmap(id, preview);
                return preview;
            }
        }
        return null;
    }

    public static Bitmap getCachedPreviewBitmap(String id) {
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            Bitmap previewBitmap = MANAGE_PREVIEW_CACHE.get(id);
            if (previewBitmap == null) {
                return null;
            }
            if (previewBitmap.isRecycled()) {
                MANAGE_PREVIEW_CACHE.remove(id);
                MANAGE_PREVIEW_BITMAP_SET.remove(previewBitmap);
                return null;
            }
            return previewBitmap;
        }
    }

    public static void setManagePreviewBitmap(ImageView imageView, Bitmap previewBitmap) {
        if (imageView == null) {
            return;
        }
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            Bitmap previousBitmap = getBitmapFromDrawable(imageView.getDrawable());
            if (previewBitmap != null && previewBitmap.isRecycled()) {
                previewBitmap = null;
            }
            Bitmap previousBoundBitmap = MANAGE_PREVIEW_BOUND_BITMAPS.get(imageView);
            if (previousBoundBitmap != null && previousBoundBitmap != previewBitmap) {
                MANAGE_PREVIEW_BOUND_BITMAPS.remove(imageView);
                recycleManagePreviewBitmapIfUnusedLocked(previousBoundBitmap);
            }
            if (previewBitmap != null && MANAGE_PREVIEW_BITMAP_SET.contains(previewBitmap)) {
                MANAGE_PREVIEW_BOUND_BITMAPS.put(imageView, previewBitmap);
            } else {
                MANAGE_PREVIEW_BOUND_BITMAPS.remove(imageView);
            }
            imageView.setImageBitmap(previewBitmap);
            if (previousBitmap != null && previousBitmap != previewBitmap) {
                recycleManagePreviewBitmapIfUnusedLocked(previousBitmap);
            }
        }
    }

    public static long getManagePreviewVersion(String id) {
        File displayFile = PictureFileStore.getDisplayFile(id);
        if (displayFile.exists()) {
            return PictureFileStore.buildFileVersion(displayFile);
        }
        File sourceFile = PictureFileStore.getAvailableSourceFile(id);
        if (sourceFile != null && sourceFile.exists()) {
            return PictureFileStore.buildFileVersion(sourceFile);
        }
        return 0L;
    }

    public static void invalidateManagePreviewCache(String id) {
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            MANAGE_PREVIEW_CACHE.remove(id);
        }
    }

    public static void clearManagePreviewCache() {
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            Bitmap[] previewBitmaps = MANAGE_PREVIEW_BITMAP_SET.toArray(new Bitmap[0]);
            MANAGE_PREVIEW_CACHE.evictAll();
            MANAGE_PREVIEW_BITMAP_SET.clear();
            for (Bitmap previewBitmap : previewBitmaps) {
                recycleManagePreviewBitmapIfUnusedLocked(previewBitmap);
            }
        }
    }

    public static Bitmap getEditSourceBitmap(Context mContext, String id) {
        Bitmap bitmap = getEditSourceBitmapOrNull(id);
        if (bitmap != null) {
            return bitmap;
        }
        return getEditBitmap(mContext, 50, 50);
    }

    @Nullable
    public static Bitmap getEditSourceBitmapOrNull(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        File originalFile = PictureFileStore.getOriginalFile(id);
        if (originalFile.exists()) {
            Bitmap bitmap = ImageBitmapProcessor.decodeSourceBitmap(originalFile, 0, 0, false);
            if (bitmap != null) {
                return bitmap;
            }
        }
        File legacyFile = PictureFileStore.getLegacyFile(id);
        if (legacyFile.exists()) {
            Bitmap bitmap = getBitmapFromFile(legacyFile);
            if (bitmap != null) {
                return bitmap;
            }
        }
        Bitmap displayBitmap = getBitmapFromFile(PictureFileStore.getDisplayFile(id));
        if (displayBitmap != null) {
            return displayBitmap;
        }
        return null;
    }

    public static Bitmap getShowBitmap(Context mContext, String id) {
        return getEditSourceBitmap(mContext, id);
    }

    public static boolean isPictureFileExist(String id) {
        return PictureFileStore.isPictureFileExist(id);
    }

    public static boolean hasAvailablePictureContent(String id) {
        return PictureFileStore.hasAvailablePictureContent(id);
    }

    public static void setPictureBitmap(FloatImageView imageView, Bitmap bitmap) {
        Bitmap previousBitmap = getBitmapFromDrawable(imageView.getDrawable());
        imageView.setImageBitmap(bitmap);
        recycleBitmap(previousBitmap, bitmap);
    }

    static boolean hasActivePictureBitmap(ImageView imageView) {
        Bitmap bitmap = getBitmapFromDrawable(imageView.getDrawable());
        return bitmap != null && !bitmap.isRecycled();
    }

    public static void releaseImageBitmap(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        Bitmap bitmap = getBitmapFromDrawable(imageView.getDrawable());
        imageView.setImageDrawable(null);
        if (releaseManagePreviewBitmap(imageView, bitmap)) {
            return;
        }
        recycleBitmap(bitmap, null);
    }

    public static void releasePictureView(FloatImageView imageView) {
        if (imageView == null) {
            return;
        }
        releaseImageBitmap(imageView);
        imageView.setBackground(null);
    }

    public static void clearAllTemp(Context mContext, String id) {
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        if (mainApplication.getRegisteredView(id) instanceof FloatImageView floatImageView) {
            releasePictureView(floatImageView);
        }
        mainApplication.unregisterView(id);
        invalidateManagePreviewCache(id);
        ImageBitmapProcessor.invalidateSourceBitmapSize(id);
        PictureFileStore.deleteAllPictureFiles(id);
    }

    private static boolean saveDisplayBitmap(String id, Bitmap bitmap, boolean recycle) {
        boolean saved = PictureFileStore.saveDisplayBitmap(id, bitmap, recycle);
        invalidateManagePreviewCache(id);
        return saved;
    }

    private static void cachePreviewBitmap(String id, Bitmap previewBitmap) {
        if (previewBitmap == null || previewBitmap.isRecycled()) {
            return;
        }
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            MANAGE_PREVIEW_CACHE.put(id, previewBitmap);
            MANAGE_PREVIEW_BITMAP_SET.add(previewBitmap);
        }
    }

    private static Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            return bitmapDrawable.getBitmap();
        }
        return null;
    }

    private static boolean releaseManagePreviewBitmap(ImageView imageView, Bitmap currentBitmap) {
        synchronized (MANAGE_PREVIEW_CACHE_LOCK) {
            Bitmap boundBitmap = MANAGE_PREVIEW_BOUND_BITMAPS.remove(imageView);
            if (boundBitmap != null) {
                recycleManagePreviewBitmapIfUnusedLocked(boundBitmap);
                return boundBitmap == currentBitmap || currentBitmap == null;
            }
            if (currentBitmap != null && MANAGE_PREVIEW_BITMAP_SET.contains(currentBitmap)) {
                return true;
            }
        }
        return false;
    }

    private static void recycleManagePreviewBitmapIfUnusedLocked(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (MANAGE_PREVIEW_BITMAP_SET.contains(bitmap) || isManagePreviewBitmapBoundLocked(bitmap)) {
            return;
        }
        bitmap.recycle();
    }

    private static boolean isManagePreviewBitmapBoundLocked(Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        for (Bitmap boundBitmap : MANAGE_PREVIEW_BOUND_BITMAPS.values()) {
            if (boundBitmap == bitmap) {
                return true;
            }
        }
        return false;
    }

    private static void recycleBitmap(Bitmap bitmap, Bitmap keepBitmap) {
        if (bitmap != null && bitmap != keepBitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
