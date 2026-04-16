package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import tool.xfy9326.floatpicture.Utils.Config;

public final class ThemeMethods {
    public static final String THEME_MODE_SYSTEM = "system";
    public static final String THEME_MODE_LIGHT = "light";
    public static final String THEME_MODE_DARK = "dark";

    private ThemeMethods() {
    }

    public static void applySavedTheme(@NonNull Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        applyThemeMode(sharedPreferences.getString(Config.PREFERENCE_THEME_MODE, THEME_MODE_SYSTEM));
    }

    public static void applyThemeMode(String mode) {
        int nightMode;
        if (THEME_MODE_LIGHT.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (THEME_MODE_DARK.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
