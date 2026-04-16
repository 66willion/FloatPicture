package tool.xfy9326.floatpicture.Services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.ManageListAdapter;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "channel_default";
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builderManage;

    public static Intent createIntent(Context context, String action) {
        return new Intent(context, NotificationService.class).setAction(action);
    }

    public static void start(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true)) {
            return;
        }
        ContextCompat.startForegroundService(context, createIntent(context, Config.INTENT_ACTION_NOTIFICATION_START));
    }

    public static void refresh(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true)) {
            return;
        }
        ContextCompat.startForegroundService(context, createIntent(context, Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT));
    }

    private static void createNotificationChannel(@NonNull Context context, @NonNull NotificationManagerCompat notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
                notificationChannel.setDescription(context.getString(R.string.notification_channel_des));
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationChannel.setShowBadge(false);
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(this, NotificationManagerCompat.from(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForegroundStarted();
        String action = intent != null ? intent.getAction() : Config.INTENT_ACTION_NOTIFICATION_START;
        if (Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK.equals(action)) {
            toggleAllWindowsVisible();
        }
        updateNotification();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        builderManage = null;
        remoteViews = null;
        super.onDestroy();
    }

    private void ensureForegroundStarted() {
        if (builderManage == null) {
            builderManage = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(createContentIntent());
            remoteViews = new RemoteViews(getPackageName(), R.layout.notification_manage);
            builderManage.setContent(remoteViews);
            startForegroundCompat(builderManage.build());
        }
    }

    private PendingIntent createContentIntent() {
        Intent intentMain = new Intent(this, MainActivity.class);
        intentMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intentMain, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createToggleIntent() {
        return PendingIntent.getService(
                this,
                1,
                createIntent(this, Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Config.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Config.NOTIFICATION_ID, notification);
        }
    }

    private void toggleAllWindowsVisible() {
        MainApplication mainApplication = (MainApplication) getApplicationContext();
        boolean visible = !mainApplication.getWinVisible();
        ManageMethods.setAllWindowsVisible(this, visible);
        ManageListAdapter manageListAdapter = mainApplication.getManageListAdapter();
        if (manageListAdapter != null) {
            manageListAdapter.notifyDataSetChanged();
        }
    }

    private void updateNotification() {
        if (builderManage == null || remoteViews == null) {
            return;
        }
        MainApplication mainApplication = (MainApplication) getApplicationContext();
        remoteViews.setImageViewResource(R.id.imageview_notification_application, R.mipmap.ic_launcher);
        remoteViews.setTextViewText(R.id.textview_picture_num, getString(R.string.notification_picture_count, String.valueOf(ManageMethods.getWindowCount())));
        remoteViews.setImageViewResource(
                R.id.imageview_set_picture_view,
                mainApplication.getWinVisible() ? R.drawable.ic_visible : R.drawable.ic_invisible
        );
        remoteViews.setOnClickPendingIntent(R.id.imageview_set_picture_view, createToggleIntent());
        builderManage.setContent(remoteViews);

        Notification notification = builderManage.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(Config.NOTIFICATION_ID, notification);
        }
    }
}
