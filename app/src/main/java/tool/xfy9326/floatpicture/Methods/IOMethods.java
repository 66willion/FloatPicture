package tool.xfy9326.floatpicture.Methods;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Objects;

import tool.xfy9326.floatpicture.Utils.Config;

public class IOMethods {
    @SuppressWarnings("deprecation")
    private static Bitmap.CompressFormat getLossyCompressFormat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Bitmap.CompressFormat.WEBP_LOSSY;
        }
        return Bitmap.CompressFormat.WEBP;
    }

    private static Bitmap.CompressFormat getLosslessCompressFormat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Bitmap.CompressFormat.WEBP_LOSSLESS;
        }
        return Bitmap.CompressFormat.PNG;
    }

    static Bitmap readImageByUri(Context context, Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            android.content.res.AssetFileDescriptor afd = contentResolver.openAssetFileDescriptor(uri, "r");
            if (afd == null) {
                return null;
            }
            try (afd; FileInputStream inputStream = afd.createInputStream()) {
                return BitmapFactory.decodeStream(inputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    static void saveBitmap(Bitmap bitmap, int quality, String path) {
        saveBitmap(bitmap, quality, path, true);
    }

    @SuppressWarnings("SameParameterValue")
    static void saveBitmap(Bitmap bitmap, int quality, String path, boolean recycle) {
        File file = new File(path);
        try {
            if (!CheckFile(file, true)) {
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    bitmap.compress(getLossyCompressFormat(), quality, outputStream);
                }
                if (recycle) {
                    bitmap.recycle();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean copyUriToFile(Context context, Uri uri, String path) {
        ContentResolver contentResolver = context.getContentResolver();
        File file = new File(path);
        boolean targetPrepared = false;
        boolean copied = false;
        try {
            if (CheckFile(file, true)) {
                return false;
            }
            targetPrepared = true;
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(file)) {
                if (inputStream == null) {
                    return false;
                }
                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readBytes);
                }
                outputStream.flush();
            }
            copied = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (targetPrepared && !copied) {
                deleteIncompleteCopy(file);
            }
        }
        return false;
    }

    static boolean copyFile(File source, File target) {
        if (source == null || target == null || !source.exists() || !source.isFile()) {
            return false;
        }
        boolean targetPrepared = false;
        boolean copied = false;
        try {
            if (CheckFile(target, true)) {
                return false;
            }
            targetPrepared = true;
            try (InputStream inputStream = new FileInputStream(source);
                 OutputStream outputStream = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readBytes);
                }
                outputStream.flush();
            }
            copied = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (targetPrepared && !copied) {
                deleteIncompleteCopy(target);
            }
        }
        return false;
    }

    @SuppressWarnings("SameParameterValue")
    static boolean saveBitmapLossless(Bitmap bitmap, String path, boolean recycle) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        File file = new File(path);
        boolean targetPrepared = false;
        boolean saved = false;
        try {
            if (CheckFile(file, true)) {
                return false;
            }
            targetPrepared = true;
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                if (!bitmap.compress(getLosslessCompressFormat(), 100, outputStream)) {
                    return false;
                }
                outputStream.flush();
                outputStream.getFD().sync();
            }
            if (!isValidBitmapFile(file)) {
                return false;
            }
            saved = true;
            if (recycle) {
                bitmap.recycle();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (targetPrepared && !saved) {
                deleteIncompleteCopy(file);
            }
        }
        return false;
    }

    private static boolean isValidBitmapFile(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() <= 0L) {
            return false;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        return options.outWidth > 0 && options.outHeight > 0;
    }

    public static String readAssetText(Context mContext, String path) {
        try (BufferedReader bufReader = new BufferedReader(
                new InputStreamReader(mContext.getResources().getAssets().open(path)))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufReader.readLine()) != null) {
                result.append(line).append("\n");
            }
            return result.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setNoMedia() {
        File nomedia = new File(Config.getNoMediaFilePath());
        if (!nomedia.exists()) {
            try {
                return nomedia.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return true;
        }
    }

    private static boolean createPath(File file) {
        if (Objects.requireNonNull(file.getParent()).trim().length() != 1) {
            File filepath = file.getParentFile();
            if (!Objects.requireNonNull(filepath).exists()) {
                return filepath.mkdirs();
            }
        }
        return true;
    }

    private static void deleteIncompleteCopy(File file) {
        if (file.exists() && file.isFile()) {
            file.delete();
        }
    }

    private static boolean CheckFile(File file, boolean delete) throws IOException {
        if (file.exists()) {
            if (file.isFile()) {
                if (delete) {
                    if (file.delete()) {
                        return !file.createNewFile();
                    }
                } else {
                    return false;
                }
            }
        } else {
            if (!createPath(file)) {
                return true;
            }
            return !file.createNewFile();
        }
        return true;
    }

    public static boolean writeFile(String content, String path) {
        File file = new File(path);
        try {
            if (CheckFile(file, false)) {
                return false;
            }
            try (OutputStream writer = new FileOutputStream(file)) {
                byte[] Bytes = content.getBytes();
                writer.write(Bytes);
                writer.flush();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String readFile(String path) {
        File file = new File(path);
        try {
            if (CheckFile(file, false)) {
                return null;
            }
            StringBuilder result = new StringBuilder();
            try (InputStream file_stream = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(file_stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }
            return result.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
