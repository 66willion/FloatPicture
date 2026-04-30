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

    private static File getPendingReplacementBackupFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending.backup");
    }

    private static File getPendingReplacementTransactionFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".pending.transaction");
    }

    private static File getOriginalBackupFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".backup");
    }

    private static File getOriginalReplacementTransactionFile(String id) {
        return new File(Config.getOriginalPictureDir() + id + ".commit.transaction");
    }

    static File getLegacyFile(String id) {
        return new File(Config.getPictureDir() + id);
    }

    static File getDisplayFile(String id) {
        return new File(Config.getPictureTempDir() + id);
    }

    private static File getPendingDisplayFile(String id) {
        return new File(Config.getPictureTempDir() + id + ".pending");
    }

    private static File getDisplayBackupFile(String id) {
        return new File(Config.getPictureTempDir() + id + ".backup");
    }

    private static File getDisplayReplacementTransactionFile(String id) {
        return new File(Config.getPictureTempDir() + id + ".transaction");
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
        File pendingFile = getPendingOriginalFile(id);
        File transactionFile = getPendingReplacementTransactionFile(id);
        File backupFile = getPendingReplacementBackupFile(id);
        recoverReplacementWorkFiles(stagedFile, pendingFile, transactionFile, backupFile);
        if (!stagedFile.exists()) {
            return false;
        }
        return replaceFileTransactionally(
                stagedFile,
                pendingFile,
                transactionFile,
                backupFile
        );
    }

    static boolean hasPendingReplacementImage(String id) {
        return getPendingOriginalFile(id).exists();
    }

    static boolean commitPendingReplacementImage(String id) {
        File pendingFile = getPendingOriginalFile(id);
        File originalFile = getOriginalFile(id);
        File transactionFile = getOriginalReplacementTransactionFile(id);
        File backupFile = getOriginalBackupFile(id);
        recoverReplacementWorkFiles(pendingFile, originalFile, transactionFile, backupFile);
        if (!pendingFile.exists()) {
            return true;
        }
        boolean committed = replaceFileTransactionally(
                pendingFile,
                originalFile,
                transactionFile,
                backupFile
        );
        if (!committed) {
            return false;
        }
        deleteFileIfExists(getLegacyFile(id));
        deleteFileIfExists(getDisplayFile(id));
        return originalFile.exists() && !pendingFile.exists();
    }

    static void clearPendingReplacementImage(String id) {
        File pendingFile = getPendingOriginalFile(id);
        File originalFile = getOriginalFile(id);
        File transactionFile = getOriginalReplacementTransactionFile(id);
        File backupFile = getOriginalBackupFile(id);
        recoverReplacementWorkFiles(pendingFile, originalFile, transactionFile, backupFile);
        deleteFileIfExists(pendingFile);
        deleteFileIfExists(transactionFile);
    }

    static void clearStagedReplacementImage(String id) {
        File stagedFile = getStagedPendingOriginalFile(id);
        File pendingFile = getPendingOriginalFile(id);
        File transactionFile = getPendingReplacementTransactionFile(id);
        File backupFile = getPendingReplacementBackupFile(id);
        recoverReplacementWorkFiles(stagedFile, pendingFile, transactionFile, backupFile);
        deleteFileIfExists(stagedFile);
        deleteFileIfExists(transactionFile);
    }

    static boolean isPictureFileExist(String id) {
        return getOriginalFile(id).exists() || getLegacyFile(id).exists();
    }

    static boolean hasAvailablePictureContent(String id) {
        return isPictureFileExist(id) || getDisplayFile(id).exists();
    }

    static int recoverPictureWorkFiles(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        int cleanedCount = 0;
        cleanedCount += recoverReplacementWorkFiles(
                getStagedPendingOriginalFile(id),
                getPendingOriginalFile(id),
                getPendingReplacementTransactionFile(id),
                getPendingReplacementBackupFile(id)
        );
        cleanedCount += recoverReplacementWorkFiles(
                getPendingOriginalFile(id),
                getOriginalFile(id),
                getOriginalReplacementTransactionFile(id),
                getOriginalBackupFile(id)
        );
        cleanedCount += recoverReplacementWorkFiles(
                getPendingDisplayFile(id),
                getDisplayFile(id),
                getDisplayReplacementTransactionFile(id),
                getDisplayBackupFile(id)
        );
        return cleanedCount;
    }

    static void deleteAllPictureFiles(String id) {
        deleteFileIfExists(getStagedPendingOriginalFile(id));
        deleteFileIfExists(getPendingOriginalFile(id));
        deleteFileIfExists(getOriginalFile(id));
        deleteFileIfExists(getPendingReplacementTransactionFile(id));
        deleteFileIfExists(getPendingReplacementBackupFile(id));
        deleteFileIfExists(getOriginalReplacementTransactionFile(id));
        deleteFileIfExists(getOriginalBackupFile(id));
        deleteFileIfExists(getLegacyFile(id));
        deleteFileIfExists(getDisplayFile(id));
        deleteFileIfExists(getPendingDisplayFile(id));
        deleteFileIfExists(getDisplayReplacementTransactionFile(id));
        deleteFileIfExists(getDisplayBackupFile(id));
    }

    static boolean saveDisplayBitmap(String id, Bitmap bitmap, boolean recycle) {
        if (!savePendingDisplayBitmap(id, bitmap, recycle)) {
            return false;
        }
        boolean committed = commitPendingDisplayBitmap(id);
        if (!committed) {
            clearPendingDisplayBitmap(id);
        }
        return committed;
    }

    static boolean savePendingDisplayBitmap(String id, Bitmap bitmap, boolean recycle) {
        clearPendingDisplayBitmap(id);
        boolean saved = IOMethods.saveBitmapLossless(bitmap, getPendingDisplayFile(id).getAbsolutePath(), false);
        if (saved && recycle && bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return saved;
    }

    static boolean commitPendingDisplayBitmap(String id) {
        File pendingFile = getPendingDisplayFile(id);
        File displayFile = getDisplayFile(id);
        File transactionFile = getDisplayReplacementTransactionFile(id);
        File backupFile = getDisplayBackupFile(id);
        recoverReplacementWorkFiles(pendingFile, displayFile, transactionFile, backupFile);
        if (!pendingFile.exists()) {
            return false;
        }
        return replaceFileTransactionally(pendingFile, displayFile, transactionFile, backupFile);
    }

    static void clearPendingDisplayBitmap(String id) {
        File pendingFile = getPendingDisplayFile(id);
        File displayFile = getDisplayFile(id);
        File transactionFile = getDisplayReplacementTransactionFile(id);
        File backupFile = getDisplayBackupFile(id);
        recoverReplacementWorkFiles(pendingFile, displayFile, transactionFile, backupFile);
        deleteFileIfExists(pendingFile);
        deleteFileIfExists(transactionFile);
    }

    static boolean clearDisplayBitmap(String id) {
        clearPendingDisplayBitmap(id);
        boolean displayCleared = deleteFileIfExists(getDisplayFile(id));
        boolean backupCleared = deleteFileIfExists(getDisplayBackupFile(id));
        boolean transactionCleared = deleteFileIfExists(getDisplayReplacementTransactionFile(id));
        return displayCleared && backupCleared && transactionCleared;
    }

    static long buildFileVersion(File file) {
        return file.lastModified() ^ file.length();
    }

    private static String getNewPictureId(Context context, Uri uri) {
        return System.currentTimeMillis() + "-" + CodeMethods.getFileMD5String(context, uri);
    }

    private static boolean replaceFileTransactionally(File sourceFile,
                                                      File targetFile,
                                                      File transactionFile,
                                                      File backupFile) {
        if (!isRegularFile(sourceFile) || targetFile == null || transactionFile == null || backupFile == null) {
            return false;
        }
        if (!deleteFileIfExists(transactionFile) || !deleteFileIfExists(backupFile)) {
            return false;
        }
        if (!moveFileToAbsentTarget(sourceFile, transactionFile)) {
            deleteFileIfExists(transactionFile);
            return false;
        }
        boolean targetBackedUp = false;
        if (targetFile.exists()) {
            if (!targetFile.isFile() || !moveFileToAbsentTarget(targetFile, backupFile)) {
                restoreSourceFile(transactionFile, sourceFile);
                return false;
            }
            targetBackedUp = true;
        }
        if (!moveFileToAbsentTarget(transactionFile, targetFile) || !targetFile.exists()) {
            deleteFileIfExists(targetFile);
            restoreBackupFile(backupFile, targetFile, targetBackedUp);
            restoreSourceFile(transactionFile, sourceFile);
            return false;
        }
        deleteFileIfExists(transactionFile);
        deleteFileIfExists(backupFile);
        return targetFile.exists() && !sourceFile.exists() && !transactionFile.exists();
    }

    private static int recoverReplacementWorkFiles(File sourceFile,
                                                   File targetFile,
                                                   File transactionFile,
                                                   File backupFile) {
        int cleanedCount = 0;
        if (backupFile.exists() && !targetFile.exists()) {
            if (moveFileToAbsentTarget(backupFile, targetFile)) {
                cleanedCount++;
            } else {
                Log.w(TAG, "Failed to restore dangling replacement backup: " + targetFile.getAbsolutePath());
            }
        }
        if (transactionFile.exists() && !sourceFile.exists()) {
            if (moveFileToAbsentTarget(transactionFile, sourceFile)) {
                cleanedCount++;
            } else {
                Log.w(TAG, "Failed to restore dangling replacement source: " + sourceFile.getAbsolutePath());
            }
        }
        if (targetFile.exists() && backupFile.exists() && deleteFileIfExists(backupFile)) {
            cleanedCount++;
        }
        if (sourceFile.exists() && transactionFile.exists() && deleteFileIfExists(transactionFile)) {
            cleanedCount++;
        }
        return cleanedCount;
    }

    private static boolean moveFileToAbsentTarget(File sourceFile, File targetFile) {
        if (!isRegularFile(sourceFile) || targetFile == null || targetFile.exists()) {
            return false;
        }
        if (sourceFile.renameTo(targetFile)) {
            return targetFile.exists() && !sourceFile.exists();
        }
        if (!IOMethods.copyFile(sourceFile, targetFile) || !targetFile.exists()) {
            return false;
        }
        if (!deleteFileIfExists(sourceFile)) {
            deleteFileIfExists(targetFile);
            return false;
        }
        return !sourceFile.exists();
    }

    private static void restoreSourceFile(File transactionFile, File sourceFile) {
        if (transactionFile.exists() && !sourceFile.exists() && !moveFileToAbsentTarget(transactionFile, sourceFile)) {
            Log.w(TAG, "Failed to restore replacement source: " + sourceFile.getAbsolutePath());
        }
    }

    private static void restoreBackupFile(File backupFile, File targetFile, boolean targetBackedUp) {
        if (!targetBackedUp) {
            deleteFileIfExists(backupFile);
            return;
        }
        if (!backupFile.exists()) {
            Log.w(TAG, "Missing replacement backup: " + targetFile.getAbsolutePath());
            return;
        }
        if (targetFile.exists()) {
            deleteFileIfExists(targetFile);
        }
        if (!moveFileToAbsentTarget(backupFile, targetFile)) {
            Log.w(TAG, "Failed to restore replacement backup: " + targetFile.getAbsolutePath());
        }
    }

    private static boolean isRegularFile(File file) {
        return file != null && file.exists() && file.isFile();
    }

    private static boolean deleteFileIfExists(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.delete()) {
            return true;
        }
        Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
        return false;
    }
}
