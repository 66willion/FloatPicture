package tool.xfy9326.floatpicture.Utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;

import tool.xfy9326.floatpicture.MainApplication;

public class Config {
    public final static int NOTIFICATION_ID = 4500;

    public final static String INTENT_PICTURE_EDIT_POSITION = "EDIT_POSITION";
    public final static String INTENT_PICTURE_EDIT_ID = "EDIT_ID";
    public final static String INTENT_PICTURE_EDIT_MODE = "EDIT_MODE";
    public final static String INTENT_PICTURE_WAS_HIDDEN = "PICTURE_WAS_HIDDEN";
    public final static String INTENT_PICTURE_BATCH_EDIT_MODE = "BATCH_EDIT_MODE";
    public final static String INTENT_PICTURE_BATCH_EDIT_IDS = "BATCH_EDIT_IDS";
    public final static String INTENT_PICTURE_BATCH_IMPORT_MODE = "BATCH_IMPORT_MODE";

    public final static String INTENT_ACTION_NOTIFICATION_START = "ACTION_NOTIFICATION_START";
    public final static String INTENT_ACTION_NOTIFICATION_BUTTON_CLICK = "ACTION_NOTIFICATION_BUTTON_CLICK";
    public final static String INTENT_ACTION_NOTIFICATION_UPDATE_COUNT = "ACTION_NOTIFICATION_UPDATE_COUNT";

    public final static String DATA_PICTURE_SHOW_ENABLED = "SHOW_ENABLED";
    public final static String DATA_PICTURE_POSITION_X = "POSITION_X";
    public final static String DATA_PICTURE_POSITION_Y = "POSITION_Y";
    public final static String DATA_PICTURE_ZOOM = "ZOOM";
    public final static String DATA_PICTURE_DEFAULT_ZOOM = "DEFAULT_ZOOM";
    public final static String DATA_PICTURE_ALPHA = "ALPHA";
    public final static String DATA_PICTURE_DEGREE = "DEGREE";
    public final static String DATA_PICTURE_CORNER_RADIUS_RATIO = "CORNER_RADIUS_RATIO";
    public final static String DATA_PICTURE_CORNER_RADIUS_MASK = "CORNER_RADIUS_MASK";
    public final static String DATA_PICTURE_EDGE_FEATHER_RATIO = "EDGE_FEATHER_RATIO";
    public final static String DATA_PICTURE_EDGE_FEATHER_MASK = "EDGE_FEATHER_MASK";
    public final static String DATA_PICTURE_TOUCH_AND_MOVE = "TOUCH_AND_MOVE";
    public final static String DATA_ALLOW_PICTURE_OVER_LAYOUT = "ALLOW_PICTURE_OVER_LAYOUT";

    public final static int MASK_CORNER_TOP_LEFT = 1;
    public final static int MASK_CORNER_TOP_RIGHT = 1 << 1;
    public final static int MASK_CORNER_BOTTOM_RIGHT = 1 << 2;
    public final static int MASK_CORNER_BOTTOM_LEFT = 1 << 3;
    public final static int MASK_CORNER_ALL = MASK_CORNER_TOP_LEFT | MASK_CORNER_TOP_RIGHT | MASK_CORNER_BOTTOM_RIGHT | MASK_CORNER_BOTTOM_LEFT;

    public final static int MASK_EDGE_TOP = 1;
    public final static int MASK_EDGE_BOTTOM = 1 << 1;
    public final static int MASK_EDGE_LEFT = 1 << 2;
    public final static int MASK_EDGE_RIGHT = 1 << 3;
    public final static int MASK_EDGE_ALL = MASK_EDGE_TOP | MASK_EDGE_BOTTOM | MASK_EDGE_LEFT | MASK_EDGE_RIGHT;

    public final static boolean DATA_DEFAULT_PICTURE_SHOW_ENABLED = true;
    public final static int DATA_DEFAULT_PICTURE_POSITION_X = 100;
    public final static int DATA_DEFAULT_PICTURE_POSITION_Y = 100;
    public final static float DATA_DEFAULT_PICTURE_ALPHA = 0.5f;
    public final static float DATA_DEFAULT_PICTURE_DEGREE = 0f;
    public final static float DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO = 0f;
    public final static int DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK = MASK_CORNER_ALL;
    public final static float DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO = 0f;
    public final static int DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK = MASK_EDGE_ALL;
    public final static boolean DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE = false;
    public final static boolean DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT = false;

