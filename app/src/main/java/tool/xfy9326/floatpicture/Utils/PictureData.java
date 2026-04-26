package tool.xfy9326.floatpicture.Utils;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import tool.xfy9326.floatpicture.Methods.CodeMethods;

public class PictureData {

    private static final String DataFileName = "PictureData.list";
    private static final String ListFileName = "PictureList.list";
    private String id;
    private JSONObject detailObject;
    private JSONObject listObject;
    private JSONObject dataObject;

    public PictureData() {
    }

    /** 保留给旧调用点；数据按 PictureData 实例读取快照，不再使用跨实例缓存。 */
    public static synchronized void invalidateCache() {
    }

    public void setDataControl(String id) {
        this.id = id;
        loadListObject();
        loadDataObject();
        this.detailObject = getDetailObject(this.id);
    }

    private JSONObject loadListObject() {
        if (listObject != null) {
            return listObject;
        }
        listObject = JsonFileStore.read(ListFileName);
        return listObject;
    }

    private JSONObject loadDataObject() {
        if (dataObject != null) {
            return dataObject;
        }
        dataObject = JsonFileStore.read(DataFileName);
        return dataObject;
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

    public boolean has(String name) {
        return detailObject != null && detailObject.has(name);
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
        JsonFileStore.updateAll(new String[]{ListFileName, DataFileName}, jsonObjects -> {
            JSONObject currentListObject = jsonObjects.get(ListFileName);
            JSONObject currentDataObject = jsonObjects.get(DataFileName);
            if (currentListObject == null || currentDataObject == null) {
                return false;
            }
            if (pictureName != null) {
                currentListObject.put(id, pictureName);
            }
            currentDataObject.put(id, detailObject);
            listObject = JsonFileStore.copy(currentListObject);
            dataObject = JsonFileStore.copy(currentDataObject);
            detailObject = getDetailObject(this.id);
            return true;
        });
    }

    public void commitData() {
        JsonFileStore.update(DataFileName, currentDataObject -> {
            currentDataObject.put(id, detailObject);
            dataObject = JsonFileStore.copy(currentDataObject);
            detailObject = getDetailObject(this.id);
            return true;
        });
    }

    public void setAllPictureShowEnabled(LinkedHashMap<String, String> pictureList, boolean visible) {
        if (pictureList == null || pictureList.isEmpty()) {
            return;
        }
        JsonFileStore.update(DataFileName, currentDataObject -> {
            for (String pictureId : pictureList.keySet()) {
                if (pictureId == null || pictureId.isEmpty()) {
                    continue;
                }
                JSONObject currentDetailObject = currentDataObject.optJSONObject(pictureId);
                if (currentDetailObject == null) {
                    currentDetailObject = new JSONObject();
                }
                currentDetailObject.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
                currentDataObject.put(pictureId, currentDetailObject);
            }
            return true;
        });
    }

    public void setPictureShowEnabled(Set<String> pictureIds, boolean visible) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        JsonFileStore.update(DataFileName, currentDataObject -> {
            for (String pictureId : pictureIds) {
                putPictureShowEnabled(currentDataObject, pictureId, visible);
            }
            return true;
        });
    }

    public void setPicturesShowEnabled(Map<String, Boolean> visibilityById) {
        if (visibilityById == null || visibilityById.isEmpty()) {
            return;
        }
        JsonFileStore.update(DataFileName, currentDataObject -> {
            for (Map.Entry<String, Boolean> entry : visibilityById.entrySet()) {
                String pictureId = entry.getKey();
                Boolean visible = entry.getValue();
                if (visible == null) {
                    continue;
                }
                putPictureShowEnabled(currentDataObject, pictureId, visible);
            }
            return true;
        });
    }

    public void remove() {
        JsonFileStore.updateAll(new String[]{ListFileName, DataFileName}, jsonObjects -> {
            JSONObject currentListObject = jsonObjects.get(ListFileName);
            JSONObject currentDataObject = jsonObjects.get(DataFileName);
            if (currentListObject == null || currentDataObject == null || !currentListObject.has(id)) {
                return false;
            }
            currentListObject.remove(id);
            currentDataObject.remove(id);
            listObject = JsonFileStore.copy(currentListObject);
            dataObject = JsonFileStore.copy(currentDataObject);
            return true;
        });
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

    private static void putPictureShowEnabled(JSONObject currentDataObject, String pictureId, boolean visible) throws JSONException {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        JSONObject currentDetailObject = currentDataObject.optJSONObject(pictureId);
        if (currentDetailObject == null) {
            currentDetailObject = new JSONObject();
        }
        currentDetailObject.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
        currentDataObject.put(pictureId, currentDetailObject);
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

}

