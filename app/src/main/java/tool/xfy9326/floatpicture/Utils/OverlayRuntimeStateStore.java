package tool.xfy9326.floatpicture.Utils;

import android.content.Context;
import android.graphics.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

public final class OverlayRuntimeStateStore {
    private static final String STATE_FILE_NAME = "OverlayRuntimeState.json";
    private static final String KEY_TRUSTED_OVERLAY_ACTIVE = "trusted_overlay_active";
    private static final String KEY_PURE_OVERLAY_MODE_ENABLED = "pure_overlay_mode_enabled";
    private static final String KEY_WINDOW_POSITIONS = "window_positions";
    private static final String KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS = "pure_overlay_managed_picture_ids";
    private static final String KEY_PURE_OVERLAY_QUICK_TOGGLE = "pure_overlay_quick_toggle";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_POSITION_X = "x";
    private static final String KEY_POSITION_Y = "y";

    private OverlayRuntimeStateStore() {
    }

    public static final class PureOverlayQuickToggleSettings {
        private final boolean enabled;
        private final int x;
        private final int y;

        private PureOverlayQuickToggleSettings(boolean enabled, int x, int y) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    public static synchronized void setTrustedOverlayActive(Context context, boolean active) {
        JSONObject state = readState();
        try {
            state.put(KEY_TRUSTED_OVERLAY_ACTIVE, active);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeState(state);
    }

    public static synchronized boolean isTrustedOverlayActive(Context context) {
        return readState().optBoolean(KEY_TRUSTED_OVERLAY_ACTIVE, false);
    }

    public static synchronized void setPureOverlayModeEnabled(Context context, boolean enabled) {
        JSONObject state = readState();
        try {
            state.put(KEY_PURE_OVERLAY_MODE_ENABLED, enabled);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeState(state);
    }

    public static synchronized Boolean getPureOverlayModeEnabled(Context context) {
        JSONObject state = readState();
        if (!state.has(KEY_PURE_OVERLAY_MODE_ENABLED)) {
            return null;
        }
        return state.optBoolean(KEY_PURE_OVERLAY_MODE_ENABLED, false);
    }

    public static synchronized void savePureOverlayQuickToggleSettings(Context context, boolean enabled, int x, int y) {
        JSONObject state = readState();
        JSONObject settings = new JSONObject();
        try {
            settings.put(KEY_ENABLED, enabled);
            settings.put(KEY_POSITION_X, x);
            settings.put(KEY_POSITION_Y, y);
            state.put(KEY_PURE_OVERLAY_QUICK_TOGGLE, settings);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeState(state);
    }

    public static synchronized PureOverlayQuickToggleSettings getPureOverlayQuickToggleSettings(Context context) {
        JSONObject settings = readState().optJSONObject(KEY_PURE_OVERLAY_QUICK_TOGGLE);
        if (settings == null) {
            return null;
        }
        return new PureOverlayQuickToggleSettings(
                settings.optBoolean(KEY_ENABLED, false),
                settings.optInt(KEY_POSITION_X, 0),
                settings.optInt(KEY_POSITION_Y, 0)
        );
    }

    public static synchronized void savePureOverlayManagedPictureIds(Context context, Set<String> pictureIds) {
        JSONObject state = readState();
        if (pictureIds == null || pictureIds.isEmpty()) {
            state.remove(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS);
            writeState(state);
            return;
        }
        JSONArray pictureIdArray = new JSONArray();
        for (String pictureId : pictureIds) {
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            pictureIdArray.put(pictureId);
        }
        if (pictureIdArray.length() == 0) {
            state.remove(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS);
        } else {
            try {
                state.put(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS, pictureIdArray);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        writeState(state);
    }

    public static synchronized LinkedHashSet<String> getPureOverlayManagedPictureIds(Context context) {
        LinkedHashSet<String> pictureIds = new LinkedHashSet<>();
        JSONArray pictureIdArray = readState().optJSONArray(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS);
        if (pictureIdArray == null) {
            return pictureIds;
        }
        for (int index = 0; index < pictureIdArray.length(); index++) {
            String pictureId = pictureIdArray.optString(index, null);
            if (pictureId == null || pictureId.isEmpty()) {
                continue;
            }
            pictureIds.add(pictureId);
        }
        return pictureIds;
    }

    public static synchronized boolean hasPureOverlayManagedPictureIds(Context context) {
        return !getPureOverlayManagedPictureIds(context).isEmpty();
    }

    public static synchronized void clearPureOverlayManagedPictureIds(Context context) {
        JSONObject state = readState();
        if (!state.has(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS)) {
            return;
        }
        state.remove(KEY_PURE_OVERLAY_MANAGED_PICTURE_IDS);
        writeState(state);
    }

    public static synchronized void removePureOverlayManagedPictureId(Context context, String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        LinkedHashSet<String> pictureIds = getPureOverlayManagedPictureIds(context);
        if (!pictureIds.remove(pictureId)) {
            return;
        }
        savePureOverlayManagedPictureIds(context, pictureIds);
    }

    public static synchronized void saveWindowPosition(Context context, String pictureId, int positionX, int positionY) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        JSONObject state = readState();
        JSONObject positions = state.optJSONObject(KEY_WINDOW_POSITIONS);
        if (positions == null) {
            positions = new JSONObject();
        }
        JSONObject position = new JSONObject();
        try {
            position.put(KEY_POSITION_X, positionX);
            position.put(KEY_POSITION_Y, positionY);
            positions.put(pictureId, position);
            state.put(KEY_WINDOW_POSITIONS, positions);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeState(state);
    }

    public static synchronized Point getWindowPosition(Context context, String pictureId, int defaultX, int defaultY) {
        JSONObject positions = readState().optJSONObject(KEY_WINDOW_POSITIONS);
        if (positions == null || pictureId == null || pictureId.isEmpty()) {
            return new Point(defaultX, defaultY);
        }
        JSONObject position = positions.optJSONObject(pictureId);
        if (position == null) {
            return new Point(defaultX, defaultY);
        }
        return new Point(position.optInt(KEY_POSITION_X, defaultX), position.optInt(KEY_POSITION_Y, defaultY));
    }

    public static synchronized void clearWindowPosition(Context context, String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        JSONObject state = readState();
        JSONObject positions = state.optJSONObject(KEY_WINDOW_POSITIONS);
        if (positions == null || !positions.has(pictureId)) {
            return;
        }
        positions.remove(pictureId);
        try {
            state.put(KEY_WINDOW_POSITIONS, positions);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeState(state);
    }

    private static JSONObject readState() {
        return JsonFileStore.read(STATE_FILE_NAME);
    }

    private static void writeState(JSONObject state) {
        JsonFileStore.write(STATE_FILE_NAME, state);
    }
}
