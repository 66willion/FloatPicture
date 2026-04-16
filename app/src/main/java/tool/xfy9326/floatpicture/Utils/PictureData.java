package tool.xfy9326.floatpicture.Utils;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;

import tool.xfy9326.floatpicture.Methods.CodeMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;

public class PictureData {

    private static final String DataFileName = "PictureData.list";
    private static final String ListFileName = "PictureList.list";

    // 静态缓存：在同一进程内跨实例共享，避免每次 setDataControl() 都读磁盘。
    // volatile 保证子线程（ClearUselessTemp）对缓存失效的可见性。
    // 写操作（commit/remove）先更新缓存再同步写磁盘，保持一致性。
    private static volatile JSONObject cachedListObject = null;
    private static volatile JSONObject cachedDataObject = null;

    private String id;
    private JSONObject detailObject;
    private JSONObject listObject;
    private JSONObject dataObject;

    public PictureData() {
    }

    /** 使静态缓存失效，下次读取时重新从磁盘加载。写操作完成后调用。 */
    public static synchronized void invalidateCache() {
        cachedListObject = null;
        cachedDataObject = null;
    }

    public void setDataControl(String id) {
        this.id = id;
        this.listObject = loadListObject();
        this.dataObject = loadDataObject();
        this.detailObject = getDetailObject(this.id);
    }

    private synchronized JSONObject loadListObject() {
        if (cachedListObject != null) {
            return cachedListObject;
        }
        JSONObject loaded = getJSONFile(ListFileName);
        cachedListObject = loaded;
        return loaded;
    }

    private synchronized JSONObject loadDataObject() {
        if (cachedDataObject != null) {
            return cachedDataObject;
        }
        JSONObject loaded = getJSONFile(DataFileName);
        cachedDataObject = loaded;
        return loaded;
    }

    @SuppressWarnings("SameParameterValue")
    public void put(String name, boolean value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public void put(String name, String value) {
        try {
            detailObject.put(name, CodeMethods.unicodeEncode(value));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void put(String name, int value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("SameParameterValue")
    public void put(String name, float value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("SameParameterValue")
    public boolean getBoolean(String name, boolean defaultValue) {
        if (detailObject.has(name)) {
            try {
                return detailObject.getBoolean(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unused")
    public String getString(String name, String defaultValue) {
        if (detailObject.has(name)) {
            try {
                return CodeMethods.unicodeDecode(detailObject.getString(name));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    public int getInt(String name, int defaultValue) {
        if (detailObject.has(name)) {
            try {
                return detailObject.getInt(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("SameParameterValue")
    public float getFloat(String name, float defaultValue) {
        if (detailObject.has(name)) {
            try {
                return (float) detailObject.getDouble(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    public void commit(String pictureName) {
        try {
            if (pictureName != null) {
                listObject.put(id, pictureName);
            }
            dataObject.put(id, detailObject);
            // 先更新缓存，再写磁盘，保持内存与磁盘一致
            cachedListObject = listObject;
            cachedDataObject = dataObject;
            setJSONFile(ListFileName, listObject);
            setJSONFile(DataFileName, dataObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void remove() {
        if (listObject.has(id)) {
            listObject.remove(id);
            dataObject.remove(id);
            cachedListObject = listObject;
            cachedDataObject = dataObject;
            setJSONFile(ListFileName, listObject);
            setJSONFile(DataFileName, dataObject);
        }
    }

    private JSONObject getDetailObject(String id) {
        if (dataObject.has(id)) {
            try {
                return dataObject.getJSONObject(id);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONObject();
    }

    public LinkedHashMap<String, String> getListArray() {
        // getListArray() 是独立方法，直接走缓存或磁盘，不依赖 setDataControl 的状态
        JSONObject listObj = loadListObject();
        try {
            Iterator<String> iterator = listObj.keys();
            LinkedHashMap<String, String> arr = new LinkedHashMap<>();
            String key;
            while (iterator.hasNext()) {
                key = iterator.next();
                arr.put(key, listObj.getString(key));
            }
            return arr;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject getJSONFile(String FileName) {
        String content = IOMethods.readFile(Config.getDataDir() + FileName);
        if (content != null) {
            try {
                if (!content.isEmpty()) {
                    return new JSONObject(content);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONObject();
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean setJSONFile(String FileName, JSONObject jsonObject) {
        return IOMethods.writeFile(jsonObject.toString(), Config.getDataDir() + FileName);
    }

}