    public final static String PREFERENCE_CATEGORY_GENERAL = "settings_category_general";
    public final static String PREFERENCE_PICTURE_NAME = "settings_picture_name";
    public final static String PREFERENCE_PICTURE_REPLACE = "settings_picture_replace";
    public final static String PREFERENCE_ALLOW_PICTURE_OVER_LAYOUT = "settings_allow_picture_over_layout";
    public final static String PREFERENCE_PICTURE_RESIZE = "settings_picture_resize";
    public final static String PREFERENCE_PICTURE_FIT_SCREEN_HEIGHT = "settings_picture_fit_screen_height";
    public final static String PREFERENCE_PICTURE_ALPHA = "settings_picture_alpha";
    public final static String PREFERENCE_PICTURE_CORNER_RADIUS = "settings_picture_corner_radius";
    public final static String PREFERENCE_PICTURE_EDGE_FEATHER = "settings_picture_edge_feather";
    public final static String PREFERENCE_PICTURE_POSITION = "settings_picture_position";
    public final static String PREFERENCE_PICTURE_DEGREE = "settings_picture_degree";
    public final static String PREFERENCE_PICTURE_TOUCH_AND_MOVE = "settings_picture_touchable_and_moveable";

    public final static String PREFERENCE_BOOT_AUTO_RUN = "boot_auto_run";
    public final static String PREFERENCE_SHOW_NOTIFICATION_CONTROL = "show_notification_control";
    public final static String PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE = "pure_overlay_quick_toggle";
    public final static String PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_ENABLED = "pure_overlay_quick_toggle_enabled";
    public final static String PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_X = "pure_overlay_quick_toggle_x";
    public final static String PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE_Y = "pure_overlay_quick_toggle_y";
    public final static String PREFERENCE_NEW_PICTURE_QUALITY = "new_picture_quality";
    public final static String PREFERENCE_TOUCHABLE_POSITION_EDIT = "touchable_position_edit";
    public final static String PREFERENCE_TRUSTED_OVERLAY_ACCESSIBILITY = "trusted_overlay_accessibility";
    public final static String PREFERENCE_PURE_OVERLAY_MODE = "pure_overlay_mode";
    public final static String PREFERENCE_THEME_MODE = "theme_mode";

    public final static String LICENSE_PATH_APPLICATION = "LICENSE";
    private final static String DEFAULT_APPLICATION_DIR = "FloatPicture";
    private final static String DEFAULT_ORIGINAL_DIR_NAME = "Original";
    private final static String DEFAULT_PICTURE_DIR_NAME = "Pictures";
    private final static String DEFAULT_PICTURE_TEMP_DIR_NAME = ".TEMP";
    private final static String DEFAULT_DATA_DIR_NAME = "Data";
    private final static String NO_MEDIA_FILE_NAME = ".nomedia";

    private static Context getContext() {
        return MainApplication.getAppContext();
    }

    private static File getAppRootDir() {
        Context context = getContext();
        File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File baseDir = externalFilesDir != null ? externalFilesDir : context.getFilesDir();
        return new File(baseDir, DEFAULT_APPLICATION_DIR);
    }

    private static String ensureSeparator(File file) {
        return file.getAbsolutePath() + File.separator;
    }

    public static String getPictureTempDir() {
        return ensureSeparator(new File(new File(getAppRootDir(), DEFAULT_PICTURE_DIR_NAME), DEFAULT_PICTURE_TEMP_DIR_NAME));
    }

    public static String getOriginalPictureDir() {
        return ensureSeparator(new File(getAppRootDir(), DEFAULT_ORIGINAL_DIR_NAME));
    }

    static String getDataDir() {
        return ensureSeparator(new File(getAppRootDir(), DEFAULT_DATA_DIR_NAME));
    }

    public static String getPictureDir() {
        return ensureSeparator(new File(getAppRootDir(), DEFAULT_PICTURE_DIR_NAME));
    }

    public static String getNoMediaFilePath() {
        return new File(getAppRootDir(), NO_MEDIA_FILE_NAME).getAbsolutePath();
    }
}
