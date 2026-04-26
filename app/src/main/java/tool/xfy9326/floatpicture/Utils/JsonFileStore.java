package tool.xfy9326.floatpicture.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
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
            for (Map.Entry<String, JSONObject> entry : jsonObjects.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    return false;
                }
            }
            for (Map.Entry<String, JSONObject> entry : jsonObjects.entrySet()) {
                if (!writeUnlocked(entry.getKey(), entry.getValue())) {
                    return false;
                }
            }
            return true;
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
            for (Map.Entry<String, JSONObject> entry : jsonObjects.entrySet()) {
                if (!writeUnlocked(entry.getKey(), entry.getValue())) {
                    return false;
                }
            }
            return true;
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
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        byte[] bytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream outputStream = new FileOutputStream(tempFile, false)) {
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.getFD().sync();
        }

        if (file.exists() && !file.delete()) {
            tempFile.delete();
            return false;
        }
        if (!tempFile.renameTo(file)) {
            tempFile.delete();
            return false;
        }
        return true;
    }

    private static File resolveDataFile(String fileName) {
        return new File(Config.getDataDir() + fileName);
    }
}
