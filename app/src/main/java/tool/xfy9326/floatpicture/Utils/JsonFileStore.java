package tool.xfy9326.floatpicture.Utils;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonFileStore {
    public interface JsonMutation {
        boolean mutate(JSONObject jsonObject) throws JSONException;
    }

    public interface JsonMapMutation {
        boolean mutate(LinkedHashMap<String, JSONObject> jsonObjects) throws JSONException;
    }

    private static final Object PROCESS_LOCK = new Object();
    private static final String LOCK_FILE_NAME = ".json-store.lock";
    private static final String TRANSACTION_FILE_NAME = ".json-store.transaction";
    private static final String TRANSACTION_FILE_TEMP_NAME = ".json-store.transaction.tmp";
    private static final String TRANSACTION_BACKUP_SUFFIX = ".transaction.backup";
    private static final String TRANSACTION_TEMP_SUFFIX = ".transaction.tmp";
    private static final String KEY_FILES = "files";
    private static final String KEY_NAME = "name";
    private static final String KEY_EXISTED = "existed";

    private JsonFileStore() {
    }

    public static JSONObject read(String fileName) {
        return withLockedStore(() -> readUnlocked(fileName), new JSONObject());
    }

    public static boolean write(String fileName, JSONObject jsonObject) {
        if (jsonObject == null) {
            return false;
        }
        return withLockedStore(() -> writeUnlocked(fileName, jsonObject), false);
    }

    public static boolean writeAll(LinkedHashMap<String, JSONObject> jsonObjects) {
        if (jsonObjects == null || jsonObjects.isEmpty()) {
            return true;
        }
        return withLockedStore(() -> {
            return writeAllUnlocked(jsonObjects);
        }, false);
    }

    public static boolean updateAll(String[] fileNames, JsonMapMutation mutation) {
        if (fileNames == null || fileNames.length == 0 || mutation == null) {
            return false;
        }
        return withLockedStore(() -> {
            LinkedHashMap<String, JSONObject> jsonObjects = new LinkedHashMap<>();
            for (String fileName : fileNames) {
                if (fileName == null) {
                    return false;
                }
                jsonObjects.put(fileName, readUnlocked(fileName));
            }
            if (!mutation.mutate(jsonObjects)) {
                return true;
            }
            return writeAllUnlocked(jsonObjects);
        }, false);
    }

    public static boolean update(String fileName, JsonMutation mutation) {
        if (mutation == null) {
            return false;
        }
        return withLockedStore(() -> {
            JSONObject jsonObject = readUnlocked(fileName);
            if (!mutation.mutate(jsonObject)) {
                return true;
            }
            return writeUnlocked(fileName, jsonObject);
        }, false);
    }

    public static JSONObject copy(JSONObject jsonObject) {
        if (jsonObject == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private interface LockedJsonOperation<T> {
        T run() throws IOException, JSONException;
    }

    private static <T> T withLockedStore(LockedJsonOperation<T> operation, T fallback) {
        synchronized (PROCESS_LOCK) {
            File dataDir = new File(Config.getDataDir());
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                return fallback;
            }
            File lockFile = new File(dataDir, LOCK_FILE_NAME);
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(lockFile, "rw");
                 FileChannel fileChannel = randomAccessFile.getChannel();
                 FileLock ignored = fileChannel.lock()) {
                recoverPendingTransaction();
                return operation.run();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                return fallback;
            }
        }
    }

    private static JSONObject readUnlocked(String fileName) throws IOException, JSONException {
        File file = resolveDataFile(fileName);
        if (!file.exists() || !file.isFile()) {
            return new JSONObject();
        }
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = inputStream.read(bytes, offset, bytes.length - offset)) != -1) {
                offset += read;
            }
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        String normalized = content.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static boolean writeUnlocked(String fileName, JSONObject jsonObject) throws IOException {
        File file = resolveDataFile(fileName);
        return writeFileUnlocked(file, jsonObject.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean writeAllUnlocked(LinkedHashMap<String, JSONObject> jsonObjects) throws IOException, JSONException {
        LinkedHashMap<String, File> targetFiles = new LinkedHashMap<>();
        LinkedHashMap<String, byte[]> nextBytes = new LinkedHashMap<>();
        LinkedHashMap<String, TransactionFile> transactionFiles = new LinkedHashMap<>();

        for (Map.Entry<String, JSONObject> entry : jsonObjects.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return false;
            }
            File file = resolveDataFile(entry.getKey());
            targetFiles.put(entry.getKey(), file);
            nextBytes.put(entry.getKey(), entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
        }

        try {
            for (Map.Entry<String, File> entry : targetFiles.entrySet()) {
                File file = entry.getValue();
                ensureParentDirectory(file);
                TransactionFile transactionFile = TransactionFile.create(entry.getKey(), file);
                prepareTransactionBackup(transactionFile);
                writeBytesToFile(transactionFile.tempFile, nextBytes.get(entry.getKey()));
                transactionFiles.put(entry.getKey(), transactionFile);
            }

            if (!writeTransactionJournal(transactionFiles)) {
                rollbackTransaction(transactionFiles);
                cleanupTransactionFiles(transactionFiles);
                return false;
            }

            for (TransactionFile transactionFile : transactionFiles.values()) {
                if (!replaceWithTemp(transactionFile.targetFile, transactionFile.tempFile)) {
                    rollbackTransaction(transactionFiles);
                    if (clearTransactionJournal()) {
                        cleanupTransactionFiles(transactionFiles);
                    }
                    return false;
                }
            }
            syncTransactionDirectories(transactionFiles);
            if (!clearTransactionJournal()) {
                rollbackTransaction(transactionFiles);
                return false;
            }
            cleanupTransactionFiles(transactionFiles);
            return true;
        } catch (IOException | JSONException e) {
            if (!getTransactionJournalFile().exists()) {
                cleanupTransactionFiles(transactionFiles);
            }
            throw e;
        } finally {
            cleanupTransactionTemps(transactionFiles);
        }
    }

    private static boolean writeFileUnlocked(File file, byte[] bytes) throws IOException {
        ensureParentDirectory(file);
        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        try {
            writeBytesToFile(tempFile, bytes);
            boolean replaced = replaceWithTemp(file, tempFile);
            if (replaced) {
                syncDirectory(file.getParentFile());
            }
            return replaced;
        } finally {
            deleteIfExists(tempFile);
        }
    }

    private static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
    }

    private static void writeBytesToFile(File file, byte[] bytes) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.getFD().sync();
        }
    }

    private static boolean replaceWithTemp(File file, File tempFile) {
        try {
            Os.rename(tempFile.getAbsolutePath(), file.getAbsolutePath());
            return true;
        } catch (ErrnoException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void recoverPendingTransaction() throws IOException, JSONException {
        File transactionFile = getTransactionJournalFile();
        if (!transactionFile.exists()) {
            return;
        }
        LinkedHashMap<String, TransactionFile> transactionFiles = readTransactionJournal(transactionFile);
        rollbackTransaction(transactionFiles);
        if (!clearTransactionJournal()) {
            throw new IOException("Failed to clear JSON transaction journal");
        }
        cleanupTransactionFiles(transactionFiles);
    }

    private static void prepareTransactionBackup(TransactionFile transactionFile) throws IOException {
        deleteIfExistsOrThrow(transactionFile.tempFile);
        deleteIfExistsOrThrow(transactionFile.backupFile);
        if (!transactionFile.existed) {
            syncDirectory(transactionFile.targetFile.getParentFile());
            return;
        }
        if (!transactionFile.targetFile.isFile()) {
            throw new IOException("Not a file: " + transactionFile.targetFile.getAbsolutePath());
        }
        writeBytesToFile(transactionFile.backupFile, readBytes(transactionFile.targetFile));
        syncDirectory(transactionFile.targetFile.getParentFile());
    }

    private static boolean writeTransactionJournal(LinkedHashMap<String, TransactionFile> transactionFiles) throws IOException, JSONException {
        JSONObject transactionObject = new JSONObject();
        JSONArray fileArray = new JSONArray();
        for (TransactionFile transactionFile : transactionFiles.values()) {
            JSONObject fileObject = new JSONObject();
            fileObject.put(KEY_NAME, transactionFile.fileName);
            fileObject.put(KEY_EXISTED, transactionFile.existed);
            fileArray.put(fileObject);
        }
        transactionObject.put(KEY_FILES, fileArray);
        boolean saved = writeFileUnlocked(getTransactionJournalFile(), transactionObject.toString().getBytes(StandardCharsets.UTF_8));
        if (saved) {
            syncDirectory(new File(Config.getDataDir()));
        }
        return saved;
    }

    private static LinkedHashMap<String, TransactionFile> readTransactionJournal(File transactionFile) throws IOException, JSONException {
        JSONObject transactionObject = new JSONObject(new String(readBytes(transactionFile), StandardCharsets.UTF_8));
        JSONArray fileArray = transactionObject.getJSONArray(KEY_FILES);
        LinkedHashMap<String, TransactionFile> transactionFiles = new LinkedHashMap<>();
        for (int index = 0; index < fileArray.length(); index++) {
            JSONObject fileObject = fileArray.getJSONObject(index);
            String fileName = fileObject.getString(KEY_NAME);
            boolean existed = fileObject.getBoolean(KEY_EXISTED);
            transactionFiles.put(fileName, TransactionFile.fromJournal(fileName, existed));
        }
        return transactionFiles;
    }

    private static void rollbackTransaction(LinkedHashMap<String, TransactionFile> transactionFiles) throws IOException {
        for (TransactionFile transactionFile : transactionFiles.values()) {
            if (transactionFile.existed) {
                if (!transactionFile.backupFile.exists() || !transactionFile.backupFile.isFile()) {
                    throw new IOException("Missing JSON transaction backup: " + transactionFile.backupFile.getAbsolutePath());
                }
                if (!writeFileUnlocked(transactionFile.targetFile, readBytes(transactionFile.backupFile))) {
                    throw new IOException("Failed to restore JSON transaction backup: " + transactionFile.targetFile.getAbsolutePath());
                }
            } else {
                deleteIfExistsOrThrow(transactionFile.targetFile);
                syncDirectory(transactionFile.targetFile.getParentFile());
            }
            deleteIfExists(transactionFile.tempFile);
        }
    }

    private static boolean clearTransactionJournal() {
        boolean transactionDeleted = deleteIfExists(getTransactionJournalFile());
        boolean tempDeleted = deleteIfExists(new File(Config.getDataDir() + TRANSACTION_FILE_TEMP_NAME));
        if (transactionDeleted && tempDeleted) {
            syncDirectory(new File(Config.getDataDir()));
        }
        return transactionDeleted && tempDeleted;
    }

    private static void cleanupTransactionFiles(LinkedHashMap<String, TransactionFile> transactionFiles) {
        for (TransactionFile transactionFile : transactionFiles.values()) {
            deleteIfExists(transactionFile.backupFile);
            deleteIfExists(transactionFile.tempFile);
        }
        syncTransactionDirectories(transactionFiles);
    }

    private static void cleanupTransactionTemps(LinkedHashMap<String, TransactionFile> transactionFiles) {
        for (TransactionFile transactionFile : transactionFiles.values()) {
            deleteIfExists(transactionFile.tempFile);
        }
    }

    private static void syncTransactionDirectories(LinkedHashMap<String, TransactionFile> transactionFiles) {
        for (TransactionFile transactionFile : transactionFiles.values()) {
            syncDirectory(transactionFile.targetFile.getParentFile());
        }
    }

    private static boolean deleteIfExists(File file) {
        return file == null || !file.exists() || file.delete();
    }

    private static void deleteIfExistsOrThrow(File file) throws IOException {
        if (!deleteIfExists(file)) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        if (file.length() > Integer.MAX_VALUE) {
            throw new IOException("File too large: " + file.getAbsolutePath());
        }
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = inputStream.read(bytes, offset, bytes.length - offset)) != -1) {
                offset += read;
            }
        }
        return bytes;
    }

    private static File getTransactionJournalFile() {
        return new File(Config.getDataDir() + TRANSACTION_FILE_NAME);
    }

    private static void syncDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        FileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
            Os.fsync(fileDescriptor);
        } catch (ErrnoException ignored) {
        } finally {
            if (fileDescriptor != null) {
                try {
                    Os.close(fileDescriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    private static final class TransactionFile {
        private final String fileName;
        private final File targetFile;
        private final File tempFile;
        private final File backupFile;
        private final boolean existed;

        private TransactionFile(String fileName, File targetFile, boolean existed) {
            this.fileName = fileName;
            this.targetFile = targetFile;
            this.tempFile = new File(targetFile.getAbsolutePath() + TRANSACTION_TEMP_SUFFIX);
            this.backupFile = new File(targetFile.getAbsolutePath() + TRANSACTION_BACKUP_SUFFIX);
            this.existed = existed;
        }

        private static TransactionFile create(String fileName, File targetFile) {
            return new TransactionFile(fileName, targetFile, targetFile.exists());
        }

        private static TransactionFile fromJournal(String fileName, boolean existed) {
            return new TransactionFile(fileName, resolveDataFile(fileName), existed);
        }
    }

    private static File resolveDataFile(String fileName) {
        return new File(Config.getDataDir() + fileName);
    }
}
