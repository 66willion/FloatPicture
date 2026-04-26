package tool.xfy9326.floatpicture;

import android.app.Application;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.View;

import java.util.LinkedHashMap;

import tool.xfy9326.floatpicture.Tools.CrashHandler;
import tool.xfy9326.floatpicture.Methods.ThemeMethods;
import tool.xfy9326.floatpicture.Utils.WindowRegistry;

public class MainApplication extends Application {
    // 悬浮窗数量在实际使用中极少超过 20 个；超出时 LRU 淘汰最久未访问的条目并释放其 Bitmap，
    // 防止无限积累导致 OOM。若用户真的添加超过上限，被淘汰的窗口下次显示时会重新从磁盘加载。
    private static final int MAX_VIEW_REGISTER_SIZE = 20;

    private static MainApplication instance;
    private WindowRegistry windowRegistry;
    private boolean ApplicationInit;
    private boolean winVisible = true;
    private float safeWindowsAlpha = 0.8f;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ApplicationInit = false;
        if (!BuildConfig.DEBUG) {
            CrashHandler.get().Catch(this);
        }
        ThemeMethods.applySavedTheme(this);
        windowRegistry = new WindowRegistry(MAX_VIEW_REGISTER_SIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.safeWindowsAlpha = getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch();
        }
    }

    public static Context getAppContext() {
        return instance;
    }

    public float getSafeWindowsAlpha() {
        return safeWindowsAlpha;
    }

    public boolean getWinVisible() {
        return winVisible;
    }

    public void setWinVisible(boolean visible) {
        winVisible = visible;
    }

    public boolean isAppInit() {
        return ApplicationInit;
    }

    public void setAppInit(boolean init) {
        ApplicationInit = init;
    }

    public void registerView(String id, View mView) {
        windowRegistry.register(id, mView);
    }

    public LinkedHashMap<String, View> getRegisteredViewsSnapshot() {
        return windowRegistry.snapshot();
    }

    public int getViewCount() {
        return windowRegistry.size();
    }

    public View getRegisteredView(String id) {
        return windowRegistry.get(id);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean unregisterView(String id) {
        return windowRegistry.unregister(id);
    }

    public boolean unregisterViewIfSame(String id, View expectedView) {
        return windowRegistry.unregisterIfSame(id, expectedView);
    }
}

