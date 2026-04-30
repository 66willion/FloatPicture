package tool.xfy9326.floatpicture.Tools;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import tool.xfy9326.floatpicture.R;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final int EXIT_CODE_CRASH = 1;

    private Context mContext;
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    public static CrashHandler get() {
        return new CrashHandler();
    }

    public void Catch(Context context) {
        this.mContext = context.getApplicationContext();
        this.defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, @NonNull final Throwable ex) {
        if (thread == Looper.getMainLooper().getThread()) {
            showCrashToast();
        } else {
            new Handler(Looper.getMainLooper()).post(this::showCrashToast);
        }
        dispatchDefaultExceptionHandler(thread, ex);
    }

    private void showCrashToast() {
        try {
            Toast.makeText(mContext, R.string.application_crash_message, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ignored) {
        }
    }

    private void dispatchDefaultExceptionHandler(Thread thread, Throwable ex) {
        if (defaultExceptionHandler != null && defaultExceptionHandler != this) {
            defaultExceptionHandler.uncaughtException(thread, ex);
        }
        // 使用非零退出码表示异常退出，与正常退出（0）区分
        System.exit(EXIT_CODE_CRASH);
    }

}

