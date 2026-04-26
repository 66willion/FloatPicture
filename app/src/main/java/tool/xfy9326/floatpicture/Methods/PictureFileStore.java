package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import java.io.File;

import tool.xfy9326.floatpicture.Utils.Config;

final class PictureFileStore {
    private static final String TAG = "PictureFileStore";

    private PictureFileStore() {
    }

    static File getOriginalFile(String id) {
        return new File(Config.getOriginalPictureDir() + id);
    }

    static File getPendingOriginalFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending");
    }

    static File getStagedPendingOriginalFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending.new");
    }

    static File getLegacyFile(String id) {
        return new File(Config.getPictureDir() + id);
    }

    static File getDisplayFile(String id) {
        return new File(Config.getPictureTempDir() + id);
    }

    static File getAvailableSourceFile(String id) {
        File originalFile = getOriginalFile(id);
        if (originalFile.exists()) {
            return originalFile;
        }
        File legacyFile = getLegacyFile(id);
        if (legacyFile.exists()) {
            return legacyFile;
        }
        return null;
    }

    static boolean isOriginalStorageFile(File imageFile) {
        return imageFile != null && imageFile.getAbsolutePath().startsWith(Config.getOriginalPictureDir());
    }

    static String setNewImage(Context context, Uri uri) {
        try {
            String id = getNewPictureId(context, uri);
            if (IOMethods.copyUriToFile(context, uri, getOriginalFile(id).getAbsolutePath())) {
                return id;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static boolean stageReplacementImage(Context context, String id, Uri uri) {
        try {
            return IOMethods.copyUriToFile(context, uri, getStagedPendingOriginalFile(id).getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static boolean applyStagedReplacementImage(String id) {
        File stagedFile = getStagedPendingOriginalFile(id);
        if (!stagedFile.exists()) {
            return false;
        }
        File pendingFile = getPendingOriginalFile(id);
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

    static boolean hasPendingReplacementImage(String id) {
        return getPendingOriginalFile(id).exists();
    }

    static boolean commitPendingReplacementImage(String id) {
        File pendingFile = getPendingOriginalFile(id);
        if (!pendingFile.exists()) {
            return true;
        }
        File originalFile = getOriginalFile(id);
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
        deleteFileIfExists(getLegacyFile(id));
        deleteFileIfExists(getDisplayFile(id));
        deleteFileIfExists(pendingFile);
        deleteFileIfExists(backupFile);
        return originalFile.exists() && !pendingFile.exists();
    }

    static void clearPendingReplacementImage(String id) {
        deleteFileIfExists(getPendingOriginalFile(id));
    }

    static void clearStagedReplacementImage(String id) {
        deleteFileIfExists(getStagedPendingOriginalFile(id));
    }

    static boolean isPictureFileExist(String id) {
        return getOriginalFile(id).exists() || getLegacyFile(id).exists();
    }

    static boolean hasAvailablePictureContent(String id) {
        return isPictureFileExist(id) || getDisplayFile(id).exists();
    }

    static void deleteAllPictureFiles(String id) {
        deleteFileIfExists(getStagedPendingOriginalFile(id));
        deleteFileIfExists(getPendingOriginalFile(id));
        deleteFileIfExists(getOriginalFile(id));
        deleteFileIfExists(getLegacyFile(id));
        deleteFileIfExists(getDisplayFile(id));
    }

    static void saveDisplayBitmap(String id, Bitmap bitmap, boolean recycle) {
        IOMethods.saveBitmapLossless(bitmap, getDisplayFile(id).getAbsolutePath(), recycle);
    }

    static long buildFileVersion(File file) {
        return file.lastModified() ^ file.length();
    }

    private static String getNewPictureId(Context context, Uri uri) {
        return System.currentTimeMillis() + "-" + CodeMethods.getFileMD5String(context, uri);
    }

    private static void deleteFileIfExists(File file) {
        if (file.exists()) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
            }
        }
    }
}
