package tool.xfy9326.floatpicture.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.preference.PreferenceManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "channel_default";

    private final LinkedHashMap<String, PreviewSession> previewSessions = new LinkedHashMap<>();
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builderManage;

    public static Intent createIntent(Context context, String action) {
        return OverlayRuntimeController.createIntent(context, action);
    }

    public static void start(Context context) {
        OverlayRuntimeController.startRuntime(context);
    }

    public static void refresh(Context context) {
        OverlayRuntimeController.refreshNotification(context);
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
        String action = intent != null ? intent.getAction() : OverlayRuntimeController.ACTION_RUNTIME_START;
        if (!OverlayRuntimeController.ACTION_RUNTIME_SHUTDOWN.equals(action)) {
            ensureForegroundStarted();
            ensureRuntimeInitialized();
        }
        boolean refreshNotification = handleAction(intent, action);
        if (OverlayRuntimeController.ACTION_RUNTIME_SHUTDOWN.equals(action)) {
            return START_NOT_STICKY;
        }
        if (refreshNotification) {
            updateNotification();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        recyclePreviewSessions();
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

    private void ensureRuntimeInitialized() {
        MainApplication mainApplication = (MainApplication) getApplicationContext();
        if (mainApplication.isAppInit()) {
            return;
        }
        ManageMethods.RunWin(this);
        mainApplication.setAppInit(true);
        IOMethods.setNoMedia();
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

    private boolean handleAction(@Nullable Intent intent, @Nullable String action) {
        if (action == null) {
            return true;
        }
        if (!OverlayRuntimeController.ACTION_RUNTIME_UPDATE_PREVIEW.equals(action)) {
            PictureData.invalidateCache();
        }
        switch (action) {
            case OverlayRuntimeController.ACTION_RUNTIME_START:
            case OverlayRuntimeController.ACTION_RUNTIME_REFRESH_NOTIFICATION:
                return true;
            case Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK:
                toggleAllWindowsVisible();
                OverlayRuntimeController.notifyRuntimeStateChanged(this);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_RECREATE_WINDOWS:
                ManageMethods.recreateVisibleWindows(this);
                reapplyPreviewSessions();
                OverlayRuntimeController.notifyRuntimeStateChanged(this);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SET_WINDOW_VISIBLE:
                handleSetWindowVisible(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SET_ALL_WINDOWS_VISIBLE:
                ManageMethods.setAllWindowsVisible(this, intent.getBooleanExtra(OverlayRuntimeController.EXTRA_VISIBLE, false));
                OverlayRuntimeController.notifyRuntimeStateChanged(this);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SYNC_PICTURE:
                handleSyncPicture(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_DELETE_PICTURE:
                handleDeletePicture(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_UPDATE_PREVIEW:
                handlePreviewUpdate(intent);
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_CANCEL_PREVIEW:
                handlePreviewEnd(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_FINISH_PREVIEW:
                handlePreviewEnd(intent);
                OverlayRuntimeController.notifyRuntimeStateChanged(this);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_RELEASE_MEMORY:
                handleReleaseMemory(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SHUTDOWN:
                shutdownRuntime();
                return false;
            default:
                return true;
        }
    }

    private void handleSetWindowVisible(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        boolean visible = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_VISIBLE, false);
        ManageMethods.setWindowVisible(this, new tool.xfy9326.floatpicture.Utils.PictureData(), pictureId, visible);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private void handleSyncPicture(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        ManageMethods.syncWindowFromDisk(this, pictureId);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private void handleDeletePicture(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        clearPreviewSession(pictureId);
        ManageMethods.DeleteWin(this, pictureId);
        OverlayRuntimeStateStore.clearWindowPosition(this, pictureId);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private void handlePreviewUpdate(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        PreviewSession previewSession = previewSessions.get(pictureId);
        if (previewSession == null) {
            previewSession = new PreviewSession(pictureId);
            previewSessions.put(pictureId, previewSession);
        }
        previewSession.previewMode = intent.getIntExtra(
                OverlayRuntimeController.EXTRA_PREVIEW_MODE,
                OverlayRuntimeController.PREVIEW_MODE_FULL
        );
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(this, pictureId);
        boolean needsBitmap = previewSession.previewMode != OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY || floatImageView == null;
        boolean reloadSource = needsBitmap && (
                intent.getBooleanExtra(OverlayRuntimeController.EXTRA_RELOAD_SOURCE, false)
                        || previewSession.sourceBitmap == null
        );
        if (reloadSource) {
            previewSession.reloadSource(this);
        }
        if (needsBitmap && previewSession.sourceBitmap == null) {
            previewSessions.remove(pictureId);
            return;
        }
        previewSession.zoom = intent.getFloatExtra(OverlayRuntimeController.EXTRA_ZOOM, 1f);
        previewSession.degree = intent.getFloatExtra(OverlayRuntimeController.EXTRA_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        previewSession.alpha = intent.getFloatExtra(OverlayRuntimeController.EXTRA_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        previewSession.cornerRadiusRatio = intent.getFloatExtra(
                OverlayRuntimeController.EXTRA_CORNER_RADIUS_RATIO,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO
        );
        previewSession.cornerRadiusMask = intent.getIntExtra(
                OverlayRuntimeController.EXTRA_CORNER_RADIUS_MASK,
                Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK
        );
        previewSession.edgeFeatherRatio = intent.getFloatExtra(
                OverlayRuntimeController.EXTRA_EDGE_FEATHER_RATIO,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO
        );
        previewSession.edgeFeatherMask = intent.getIntExtra(
                OverlayRuntimeController.EXTRA_EDGE_FEATHER_MASK,
                Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK
        );
        previewSession.positionX = intent.getIntExtra(OverlayRuntimeController.EXTRA_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        previewSession.positionY = intent.getIntExtra(OverlayRuntimeController.EXTRA_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        previewSession.touchAndMove = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        previewSession.overLayout = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        applyPreviewSession(previewSession);
    }

    private void handlePreviewEnd(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        clearPreviewSession(pictureId);
        ManageMethods.syncWindowFromDisk(this, pictureId);
    }

    private void handleReleaseMemory(@Nullable Intent intent) {
        int releasedWindowCount = ManageMethods.releaseInactiveWindowMemory(this);
        ResultReceiver resultReceiver = OverlayRuntimeController.getResultReceiver(intent);
        if (resultReceiver != null) {
            Bundle resultData = new Bundle();
            resultData.putInt(OverlayRuntimeController.EXTRA_RELEASED_WINDOW_COUNT, releasedWindowCount);
            resultReceiver.send(0, resultData);
        }
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private void shutdownRuntime() {
        recyclePreviewSessions();
        ManageMethods.CloseAllWindows(this);
        ((MainApplication) getApplicationContext()).setAppInit(false);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
        stopSelf();
    }

    private void toggleAllWindowsVisible() {
        Set<String> targetIds = getPureOverlayNotificationTargetIds();
        if (targetIds != null) {
            boolean visible = !ManageMethods.hasVisibleWindowsConfigured(this, targetIds);
            ManageMethods.setWindowsVisible(this, targetIds, visible);
            return;
        }
        boolean visible = !ManageMethods.hasVisibleWindowsConfigured(this);
        ManageMethods.setAllWindowsVisible(this, visible);
    }

    private void updateNotification() {
        if (builderManage == null || remoteViews == null) {
            return;
        }
        boolean showControl = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true);
        Set<String> targetIds = getPureOverlayNotificationTargetIds();
        boolean anyVisible = targetIds != null
                ? ManageMethods.hasVisibleWindowsConfigured(this, targetIds)
                : ManageMethods.hasVisibleWindowsConfigured(this);
        remoteViews.setImageViewResource(R.id.imageview_notification_application, R.mipmap.ic_launcher);
        remoteViews.setTextViewText(
                R.id.textview_picture_num,
                getString(R.string.notification_picture_count, String.valueOf(ManageMethods.getWindowCount()))
        );
        remoteViews.setImageViewResource(
                R.id.imageview_set_picture_view,
                anyVisible ? R.drawable.ic_visible : R.drawable.ic_invisible
        );
        remoteViews.setViewVisibility(R.id.imageview_set_picture_view, showControl ? View.VISIBLE : View.GONE);
        if (showControl) {
            remoteViews.setOnClickPendingIntent(R.id.layout_notification_toggle, createToggleIntent());
        } else {
            remoteViews.setOnClickPendingIntent(R.id.layout_notification_toggle, null);
        }
        builderManage.setContent(remoteViews);

        Notification notification = builderManage.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            try {
                notificationManager.notify(Config.NOTIFICATION_ID, notification);
            } catch (SecurityException ignored) {
            }
        }
    }

    @Nullable
    private Set<String> getPureOverlayNotificationTargetIds() {
        Set<String> targetIds = OverlayRuntimeStateStore.getPureOverlayManagedPictureIds(this);
        return targetIds.isEmpty() ? null : targetIds;
    }

    private void applyPreviewSession(@NonNull PreviewSession previewSession) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(this, previewSession.pictureId);
        if (previewSession.previewMode == OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY && floatImageView != null) {
            floatImageView.setMoveable(previewSession.touchAndMove);
            floatImageView.setOverLayout(previewSession.overLayout);
            floatImageView.setPictureAlpha(previewSession.alpha);
            WindowsMethods.updateWindow(
                    WindowsMethods.getWindowManager(this),
                    floatImageView,
                    previewSession.touchAndMove,
                    previewSession.overLayout,
                    previewSession.alpha,
                    previewSession.positionX,
                    previewSession.positionY
            );
            OverlayRuntimeStateStore.saveWindowPosition(this, previewSession.pictureId, previewSession.positionX, previewSession.positionY);
            return;
        }

        Bitmap renderedBitmap = buildPreviewBitmap(previewSession);
        if (renderedBitmap == null) {
            return;
        }
        if (floatImageView == null) {
            floatImageView = ImageMethods.createPictureView(this, renderedBitmap, previewSession.touchAndMove, previewSession.overLayout, previewSession.alpha);
            ImageMethods.saveFloatImageViewById(this, previewSession.pictureId, floatImageView);
        } else {
            ImageMethods.setPictureBitmap(floatImageView, renderedBitmap);
            floatImageView.setMoveable(previewSession.touchAndMove);
            floatImageView.setOverLayout(previewSession.overLayout);
            floatImageView.setPictureAlpha(previewSession.alpha);
        }
        WindowsMethods.createWindow(
                WindowsMethods.getWindowManager(this),
                floatImageView,
                previewSession.touchAndMove,
                previewSession.overLayout,
                previewSession.alpha,
                previewSession.positionX,
                previewSession.positionY
        );
        OverlayRuntimeStateStore.saveWindowPosition(this, previewSession.pictureId, previewSession.positionX, previewSession.positionY);
    }

    @Nullable
    private Bitmap buildPreviewBitmap(@NonNull PreviewSession previewSession) {
        if (previewSession.sourceBitmap == null) {
            return null;
        }
        return switch (previewSession.previewMode) {
            case OverlayRuntimeController.PREVIEW_MODE_OUTLINE ->
                    ImageMethods.createOutlinePreviewBitmap(
                            previewSession.sourceBitmap,
                            previewSession.zoom,
                            previewSession.degree,
                            previewSession.cornerRadiusRatio,
                            previewSession.cornerRadiusMask
                    );
            case OverlayRuntimeController.PREVIEW_MODE_LOW_RES ->
                    ImageMethods.resizeBitmapFromScaledSource(
                            previewSession.getLowResSourceBitmap(),
                            previewSession.sourceBitmap.getWidth(),
                            previewSession.sourceBitmap.getHeight(),
                            previewSession.zoom,
                            previewSession.degree,
                            previewSession.cornerRadiusRatio,
                            previewSession.cornerRadiusMask,
                            previewSession.edgeFeatherRatio,
                            previewSession.edgeFeatherMask
                    );
            default -> ImageMethods.resizeBitmap(
                    previewSession.sourceBitmap,
                    previewSession.zoom,
                    previewSession.degree,
                    previewSession.cornerRadiusRatio,
                    previewSession.cornerRadiusMask,
                    previewSession.edgeFeatherRatio,
                    previewSession.edgeFeatherMask
            );
        };
    }

    private void reapplyPreviewSessions() {
        for (Map.Entry<String, PreviewSession> entry : previewSessions.entrySet()) {
            PreviewSession previewSession = entry.getValue();
            android.graphics.Point position = OverlayRuntimeStateStore.getWindowPosition(
                    this,
                    previewSession.pictureId,
                    previewSession.positionX,
                    previewSession.positionY
            );
            previewSession.positionX = position.x;
            previewSession.positionY = position.y;
            applyPreviewSession(previewSession);
        }
    }

    private void recyclePreviewSessions() {
        for (PreviewSession previewSession : previewSessions.values()) {
            previewSession.recycle();
        }
        previewSessions.clear();
    }

    private void clearPreviewSession(@NonNull String pictureId) {
        PreviewSession previewSession = previewSessions.remove(pictureId);
        if (previewSession != null) {
            previewSession.recycle();
        }
    }

    private static final class PreviewSession {
        private final String pictureId;
        private Bitmap sourceBitmap;
        private Bitmap lowResSourceBitmap;
        private float zoom = 1f;
        private float degree = Config.DATA_DEFAULT_PICTURE_DEGREE;
        private float alpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
        private float cornerRadiusRatio = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_RATIO;
        private int cornerRadiusMask = Config.DATA_DEFAULT_PICTURE_CORNER_RADIUS_MASK;
        private float edgeFeatherRatio = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_RATIO;
        private int edgeFeatherMask = Config.DATA_DEFAULT_PICTURE_EDGE_FEATHER_MASK;
        private int positionX = Config.DATA_DEFAULT_PICTURE_POSITION_X;
        private int positionY = Config.DATA_DEFAULT_PICTURE_POSITION_Y;
        private boolean touchAndMove = Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE;
        private boolean overLayout = Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT;
        private int previewMode = OverlayRuntimeController.PREVIEW_MODE_FULL;

        private PreviewSession(@NonNull String pictureId) {
            this.pictureId = pictureId;
        }

        private void reloadSource(@NonNull Context context) {
            recycle();
            sourceBitmap = ImageMethods.getPendingEditSourceBitmap(pictureId);
            if (sourceBitmap == null) {
                sourceBitmap = ImageMethods.getEditSourceBitmap(context, pictureId);
            }
        }

        @Nullable
        private Bitmap getLowResSourceBitmap() {
            if (sourceBitmap == null) {
                return null;
            }
            if (lowResSourceBitmap == null || lowResSourceBitmap.isRecycled()) {
                lowResSourceBitmap = ImageMethods.createPreviewSourceBitmap(sourceBitmap);
            }
            return lowResSourceBitmap;
        }

        private void recycle() {
            if (lowResSourceBitmap != null && lowResSourceBitmap != sourceBitmap) {
                ImageMethods.recycleBitmap(lowResSourceBitmap);
            }
            lowResSourceBitmap = null;
            ImageMethods.recycleBitmap(sourceBitmap);
            sourceBitmap = null;
        }
    }
}
