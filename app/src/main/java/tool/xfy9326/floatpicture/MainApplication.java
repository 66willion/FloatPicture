package tool.xfy9326.floatpicture;

import android.app.Application;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Tools.CrashHandler;
import tool.xfy9326.floatpicture.Methods.ThemeMethods;
import tool.xfy9326.floatpicture.View.FloatImageView;
import tool.xfy9326.floatpicture.View.ManageListAdapter;

public class MainApplication extends Application {
    // 悬浮窗数量在实际使用中极少超过 20 个；超出时 LRU 淘汰最久未访问的条目并释放其 Bitmap，
    // 防止无限积累导致 OOM。若用户真的添加超过上限，被淘汰的窗口下次显示时会重新从磁盘加载。
    private static final int MAX_VIEW_REGISTER_SIZE = 20;

    private static MainApplication instance;
    private LinkedHashMap<String, View> ViewRegister;
    private ManageListAdapter manageListAdapter;
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
        // accessOrder=true：每次 get/put 都把该条目移到链表尾部，头部为最久未访问（LRU）。
        this.ViewRegister = new LinkedHashMap<String, View>(MAX_VIEW_REGISTER_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, View> eldest) {
                if (size() > MAX_VIEW_REGISTER_SIZE) {
                    // 主动释放被淘汰条目持有的 Bitmap，避免内存泄漏
                    if (eldest.getValue() instanceof FloatImageView floatImageView) {
                        ImageMethods.releasePictureView(floatImageView);
                    }
                    return true;
                }
                return false;
            }
        };
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

    public ManageListAdapter getManageListAdapter() {
        return manageListAdapter;
    }

    public void setManageListAdapter(ManageListAdapter manageListAdapter) {
        this.manageListAdapter = manageListAdapter;
    }

    public boolean isAppInit() {
        return ApplicationInit;
    }

    public void setAppInit(boolean init) {
        ApplicationInit = init;
    }

    public void registerView(String id, View mView) {
        ViewRegister.put(id, mView);
    }

    public LinkedHashMap<String, View> getRegister() {
        return ViewRegister;
    }

    public int getViewCount() {
        return ViewRegister.size();
    }

    public View getRegisteredView(String id) {
        // LinkedHashMap(accessOrder=true) 的 get() 会更新访问顺序，保持 LRU 语义正确
        return ViewRegister.getOrDefault(id, null);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean unregisterView(String id) {
        if (ViewRegister.containsKey(id)) {
            Object mView = ViewRegister.get(id);
            if (mView instanceof FloatImageView) {
                ((FloatImageView) mView).refreshDrawableState();
            }
            ViewRegister.remove(id);
            return true;
        }
        return false;
    }
}

