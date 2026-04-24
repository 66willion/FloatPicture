package tool.xfy9326.floatpicture.Methods;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import java.lang.ref.WeakReference;

import tool.xfy9326.floatpicture.R;

public class PermissionMethods {

    public static boolean hasOverlayPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    public static Intent createOverlayPermissionIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static void askOverlayPermission(final Activity activity, final Runnable launchPermissionRequest) {
        if (!hasOverlayPermission(activity)) {
            AlertDialog.Builder overlayPermission = new AlertDialog.Builder(activity);
            overlayPermission.setTitle(R.string.permission_warn);
            overlayPermission.setMessage(R.string.permission_warn_overlay_explanation);
            overlayPermission.setPositiveButton(R.string.done, (dialogInterface, i) -> launchPermissionRequest.run());
            overlayPermission.setNegativeButton(R.string.cancel, (dialogInterface, i) -> activity.finish());
            overlayPermission.setCancelable(false);
            overlayPermission.show();
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static void delayOverlayPermissionCheck(final Context context) {
        // 用 WeakReference 避免 lambda 在 600ms 延迟期间强引用 Activity Context
        WeakReference<Context> weakContext = new WeakReference<>(context);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Context ctx = weakContext.get();
            if (ctx == null) {
                return;
            }
            if (!hasOverlayPermission(ctx)) {
                ApplicationMethods.showToast(ctx, R.string.permission_warn_overlay_intent);
            } else {
                OverlayRuntimeController.startRuntime(ctx);
                IOMethods.setNoMedia();
            }
        }, 600);
    }
}
