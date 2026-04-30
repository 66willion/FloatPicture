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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Utils.AppExecutors;
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

    private final ConcurrentHashMap<String, PreviewSession> previewSessions = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService previewRenderExecutor = createPreviewRenderExecutor();
    private final AtomicInteger notificationUpdateGeneration = new AtomicInteger();
    private PureOverlayQuickToggleController pureOverlayQuickToggleController;
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builderManage;
    private boolean notificationWindowToggleInProgress = false;

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

    private static ExecutorService createPreviewRenderExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                runnable.run();
            }, "floatpicture-preview-render");
            thread.setDaemon(false);
            return thread;
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(this, NotificationManagerCompat.from(this));
        pureOverlayQuickToggleController = new PureOverlayQuickToggleController(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : OverlayRuntimeController.ACTION_RUNTIME_START;
        boolean shutdownAction = OverlayRuntimeController.ACTION_RUNTIME_SHUTDOWN.equals(action);
        boolean startedAsForegroundService = intent != null
                && intent.getBooleanExtra(OverlayRuntimeController.EXTRA_STARTED_AS_FOREGROUND_SERVICE, false);
        if (!shutdownAction || startedAsForegroundService) {
            ensureForegroundStarted();
            if (!shutdownAction && shouldInitializeRuntime(intent, action)) {
                ensureRuntimeInitialized();
            }
        }
        boolean refreshNotification = handleAction(intent, action);
        if (shutdownAction) {
            return START_NOT_STICKY;
        }
        refreshPureOverlayQuickToggle();
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
        if (pureOverlayQuickToggleController != null) {
            pureOverlayQuickToggleController.release();
            pureOverlayQuickToggleController = null;
        }
        notificationWindowToggleInProgress = false;
        recyclePreviewSessions();
        previewRenderExecutor.shutdownNow();
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

    private boolean shouldInitializeRuntime(@Nullable Intent intent, @Nullable String action) {
        if (OverlayRuntimeController.ACTION_RUNTIME_HIDE_ALL_WINDOWS.equals(action)) {
            return false;
        }
        if ((OverlayRuntimeController.ACTION_RUNTIME_SYNC_PICTURE.equals(action)
                || OverlayRuntimeController.ACTION_RUNTIME_SYNC_PICTURES.equals(action))
                && intent != null
                && !intent.getBooleanExtra(OverlayRuntimeController.EXTRA_CREATE_IF_VISIBLE, true)) {
            return false;
        }
        if (OverlayRuntimeController.ACTION_RUNTIME_REFRESH_NOTIFICATION.equals(action)
                && intent != null
                && !intent.getBooleanExtra(OverlayRuntimeController.EXTRA_INITIALIZE_RUNTIME, true)) {
            return false;
        }
        return true;
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
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_RECREATE_WINDOWS:
                ManageMethods.recreateVisibleWindows(this, () -> {
                    reapplyPreviewSessions();
                    OverlayRuntimeController.notifyRuntimeStateChanged(this);
                    updateNotification();
                });
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_SET_WINDOW_VISIBLE:
                handleSetWindowVisible(intent);
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_SET_ALL_WINDOWS_VISIBLE:
                setPureOverlayWindowsVisible(intent);
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_HIDE_ALL_WINDOWS:
                ManageMethods.setAllWindowsVisible(this, false);
                OverlayRuntimeController.notifyRuntimeStateChanged(this);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SYNC_PICTURE:
                handleSyncPicture(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SYNC_PICTURES:
                handleSyncPictures(intent);
                return false;
            case OverlayRuntimeController.ACTION_RUNTIME_DELETE_PICTURE:
                handleDeletePicture(intent);
                return true;
            case OverlayRuntimeController.ACTION_RUNTIME_SHOW_RANDOM_WINDOW:
                return handleShowRandomWindow(intent);
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
    }

    private void handleSyncPicture(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String pictureId = intent.getStringExtra(OverlayRuntimeController.EXTRA_PICTURE_ID);
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        boolean createIfVisible = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_CREATE_IF_VISIBLE, true);
        ManageMethods.syncWindowFromDisk(this, pictureId, createIfVisible);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private void handleSyncPictures(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        ArrayList<String> pictureIds = intent.getStringArrayListExtra(OverlayRuntimeController.EXTRA_PICTURE_IDS);
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        boolean createIfVisible = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_CREATE_IF_VISIBLE, true);
        ManageMethods.syncWindowsFromDiskAsync(this, pictureIds, createIfVisible, () -> {
            OverlayRuntimeController.notifyRuntimeStateChanged(this);
            updateNotification();
        });
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
        if (!ManageMethods.DeleteWin(this, pictureId)) {
            OverlayRuntimeController.notifyRuntimeStateChanged(this);
            return;
        }
        OverlayRuntimeStateStore.clearWindowPosition(this, pictureId);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
    }

    private boolean handleShowRandomWindow(@Nullable Intent intent) {
        String pictureId = ManageMethods.showOnlyRandomWindow(this);
        ResultReceiver resultReceiver = OverlayRuntimeController.getResultReceiver(intent);
        if (resultReceiver != null) {
            Bundle resultData = new Bundle();
            if (pictureId != null && !pictureId.isEmpty()) {
                resultData.putString(OverlayRuntimeController.EXTRA_PICTURE_ID, pictureId);
            }
            resultReceiver.send(
                    pictureId != null && !pictureId.isEmpty()
                            ? OverlayRuntimeController.RANDOM_WINDOW_RESULT_SUCCESS
                            : OverlayRuntimeController.RANDOM_WINDOW_RESULT_NO_CANDIDATE,
                    resultData
            );
        }
        if (pictureId == null || pictureId.isEmpty()) {
            return false;
        }
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
        return true;
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
        PreviewRenderRequest request = previewSession.updateFromIntent(intent);
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(this, pictureId);
        if (request.previewMode == OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY && floatImageView != null) {
            applyMoveOnlyPreview(request, floatImageView);
            return;
        }
        schedulePreviewRender(request);
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
        if (pureOverlayQuickToggleController != null) {
            pureOverlayQuickToggleController.release();
        }
        recyclePreviewSessions();
        ManageMethods.CloseAllWindows(this);
        ((MainApplication) getApplicationContext()).setAppInit(false);
        OverlayRuntimeController.notifyRuntimeStateChanged(this);
        stopSelf();
    }

    private boolean toggleAllWindowsVisible() {
        if (notificationWindowToggleInProgress) {
            return false;
        }
        Set<String> targetIds = getPureOverlayNotificationTargetIds();
        if (targetIds == null) {
            return false;
        }
        boolean visible = !ManageMethods.hasVisibleRuntimeWindows(this, targetIds);
        return startNotificationWindowsVisibleChange(targetIds, visible);
    }

    private boolean setPureOverlayWindowsVisible(@Nullable Intent intent) {
        if (notificationWindowToggleInProgress) {
            return false;
        }
        if (intent == null) {
            return false;
        }
        Set<String> targetIds = getPureOverlayNotificationTargetIds();
        if (targetIds == null) {
            return false;
        }
        return startNotificationWindowsVisibleChange(
                targetIds,
                intent.getBooleanExtra(OverlayRuntimeController.EXTRA_VISIBLE, false)
        );
    }

    private boolean startNotificationWindowsVisibleChange(@NonNull Set<String> targetIds, boolean visible) {
        notificationWindowToggleInProgress = true;
        ManageMethods.setWindowsVisibleAsync(this, targetIds, visible, false, () -> notificationWindowToggleInProgress = false);
        return true;
    }

    private void updateNotification() {
        if (builderManage == null || remoteViews == null) {
            return;
        }
        int generation = notificationUpdateGeneration.incrementAndGet();
        AppExecutors.io().execute(() -> {
            NotificationUiState uiState = buildNotificationUiState();
            mainHandler.post(() -> {
                if (generation != notificationUpdateGeneration.get()
                        || builderManage == null
                        || remoteViews == null) {
                    return;
                }
                applyNotificationUiState(uiState);
            });
        });
    }

    private void refreshPureOverlayQuickToggle() {
        if (pureOverlayQuickToggleController != null) {
            pureOverlayQuickToggleController.refresh();
        }
    }

    @NonNull
    private NotificationUiState buildNotificationUiState() {
        boolean showControl = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true);
        Set<String> targetIds = getPureOverlayNotificationTargetIds();
        boolean pureOverlayToggleVisible = showControl && targetIds != null;
        boolean anyVisible = ManageMethods.hasVisibleRuntimeWindows(this, targetIds);
        int pictureCount = targetIds != null
                ? ManageMethods.getWindowCount(targetIds)
                : ManageMethods.getWindowCount();
        return new NotificationUiState(pureOverlayToggleVisible, anyVisible, pictureCount);
    }

    private void applyNotificationUiState(@NonNull NotificationUiState uiState) {
        remoteViews.setImageViewResource(R.id.imageview_notification_application, R.mipmap.ic_launcher);
        remoteViews.setTextViewText(
                R.id.textview_picture_num,
                getString(R.string.notification_picture_count, String.valueOf(uiState.pictureCount))
        );
        remoteViews.setImageViewResource(
                R.id.imageview_set_picture_view,
                uiState.anyVisible ? R.drawable.ic_visible : R.drawable.ic_invisible
        );
        remoteViews.setViewVisibility(R.id.layout_notification_toggle, uiState.pureOverlayToggleVisible ? View.VISIBLE : View.GONE);
        if (uiState.pureOverlayToggleVisible) {
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
        refreshPureOverlayQuickToggle();
    }

    private static final class NotificationUiState {
        private final boolean pureOverlayToggleVisible;
        private final boolean anyVisible;
        private final int pictureCount;

        private NotificationUiState(boolean pureOverlayToggleVisible, boolean anyVisible, int pictureCount) {
            this.pureOverlayToggleVisible = pureOverlayToggleVisible;
            this.anyVisible = anyVisible;
            this.pictureCount = pictureCount;
        }
    }

    @Nullable
    private Set<String> getPureOverlayNotificationTargetIds() {
        Set<String> targetIds = OverlayRuntimeStateStore.getPureOverlayManagedPictureIds(this);
        return targetIds.isEmpty() ? null : targetIds;
    }

    private void applyMoveOnlyPreview(@NonNull PreviewRenderRequest request, @NonNull FloatImageView floatImageView) {
        floatImageView.setMoveable(request.touchAndMove);
        floatImageView.setOverLayout(request.overLayout);
        floatImageView.setPictureAlpha(request.alpha);
        WindowsMethods.updateWindow(
                WindowsMethods.getWindowManager(this),
                floatImageView,
                request.touchAndMove,
                request.overLayout,
                request.alpha,
                request.positionX,
                request.positionY
        );
    }

    private void schedulePreviewRender(@NonNull PreviewRenderRequest request) {
        try {
            previewRenderExecutor.execute(() -> renderPreviewInBackground(request));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void renderPreviewInBackground(@NonNull PreviewRenderRequest request) {
        PreviewSession previewSession = previewSessions.get(request.pictureId);
        if (previewSession == null || !previewSession.isCurrent(request.generation)) {
            return;
        }
        Bitmap sourceBitmap = null;
        Bitmap renderedBitmap = null;
        try {
            sourceBitmap = ImageMethods.getPendingEditSourceBitmap(request.pictureId);
            if (sourceBitmap == null) {
                sourceBitmap = ImageMethods.getEditSourceBitmap(this, request.pictureId);
            }
            if (sourceBitmap == null || sourceBitmap.isRecycled()) {
                return;
            }
            if (!previewSession.isCurrent(request.generation)) {
                return;
            }
            renderedBitmap = buildPreviewBitmap(request, sourceBitmap);
            if (renderedBitmap == null || renderedBitmap.isRecycled()) {
                return;
            }
            if (!previewSession.isCurrent(request.generation)) {
                return;
            }
            if (renderedBitmap != sourceBitmap) {
                ImageMethods.recycleBitmap(sourceBitmap);
                sourceBitmap = null;
            }
            Bitmap resultBitmap = renderedBitmap;
            renderedBitmap = null;
            sourceBitmap = null;
            if (!mainHandler.post(() -> applyRenderedPreview(request, resultBitmap))) {
                ImageMethods.recycleBitmap(resultBitmap);
            }
        } catch (OutOfMemoryError e) {
            android.util.Log.e("NotificationService", "Preview render ran out of memory: " + request.pictureId, e);
        } catch (RuntimeException e) {
            android.util.Log.e("NotificationService", "Preview render failed: " + request.pictureId, e);
        } finally {
            ImageMethods.recycleBitmap(renderedBitmap);
            ImageMethods.recycleBitmap(sourceBitmap);
        }
    }

    @Nullable
    private Bitmap buildPreviewBitmap(@NonNull PreviewRenderRequest request, @NonNull Bitmap sourceBitmap) {
        return switch (request.previewMode) {
            case OverlayRuntimeController.PREVIEW_MODE_OUTLINE ->
                    ImageMethods.createOutlinePreviewBitmap(
                            sourceBitmap,
                            request.zoom,
                            request.degree,
                            request.cornerRadiusRatio,
                            request.cornerRadiusMask
                    );
            case OverlayRuntimeController.PREVIEW_MODE_LOW_RES -> {
                Bitmap lowResSourceBitmap = ImageMethods.createPreviewSourceBitmap(sourceBitmap);
                Bitmap previewBitmap = ImageMethods.resizeBitmapFromScaledSource(
                        lowResSourceBitmap,
                        sourceBitmap.getWidth(),
                        sourceBitmap.getHeight(),
                        request.zoom,
                        request.degree,
                        request.cornerRadiusRatio,
                        request.cornerRadiusMask,
                        request.edgeFeatherRatio,
                        request.edgeFeatherMask
                );
                if (lowResSourceBitmap != sourceBitmap && lowResSourceBitmap != previewBitmap) {
                    ImageMethods.recycleBitmap(lowResSourceBitmap);
                }
                yield previewBitmap;
            }
            default -> ImageMethods.resizeBitmap(
                    sourceBitmap,
                    request.zoom,
                    request.degree,
                    request.cornerRadiusRatio,
                    request.cornerRadiusMask,
                    request.edgeFeatherRatio,
                    request.edgeFeatherMask
            );
        };
    }

    private void applyRenderedPreview(@NonNull PreviewRenderRequest request, @Nullable Bitmap renderedBitmap) {
        PreviewSession previewSession = previewSessions.get(request.pictureId);
        if (previewSession == null || !previewSession.isCurrent(request.generation)) {
            ImageMethods.recycleBitmap(renderedBitmap);
            return;
        }
        if (renderedBitmap == null || renderedBitmap.isRecycled()) {
            return;
        }
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(this, request.pictureId);
        if (floatImageView == null) {
            floatImageView = ImageMethods.createPictureView(this, renderedBitmap, request.touchAndMove, request.overLayout, request.alpha);
            ImageMethods.saveFloatImageViewById(this, request.pictureId, floatImageView);
        } else {
            ImageMethods.setPictureBitmap(floatImageView, renderedBitmap);
            floatImageView.setMoveable(request.touchAndMove);
            floatImageView.setOverLayout(request.overLayout);
            floatImageView.setPictureAlpha(request.alpha);
        }
        WindowsMethods.createWindow(
                WindowsMethods.getWindowManager(this),
                floatImageView,
                request.touchAndMove,
                request.overLayout,
                request.alpha,
                request.positionX,
                request.positionY
        );
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
            PreviewRenderRequest request = previewSession.snapshotWithPosition(position.x, position.y);
            FloatImageView floatImageView = ImageMethods.getFloatImageViewById(this, request.pictureId);
            if (request.previewMode == OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY && floatImageView != null) {
                applyMoveOnlyPreview(request, floatImageView);
            } else {
                schedulePreviewRender(request);
            }
        }
    }

    private void recyclePreviewSessions() {
        for (PreviewSession previewSession : previewSessions.values()) {
            previewSession.invalidate();
        }
        previewSessions.clear();
    }

    private void clearPreviewSession(@NonNull String pictureId) {
        PreviewSession previewSession = previewSessions.remove(pictureId);
        if (previewSession != null) {
            previewSession.invalidate();
        }
    }

    private static final class PreviewRenderRequest {
        private final String pictureId;
        private final int generation;
        private final float zoom;
        private final float degree;
        private final float alpha;
        private final float cornerRadiusRatio;
        private final int cornerRadiusMask;
        private final float edgeFeatherRatio;
        private final int edgeFeatherMask;
        private final int positionX;
        private final int positionY;
        private final boolean touchAndMove;
        private final boolean overLayout;
        private final int previewMode;

        private PreviewRenderRequest(@NonNull String pictureId,
                                     int generation,
                                     float zoom,
                                     float degree,
                                     float alpha,
                                     float cornerRadiusRatio,
                                     int cornerRadiusMask,
                                     float edgeFeatherRatio,
                                     int edgeFeatherMask,
                                     int positionX,
                                     int positionY,
                                     boolean touchAndMove,
                                     boolean overLayout,
                                     int previewMode) {
            this.pictureId = pictureId;
            this.generation = generation;
            this.zoom = zoom;
            this.degree = degree;
            this.alpha = alpha;
            this.cornerRadiusRatio = cornerRadiusRatio;
            this.cornerRadiusMask = cornerRadiusMask;
            this.edgeFeatherRatio = edgeFeatherRatio;
            this.edgeFeatherMask = edgeFeatherMask;
            this.positionX = positionX;
            this.positionY = positionY;
            this.touchAndMove = touchAndMove;
            this.overLayout = overLayout;
            this.previewMode = previewMode;
        }
    }

    private static final class PreviewSession {
        private final String pictureId;
        private final AtomicInteger generation = new AtomicInteger();
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

        private PreviewRenderRequest updateFromIntent(@NonNull Intent intent) {
            zoom = intent.getFloatExtra(OverlayRuntimeController.EXTRA_ZOOM, zoom);
            degree = intent.getFloatExtra(OverlayRuntimeController.EXTRA_DEGREE, degree);
            alpha = intent.getFloatExtra(OverlayRuntimeController.EXTRA_ALPHA, alpha);
            cornerRadiusRatio = intent.getFloatExtra(OverlayRuntimeController.EXTRA_CORNER_RADIUS_RATIO, cornerRadiusRatio);
            cornerRadiusMask = intent.getIntExtra(OverlayRuntimeController.EXTRA_CORNER_RADIUS_MASK, cornerRadiusMask);
            edgeFeatherRatio = intent.getFloatExtra(OverlayRuntimeController.EXTRA_EDGE_FEATHER_RATIO, edgeFeatherRatio);
            edgeFeatherMask = intent.getIntExtra(OverlayRuntimeController.EXTRA_EDGE_FEATHER_MASK, edgeFeatherMask);
            positionX = intent.getIntExtra(OverlayRuntimeController.EXTRA_POSITION_X, positionX);
            positionY = intent.getIntExtra(OverlayRuntimeController.EXTRA_POSITION_Y, positionY);
            touchAndMove = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_TOUCH_AND_MOVE, touchAndMove);
            overLayout = intent.getBooleanExtra(OverlayRuntimeController.EXTRA_OVER_LAYOUT, overLayout);
            previewMode = intent.getIntExtra(OverlayRuntimeController.EXTRA_PREVIEW_MODE, previewMode);
            return snapshot(generation.incrementAndGet());
        }

        private PreviewRenderRequest snapshotWithPosition(int positionX, int positionY) {
            this.positionX = positionX;
            this.positionY = positionY;
            return snapshot(generation.incrementAndGet());
        }

        private boolean isCurrent(int requestGeneration) {
            return generation.get() == requestGeneration;
        }

        private void invalidate() {
            generation.incrementAndGet();
        }

        private PreviewRenderRequest snapshot(int requestGeneration) {
            return new PreviewRenderRequest(
                    pictureId,
                    requestGeneration,
                    zoom,
                    degree,
                    alpha,
                    cornerRadiusRatio,
                    cornerRadiusMask,
                    edgeFeatherRatio,
                    edgeFeatherMask,
                    positionX,
                    positionY,
                    touchAndMove,
                    overLayout,
                    previewMode
            );
        }
    }
}
