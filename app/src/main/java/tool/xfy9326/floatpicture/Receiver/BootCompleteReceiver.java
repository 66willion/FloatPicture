package tool.xfy9326.floatpicture.Receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.preference.PreferenceManager;

import java.util.Objects;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            boolean bootAutoRun = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(Config.PREFERENCE_BOOT_AUTO_RUN, false);
            boolean pureOverlayMode = ApplicationMethods.isPureOverlayModeEnabled(context)
                    && OverlayRuntimeStateStore.hasPureOverlayManagedPictureIds(context);
            if ((bootAutoRun || pureOverlayMode) && PermissionMethods.canStartOverlayRuntime(context)) {
                ApplicationMethods.startNotificationControl(context);
            }
        }
    }
}
