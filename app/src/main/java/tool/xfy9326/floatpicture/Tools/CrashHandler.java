package tool.xfy9326.floatpicture.Tools;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private Context mContext;

    public static CrashHandler get() {
        return new CrashHandler();
    }

    public void Catch(Context context) {
        this.mContext = context.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, @NonNull final Throwable ex) {
        // 使用主线程 Handler 显示 Toast，避免 Looper.loop() 无限循环造成僵尸线程
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(mContext, ExToString(ex), Toast.LENGTH_SHORT).show()
        );
        try {
            // 等待 Toast 有机会显示后退出，时间缩短为 2s 避免长时间卡死
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 使用非零退出码表示异常退出，与正常退出（0）区分
        System.exit(1);
    }

    private String ExToString(Throwable ex) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        return writer.toString();
    }

}

