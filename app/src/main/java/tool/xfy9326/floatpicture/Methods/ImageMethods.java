package tool.xfy9326.floatpicture.Methods;

import static android.graphics.Bitmap.createBitmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.FloatImageView;

public class ImageMethods {
    private static final int DEFAULT_PREVIEW_SIZE_DP = 120;
    private static final int DISPLAY_DECODE_MULTIPLIER = 2;
    private static final float MIN_ZOOM = 0.01f;
    private static final Object BITMAP_LOCK = new Object();

    private static Bitmap getBitmapFromFile(File imageFile) {
        return getBitmapFromFile(imageFile, null);
    }

    private static Bitmap getBitmapFromFile(File imageFile, BitmapFactory.Options options) {
        if (imageFile.exists() && imageFile.isFile() && imageFile.canRead()) {
            return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        }
        return null;
    }

    private static File getOriginalPictureFile(String id) {
        return new File(Config.getOriginalPictureDir() + id);
    }

    private static File getPendingOriginalPictureFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending");
    }

    private static File getStagedPendingOriginalPictureFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending.new");
    }

    private static File getLegacyPictureFile(String id) {
        return new File(Config.getPictureDir() + id);
    }

    private static File getDisplayPictureFile(String id) {
        return new File(Config.getPictureTempDir() + id);
    }

    private static File getAvailableSourceFile(String id) {
        File originalFile = getOriginalPictureFile(id);
        if (originalFile.exists()) {
            return originalFile;
        }
        File legacyFile = getLegacyPictureFile(id);
        if (legacyFile.exists()) {
            return legacyFile;
        }
        return null;
    }

    private static boolean isOriginalPictureFile(File imageFile) {
        return imageFile != null && imageFile.getAbsolutePath().startsWith(Config.getOriginalPictureDir());
    }

    private static String getNewPictureId(Context mContext, Uri uri) {
        return System.currentTimeMillis() + "-" + CodeMethods.getFileMD5String(mContext, uri);
    }

    public static String setNewImage(Context mContext, Uri uri) {
        try {
            String id = getNewPictureId(mContext, uri);
            if (IOMethods.copyUriToFile(mContext, uri, Config.getOriginalPictureDir() + id)) {
                return id;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean stageReplacementImage(Context context, String id, Uri uri) {
        try {
            return IOMethods.copyUriToFile(context, uri, getStagedPendingOriginalPictureFile(id).getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Bitmap getStagedReplacementBitmap(String id) {
        File stagedFile = getStagedPendingOriginalPictureFile(id);
        if (!stagedFile.exists()) {
            return null;
        }
        return decodeSourceBitmap(stagedFile, 0, 0, false);
    }

    public static boolean applyStagedReplacementImage(String id) {
        File stagedFile = getStagedPendingOriginalPictureFile(id);
        if (!stagedFile.exists()) {
            return false;
        }
        File pendingFile = getPendingOriginalPictureFile(id);
        File pendingBackupFile = new File(Config.getOriginalPictureDir() + id + ".pending.backup");
        deleteFileIfExists(pendingBackupFile);
        boolean pendingBackedUp = false;
        if (pendingFile.exists()) {
            if (pendingFile.renameTo(pendingBackupFile)) {
                pendingBackedUp = true;
            } else {
                if (!IOMethods.copyFile(pendingFile, pendingBackupFile)) {
                    return false;
                }
                pendingBackedUp = true;
                deleteFileIfExists(pendingFile);
                if (pendingFile.exists()) {
                    deleteFileIfExists(pendingBackupFile);
                    return false;
                }
            }
        }
        boolean replaced = stagedFile.renameTo(pendingFile) || IOMethods.copyFile(stagedFile, pendingFile);
        if (!replaced || !pendingFile.exists()) {
            deleteFileIfExists(pendingFile);
            if (pendingBackedUp) {
                if (!pendingBackupFile.renameTo(pendingFile)) {
                    IOMethods.copyFile(pendingBackupFile, pendingFile);
                }
            }
            return false;
        }
        deleteFileIfExists(stagedFile);
        deleteFileIfExists(pendingBackupFile);
        return pendingFile.exists();
    }

    public static boolean hasPendingReplacementImage(String id) {
        return getPendingOriginalPictureFile(id).exists();
    }

    public static Bitmap getPendingEditSourceBitmap(String id) {
        File pendingFile = getPendingOriginalPictureFile(id);
        if (!pendingFile.exists()) {
            return null;
        }
        return decodeSourceBitmap(pendingFile, 0, 0, false);
    }

    public static boolean commitPendingReplacementImage(String id) {
        File pendingFile = getPendingOriginalPictureFile(id);
        if (!pendingFile.exists()) {
            return true;
        }
        File originalFile = getOriginalPictureFile(id);
        File backupFile = new File(Config.getOriginalPictureDir() + id + ".backup");
        deleteFileIfExists(backupFile);
        boolean originalBackedUp = false;
        if (originalFile.exists()) {
            if (originalFile.renameTo(backupFile)) {
                originalBackedUp = true;
            } else {
                if (!IOMethods.copyFile(originalFile, backupFile)) {
                    return false;
                }
                originalBackedUp = true;
                deleteFileIfExists(originalFile);
                if (originalFile.exists()) {
                    deleteFileIfExists(backupFile);
                    return false;
                }
            }
        }
        boolean replaced = pendingFile.renameTo(originalFile) || IOMethods.copyFile(pendingFile, originalFile);
        if (!replaced || !originalFile.exists()) {
            deleteFileIfExists(originalFile);
            if (originalBackedUp) {
                if (!backupFile.renameTo(originalFile)) {
                    IOMethods.copyFile(backupFile, originalFile);
                }
            }
            return false;
        }
        deleteFileIfExists(getLegacyPictureFile(id));
        deleteFileIfExists(getDisplayPictureFile(id));
        deleteFileIfExists(pendingFile);
        deleteFileIfExists(backupFile);
        return originalFile.exists() && !pendingFile.exists();
    }

    public static void clearPendingReplacementImage(String id) {
        deleteFileIfExists(getPendingOriginalPictureFile(id));
    }

    public static void clearStagedReplacementImage(String id) {
        deleteFileIfExists(getStagedPendingOriginalPictureFile(id));
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
                resizeBitmap(bitmap, zoom, degree, cornerRadiusRatio, edgeFeatherRatio),
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
        return getEditBitmap(mContext, bitmap.getWidth(), bitmap.getHeight());
    }

    private static Bitmap getEditBitmap(Context mContext, int width, int height) {
        Bitmap transparent_bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(transparent_bitmap);
        canvas.drawColor(ContextCompat.getColor(mContext, R.color.colorImageViewEditBackground));
        return transparent_bitmap;
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, float zoom, float degree) {
        return resizeBitmap(bitmap, zoom, degree, Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO, Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap,
                                      float zoom,
                                      float degree,
                                      float cornerRadiusRatio,
                                      float edgeFeatherRatio) {
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
            Bitmap transformedBitmap = createBitmap(bitmap, 0, 0, width, height, matrix, true);
            Bitmap appearanceBitmap = applyAppearanceEffects(transformedBitmap, cornerRadiusRatio, edgeFeatherRatio);
            if (appearanceBitmap != transformedBitmap && transformedBitmap != bitmap) {
                recycleBitmap(transformedBitmap);
            }
            return appearanceBitmap;
        }
    }

    public static float getDefaultZoom(Context mContext, Bitmap bitmap, boolean isMax) {
        return getDefaultZoom(mContext, bitmap.getWidth(), bitmap.getHeight(), isMax);
    }

    public static float getDefaultZoom(Context mContext, String id, boolean isMax) {
        Point pictureSize = getSourceBitmapSize(id);
        if (pictureSize != null) {
            return getDefaultZoom(mContext, pictureSize.x, pictureSize.y, isMax);
        }
        Bitmap bitmap = getEditSourceBitmap(mContext, id);
        float defaultZoom = getDefaultZoom(mContext, bitmap, isMax);
        recycleBitmap(bitmap);
        return defaultZoom;
    }

    public static Bitmap getDisplayBitmap(Context mContext, String id, float zoom, float degree) {
        return getDisplayBitmap(
                mContext,
                id,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    public static Bitmap getDisplayBitmap(Context mContext,
                                          String id,
                                          float zoom,
                                          float degree,
                                          float cornerRadiusRatio,
                                          float edgeFeatherRatio) {
        Bitmap displayBitmap = getBitmapFromFile(getDisplayPictureFile(id));
        if (displayBitmap != null) {
            return displayBitmap;
        }
        Bitmap renderedBitmap = createAndSaveDisplayBitmap(id, zoom, degree, cornerRadiusRatio, edgeFeatherRatio);
        return renderedBitmap != null ? renderedBitmap : getEditBitmap(mContext, 50, 50);
    }

    public static Bitmap createAndSaveDisplayBitmap(String id, Bitmap sourceBitmap, float zoom, float degree) {
        return createAndSaveDisplayBitmap(
                id,
                sourceBitmap,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    Bitmap sourceBitmap,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    float edgeFeatherRatio) {
        Bitmap renderedBitmap = renderDisplayBitmap(sourceBitmap, zoom, degree, cornerRadiusRatio, edgeFeatherRatio);
        saveDisplayBitmap(id, renderedBitmap, false);
        return renderedBitmap;
    }

    public static Bitmap createAndSaveDisplayBitmap(String id, float zoom, float degree) {
        return createAndSaveDisplayBitmap(
                id,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    public static Bitmap createAndSaveDisplayBitmap(String id,
                                                    float zoom,
                                                    float degree,
                                                    float cornerRadiusRatio,
                                                    float edgeFeatherRatio) {
        Bitmap renderedBitmap = buildDisplayBitmap(id, zoom, degree, cornerRadiusRatio, edgeFeatherRatio);
        if (renderedBitmap != null) {
            saveDisplayBitmap(id, renderedBitmap, false);
        }
        return renderedBitmap;
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

    public static Bitmap getPreviewBitmap(Context mContext, String id) {
        int previewSize = Math.max(Math.round(mContext.getResources().getDisplayMetrics().density * DEFAULT_PREVIEW_SIZE_DP), 1);
        Bitmap preview = decodeSampledBitmap(getDisplayPictureFile(id), previewSize, previewSize, true);
        if (preview != null) {
            return preview;
        }
        File sourceFile = getAvailableSourceFile(id);
        if (sourceFile != null) {
            preview = decodeSourceBitmap(sourceFile, previewSize, previewSize, true);
            if (preview != null) {
                return preview;
            }
        }
        return getEditBitmap(mContext, 50, 50);
    }

    public static Bitmap getEditSourceBitmap(Context mContext, String id) {
        File originalFile = getOriginalPictureFile(id);
        if (originalFile.exists()) {
            Bitmap bitmap = decodeSourceBitmap(originalFile, 0, 0, false);
            if (bitmap != null) {
                return bitmap;
            }
        }
        File legacyFile = getLegacyPictureFile(id);
        if (legacyFile.exists()) {
            Bitmap bitmap = getBitmapFromFile(legacyFile);
            if (bitmap != null) {
                return bitmap;
            }
        }
        Bitmap displayBitmap = getBitmapFromFile(getDisplayPictureFile(id));
        if (displayBitmap != null) {
            return displayBitmap;
        }
        return getEditBitmap(mContext, 50, 50);
    }

    public static Bitmap getShowBitmap(Context mContext, String id) {
        return getEditSourceBitmap(mContext, id);
    }

    public static boolean isPictureFileExist(String id) {
        return getOriginalPictureFile(id).exists() || getLegacyPictureFile(id).exists();
    }

    public static void setPictureBitmap(FloatImageView imageView, Bitmap bitmap) {
        Bitmap previousBitmap = getBitmapFromDrawable(imageView.getDrawable());
        imageView.setImageBitmap(bitmap);
        recycleBitmap(previousBitmap, bitmap);
    }

    public static void releaseImageBitmap(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        Bitmap bitmap = getBitmapFromDrawable(imageView.getDrawable());
        imageView.setImageDrawable(null);
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
        deleteFileIfExists(getStagedPendingOriginalPictureFile(id));
        deleteFileIfExists(getPendingOriginalPictureFile(id));
        deleteFileIfExists(getOriginalPictureFile(id));
        deleteFileIfExists(getLegacyPictureFile(id));
        deleteFileIfExists(getDisplayPictureFile(id));
    }

    private static Bitmap decodeSampledBitmap(File imageFile, int reqWidth, int reqHeight, boolean decodeAtLeastTarget) {
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

    private static Bitmap decodeSourceBitmap(File sourceFile, int reqWidth, int reqHeight, boolean decodeAtLeastTarget) {
        Bitmap bitmap = decodeSampledBitmap(sourceFile, reqWidth, reqHeight, decodeAtLeastTarget);
        if (bitmap == null) {
            return null;
        }
        if (isOriginalPictureFile(sourceFile)) {
            return applyExifOrientation(bitmap, sourceFile);
        }
        return bitmap;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight, boolean decodeAtLeastTarget) {
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

    private static Point getSourceBitmapSize(String id) {
        File sourceFile = getAvailableSourceFile(id);
        if (sourceFile == null) {
            return null;
        }
        Point size = getBitmapSize(sourceFile);
        if (size == null) {
            return null;
        }
        if (isRotateSizeSwapped(getExifRotationDegrees(sourceFile))) {
            return new Point(size.y, size.x);
        }
        return size;
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

    private static Bitmap buildDisplayBitmap(String id, float zoom, float degree) {
        return buildDisplayBitmap(
                id,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    private static Bitmap buildDisplayBitmap(String id,
                                             float zoom,
                                             float degree,
                                             float cornerRadiusRatio,
                                             float edgeFeatherRatio) {
        File sourceFile = getAvailableSourceFile(id);
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
        Bitmap renderedBitmap = renderDisplayBitmap(sourceBitmap, targetWidth, targetHeight, degree, cornerRadiusRatio, edgeFeatherRatio);
        if (renderedBitmap != sourceBitmap) {
            recycleBitmap(sourceBitmap);
        }
        return renderedBitmap;
    }

    private static Bitmap renderDisplayBitmap(Bitmap sourceBitmap, float zoom, float degree) {
        return renderDisplayBitmap(
                sourceBitmap,
                zoom,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    private static Bitmap renderDisplayBitmap(Bitmap sourceBitmap,
                                              float zoom,
                                              float degree,
                                              float cornerRadiusRatio,
                                              float edgeFeatherRatio) {
        int targetWidth = getDisplayTargetSize(sourceBitmap.getWidth(), zoom);
        int targetHeight = getDisplayTargetSize(sourceBitmap.getHeight(), zoom);
        return renderDisplayBitmap(sourceBitmap, targetWidth, targetHeight, degree, cornerRadiusRatio, edgeFeatherRatio);
    }

    private static Bitmap renderDisplayBitmap(Bitmap sourceBitmap, int targetWidth, int targetHeight, float degree) {
        return renderDisplayBitmap(
                sourceBitmap,
                targetWidth,
                targetHeight,
                degree,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
    }

    private static Bitmap renderDisplayBitmap(Bitmap sourceBitmap,
                                              int targetWidth,
                                              int targetHeight,
                                              float degree,
                                              float cornerRadiusRatio,
                                              float edgeFeatherRatio) {
        Bitmap scaledBitmap = scaleBitmapMultiPass(sourceBitmap, targetWidth, targetHeight);
        Bitmap rotatedBitmap = applyUserRotation(scaledBitmap, degree);
        if (rotatedBitmap != scaledBitmap) {
            recycleBitmap(scaledBitmap);
        }
        Bitmap appearanceBitmap = applyAppearanceEffects(rotatedBitmap, cornerRadiusRatio, edgeFeatherRatio);
        if (appearanceBitmap != rotatedBitmap && rotatedBitmap != sourceBitmap) {
            recycleBitmap(rotatedBitmap);
        }
        return appearanceBitmap;
    }

    private static Bitmap scaleBitmapMultiPass(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return bitmap;
        }
        Bitmap currentBitmap = bitmap;
        while ((currentBitmap.getWidth() / 2) >= targetWidth && (currentBitmap.getHeight() / 2) >= targetHeight) {
            int nextWidth = Math.max(targetWidth, currentBitmap.getWidth() / 2);
            int nextHeight = Math.max(targetHeight, currentBitmap.getHeight() / 2);
            Bitmap nextBitmap = createScaledBitmapHighQuality(currentBitmap, nextWidth, nextHeight);
            if (currentBitmap != bitmap) {
                recycleBitmap(currentBitmap);
            }
            currentBitmap = nextBitmap;
        }
        if (currentBitmap.getWidth() != targetWidth || currentBitmap.getHeight() != targetHeight) {
            Bitmap exactBitmap = createScaledBitmapHighQuality(currentBitmap, targetWidth, targetHeight);
            if (currentBitmap != bitmap) {
                recycleBitmap(currentBitmap);
            }
            currentBitmap = exactBitmap;
        }
        return currentBitmap;
    }

    private static Bitmap createScaledBitmapHighQuality(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        Bitmap scaledBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, null, new android.graphics.Rect(0, 0, targetWidth, targetHeight), paint);
        return scaledBitmap;
    }

    private static Bitmap applyUserRotation(Bitmap bitmap, float degree) {
        float normalizedDegree = normalizeDegree(degree);
        if (normalizedDegree == 0f) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(normalizedDegree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, File imageFile) {
        int rotationDegrees = getExifRotationDegrees(imageFile);
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotatedBitmap != bitmap) {
            recycleBitmap(bitmap);
        }
        return rotatedBitmap;
    }

    private static int getExifRotationDegrees(File imageFile) {
        if (!isOriginalPictureFile(imageFile) || !imageFile.exists()) {
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
        float safeZoom = Math.max(zoom, MIN_ZOOM);
        return Math.max(Math.round(sourceSize * safeZoom), 1);
    }

    private static int getDecodeTargetSize(int targetSize) {
        return Math.max(targetSize * DISPLAY_DECODE_MULTIPLIER, 1);
    }

    private static Bitmap applyAppearanceEffects(Bitmap bitmap, float cornerRadiusRatio, float edgeFeatherRatio) {
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
        if (cornerRadiusPx <= 0f && edgeFeatherPx <= 0f) {
            return bitmap;
        }
        return edgeFeatherPx > 0f
                ? applyFeatheredShapeMask(bitmap, cornerRadiusPx, edgeFeatherPx)
                : applyRoundCorners(bitmap, cornerRadiusPx);
    }

    private static Bitmap applyRoundCorners(Bitmap bitmap, float cornerRadiusPx) {
        if (cornerRadiusPx <= 0f) {
            return bitmap;
        }
        Bitmap roundedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(roundedBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF rect = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);
        return roundedBitmap;
    }

    private static Bitmap applyFeatheredShapeMask(Bitmap bitmap, float cornerRadiusPx, float edgeFeatherPx) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] sourcePixels = new int[width * height];
        int[] maskedPixels = new int[sourcePixels.length];
        bitmap.getPixels(sourcePixels, 0, width, 0, 0, width, height);

        float halfWidth = width / 2f;
        float halfHeight = height / 2f;
        float bodyHalfWidth = Math.max(halfWidth - cornerRadiusPx, 0f);
        float bodyHalfHeight = Math.max(halfHeight - cornerRadiusPx, 0f);

        int index = 0;
        for (int y = 0; y < height; y++) {
            float py = (y + 0.5f) - halfHeight;
            for (int x = 0; x < width; x++) {
                int color = sourcePixels[index];
                int sourceAlpha = color >>> 24;
                if (sourceAlpha == 0) {
                    index++;
                    continue;
                }
                float px = (x + 0.5f) - halfWidth;
                float signedDistance = signedDistanceToRoundRect(px, py, bodyHalfWidth, bodyHalfHeight, cornerRadiusPx);
                if (signedDistance >= 0f) {
                    maskedPixels[index] = 0;
                    index++;
                    continue;
                }
                float insideDistance = -signedDistance;
                float featherAlpha = insideDistance >= edgeFeatherPx
                        ? 1f
                        : smoothStep(insideDistance / edgeFeatherPx);
                int maskedAlpha = Math.round(sourceAlpha * featherAlpha);
                maskedPixels[index] = (maskedAlpha << 24) | (color & 0x00FFFFFF);
                index++;
            }
        }

        Bitmap featheredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        featheredBitmap.setPixels(maskedPixels, 0, width, 0, 0, width, height);
        return featheredBitmap;
    }

    private static float signedDistanceToRoundRect(float px,
                                                   float py,
                                                   float bodyHalfWidth,
                                                   float bodyHalfHeight,
                                                   float cornerRadiusPx) {
        float qx = Math.abs(px) - bodyHalfWidth;
        float qy = Math.abs(py) - bodyHalfHeight;
        float outsideX = Math.max(qx, 0f);
        float outsideY = Math.max(qy, 0f);
        float outsideDistance = (float) Math.hypot(outsideX, outsideY);
        float insideDistance = Math.min(Math.max(qx, qy), 0f);
        return outsideDistance + insideDistance - cornerRadiusPx;
    }

    private static float smoothStep(float value) {
        float clampedValue = clampToUnit(value);
        return clampedValue * clampedValue * (3f - (2f * clampedValue));
    }

    private static float clampAppearanceRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private static float clampToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float normalizeDegree(float degree) {
        float normalizedDegree = degree % 360f;
        if (normalizedDegree < 0f) {
            normalizedDegree += 360f;
        }
        if (Math.abs(normalizedDegree) < 0.01f || Math.abs(normalizedDegree - 360f) < 0.01f) {
            return 0f;
        }
        return normalizedDegree;
    }

    private static void saveDisplayBitmap(String id, Bitmap bitmap, boolean recycle) {
        IOMethods.saveBitmapLossless(bitmap, Config.getPictureTempDir() + id, recycle);
    }

    private static void deleteFileIfExists(File file) {
        if (file.exists()) {
            if (!file.delete()) {
                Log.w("ImageMethods", "Failed to delete: " + file.getAbsolutePath());
            }
        }
    }

    private static Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            return bitmapDrawable.getBitmap();
        }
        return null;
    }

    private static void recycleBitmap(Bitmap bitmap, Bitmap keepBitmap) {
        if (bitmap != null && bitmap != keepBitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
