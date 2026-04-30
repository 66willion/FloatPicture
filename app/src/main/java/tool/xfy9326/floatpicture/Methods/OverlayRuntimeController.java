package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import tool.xfy9326.floatpicture.Services.NotificationService;

public final class OverlayRuntimeController {
    public static final String ACTION_RUNTIME_START = "tool.xfy9326.floatpicture.action.RUNTIME_START";
    public static final String ACTION_RUNTIME_SHUTDOWN = "tool.xfy9326.floatpicture.action.RUNTIME_SHUTDOWN";
    public static final String ACTION_RUNTIME_REFRESH_NOTIFICATION = "tool.xfy9326.floatpicture.action.RUNTIME_REFRESH_NOTIFICATION";
    public static final String ACTION_RUNTIME_RECREATE_WINDOWS = "tool.xfy9326.floatpicture.action.RUNTIME_RECREATE_WINDOWS";
    public static final String ACTION_RUNTIME_SET_WINDOW_VISIBLE = "tool.xfy9326.floatpicture.action.RUNTIME_SET_WINDOW_VISIBLE";
    public static final String ACTION_RUNTIME_SET_ALL_WINDOWS_VISIBLE = "tool.xfy9326.floatpicture.action.RUNTIME_SET_ALL_WINDOWS_VISIBLE";
    public static final String ACTION_RUNTIME_HIDE_ALL_WINDOWS = "tool.xfy9326.floatpicture.action.RUNTIME_HIDE_ALL_WINDOWS";
    public static final String ACTION_RUNTIME_SYNC_PICTURE = "tool.xfy9326.floatpicture.action.RUNTIME_SYNC_PICTURE";
    public static final String ACTION_RUNTIME_SYNC_PICTURES = "tool.xfy9326.floatpicture.action.RUNTIME_SYNC_PICTURES";
    public static final String ACTION_RUNTIME_DELETE_PICTURE = "tool.xfy9326.floatpicture.action.RUNTIME_DELETE_PICTURE";
    public static final String ACTION_RUNTIME_SHOW_RANDOM_WINDOW = "tool.xfy9326.floatpicture.action.RUNTIME_SHOW_RANDOM_WINDOW";
    public static final String ACTION_RUNTIME_UPDATE_PREVIEW = "tool.xfy9326.floatpicture.action.RUNTIME_UPDATE_PREVIEW";
    public static final String ACTION_RUNTIME_CANCEL_PREVIEW = "tool.xfy9326.floatpicture.action.RUNTIME_CANCEL_PREVIEW";
    public static final String ACTION_RUNTIME_FINISH_PREVIEW = "tool.xfy9326.floatpicture.action.RUNTIME_FINISH_PREVIEW";
    public static final String ACTION_RUNTIME_RELEASE_MEMORY = "tool.xfy9326.floatpicture.action.RUNTIME_RELEASE_MEMORY";
    public static final String ACTION_RUNTIME_STATE_CHANGED = "tool.xfy9326.floatpicture.action.RUNTIME_STATE_CHANGED";

    public static final String EXTRA_PICTURE_ID = "extra_picture_id";
    public static final String EXTRA_PICTURE_IDS = "extra_picture_ids";
    public static final String EXTRA_VISIBLE = "extra_visible";
    public static final String EXTRA_TOUCH_AND_MOVE = "extra_touch_and_move";
    public static final String EXTRA_OVER_LAYOUT = "extra_over_layout";
    public static final String EXTRA_ALPHA = "extra_alpha";
    public static final String EXTRA_ZOOM = "extra_zoom";
    public static final String EXTRA_DEGREE = "extra_degree";
    public static final String EXTRA_CORNER_RADIUS_RATIO = "extra_corner_radius_ratio";
    public static final String EXTRA_CORNER_RADIUS_MASK = "extra_corner_radius_mask";
    public static final String EXTRA_EDGE_FEATHER_RATIO = "extra_edge_feather_ratio";
    public static final String EXTRA_EDGE_FEATHER_MASK = "extra_edge_feather_mask";
    public static final String EXTRA_POSITION_X = "extra_position_x";
    public static final String EXTRA_POSITION_Y = "extra_position_y";
    public static final String EXTRA_PREVIEW_MODE = "extra_preview_mode";
    public static final String EXTRA_RELOAD_SOURCE = "extra_reload_source";
    public static final String EXTRA_CREATE_IF_VISIBLE = "extra_create_if_visible";
    public static final String EXTRA_INITIALIZE_RUNTIME = "extra_initialize_runtime";
    public static final String EXTRA_RESULT_RECEIVER = "extra_result_receiver";
    public static final String EXTRA_RELEASED_WINDOW_COUNT = "extra_released_window_count";
    public static final String EXTRA_STARTED_AS_FOREGROUND_SERVICE = "extra_started_as_foreground_service";

    public static final int PREVIEW_MODE_FULL = 0;
    public static final int PREVIEW_MODE_MOVE_ONLY = 1;
    public static final int PREVIEW_MODE_OUTLINE = 2;
    public static final int PREVIEW_MODE_LOW_RES = 3;

    public static final int RANDOM_WINDOW_RESULT_SUCCESS = 1;
    public static final int RANDOM_WINDOW_RESULT_NO_CANDIDATE = 2;
    public static final int RANDOM_WINDOW_RESULT_ERROR = 3;

    private static final long RUNTIME_RESULT_TIMEOUT_MS = 4000L;

    public interface RandomWindowResultCallback {
        void onComplete(int result);
    }

    public interface ReleaseMemoryResultCallback {
        void onComplete(int releasedWindowCount);
    }

    private OverlayRuntimeController() {
    }

    public static void startRuntime(@NonNull Context context) {
        dispatchForeground(getAppContext(context), createIntent(context, ACTION_RUNTIME_START));
    }

    public static void shutdownRuntime(@NonNull Context context) {
        dispatchCommand(getAppContext(context), createIntent(context, ACTION_RUNTIME_SHUTDOWN));
    }

    public static void refreshNotification(@NonNull Context context) {
        refreshNotification(context, true);
    }

    public static void refreshNotification(@NonNull Context context, boolean initializeRuntime) {
        Intent intent = createIntent(context, ACTION_RUNTIME_REFRESH_NOTIFICATION);
        intent.putExtra(EXTRA_INITIALIZE_RUNTIME, initializeRuntime);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void recreateVisibleWindows(@NonNull Context context) {
        dispatchCommand(getAppContext(context), createIntent(context, ACTION_RUNTIME_RECREATE_WINDOWS));
    }

    public static void syncPicture(@NonNull Context context, @Nullable String pictureId) {
        syncPicture(context, pictureId, true);
    }

    public static void syncPicture(@NonNull Context context, @Nullable String pictureId, boolean createIfVisible) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_SYNC_PICTURE);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        intent.putExtra(EXTRA_CREATE_IF_VISIBLE, createIfVisible);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void syncPictures(@NonNull Context context,
                                    @Nullable Collection<String> pictureIds,
                                    boolean createIfVisible) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }
        ArrayList<String> pictureIdSnapshot = new ArrayList<>(pictureIds.size());
        for (String pictureId : pictureIds) {
            if (pictureId == null || pictureId.isEmpty() || pictureIdSnapshot.contains(pictureId)) {
                continue;
            }
            pictureIdSnapshot.add(pictureId);
        }
        if (pictureIdSnapshot.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_SYNC_PICTURES);
        intent.putStringArrayListExtra(EXTRA_PICTURE_IDS, pictureIdSnapshot);
        intent.putExtra(EXTRA_CREATE_IF_VISIBLE, createIfVisible);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void deletePicture(@NonNull Context context, @Nullable String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_DELETE_PICTURE);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void setWindowVisible(@NonNull Context context, @Nullable String pictureId, boolean visible) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_SET_WINDOW_VISIBLE);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        intent.putExtra(EXTRA_VISIBLE, visible);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void setAllWindowsVisible(@NonNull Context context, boolean visible) {
        Intent intent = createIntent(context, ACTION_RUNTIME_SET_ALL_WINDOWS_VISIBLE);
        intent.putExtra(EXTRA_VISIBLE, visible);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void hideAllWindows(@NonNull Context context) {
        dispatchCommand(getAppContext(context), createIntent(context, ACTION_RUNTIME_HIDE_ALL_WINDOWS));
    }

    public static void showRandomWindow(@NonNull Context context, @NonNull RandomWindowResultCallback callback) {
        Context appContext = getAppContext(context);
        Handler resultHandler = new Handler(Looper.getMainLooper());
        AtomicBoolean resultDelivered = new AtomicBoolean(false);
        Runnable timeoutRunnable = () -> completeRandomWindowResult(
                resultHandler,
                resultDelivered,
                callback,
                RANDOM_WINDOW_RESULT_ERROR
        );
        ResultReceiver receiver = new ResultReceiver(resultHandler) {
            @Override
            protected void onReceiveResult(int code, Bundle resultData) {
                resultHandler.removeCallbacks(timeoutRunnable);
                completeRandomWindowResult(resultHandler, resultDelivered, callback, code);
            }
        };
        Intent intent = createIntent(appContext, ACTION_RUNTIME_SHOW_RANDOM_WINDOW);
        intent.putExtra(EXTRA_RESULT_RECEIVER, receiver);
        resultHandler.postDelayed(timeoutRunnable, RUNTIME_RESULT_TIMEOUT_MS);
        try {
            dispatchCommand(appContext, intent);
        } catch (IllegalStateException e) {
            resultHandler.removeCallbacks(timeoutRunnable);
            completeRandomWindowResult(resultHandler, resultDelivered, callback, RANDOM_WINDOW_RESULT_ERROR);
        }
    }

    public static void updatePreview(@NonNull Context context,
                                     @Nullable String pictureId,
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
                                     int previewMode,
                                     boolean reloadSource) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_UPDATE_PREVIEW);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        intent.putExtra(EXTRA_ZOOM, zoom);
        intent.putExtra(EXTRA_DEGREE, degree);
        intent.putExtra(EXTRA_ALPHA, alpha);
        intent.putExtra(EXTRA_CORNER_RADIUS_RATIO, cornerRadiusRatio);
        intent.putExtra(EXTRA_CORNER_RADIUS_MASK, cornerRadiusMask);
        intent.putExtra(EXTRA_EDGE_FEATHER_RATIO, edgeFeatherRatio);
        intent.putExtra(EXTRA_EDGE_FEATHER_MASK, edgeFeatherMask);
        intent.putExtra(EXTRA_POSITION_X, positionX);
        intent.putExtra(EXTRA_POSITION_Y, positionY);
        intent.putExtra(EXTRA_TOUCH_AND_MOVE, touchAndMove);
        intent.putExtra(EXTRA_OVER_LAYOUT, overLayout);
        intent.putExtra(EXTRA_PREVIEW_MODE, previewMode);
        intent.putExtra(EXTRA_RELOAD_SOURCE, reloadSource);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void cancelPreview(@NonNull Context context, @Nullable String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_CANCEL_PREVIEW);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void finishPreview(@NonNull Context context, @Nullable String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        Intent intent = createIntent(context, ACTION_RUNTIME_FINISH_PREVIEW);
        intent.putExtra(EXTRA_PICTURE_ID, pictureId);
        dispatchCommand(getAppContext(context), intent);
    }

    public static void releaseMemory(@NonNull Context context, @NonNull ReleaseMemoryResultCallback callback) {
        Context appContext = getAppContext(context);
        Handler resultHandler = new Handler(Looper.getMainLooper());
        AtomicBoolean resultDelivered = new AtomicBoolean(false);
        Runnable timeoutRunnable = () -> completeReleaseMemoryResult(
                resultHandler,
                resultDelivered,
                callback,
                0
        );
        ResultReceiver receiver = new ResultReceiver(resultHandler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                int releasedWindowCount = resultData != null ? resultData.getInt(EXTRA_RELEASED_WINDOW_COUNT, 0) : 0;
                resultHandler.removeCallbacks(timeoutRunnable);
                completeReleaseMemoryResult(resultHandler, resultDelivered, callback, releasedWindowCount);
            }
        };
        Intent intent = createIntent(appContext, ACTION_RUNTIME_RELEASE_MEMORY);
        intent.putExtra(EXTRA_RESULT_RECEIVER, receiver);
        resultHandler.postDelayed(timeoutRunnable, RUNTIME_RESULT_TIMEOUT_MS);
        try {
            dispatchCommand(appContext, intent);
        } catch (IllegalStateException e) {
            resultHandler.removeCallbacks(timeoutRunnable);
            completeReleaseMemoryResult(resultHandler, resultDelivered, callback, 0);
        }
    }

    public static void notifyRuntimeStateChanged(@NonNull Context context) {
        Context appContext = getAppContext(context);
        Intent intent = new Intent(ACTION_RUNTIME_STATE_CHANGED);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String action) {
        Context appContext = getAppContext(context);
        return new Intent(appContext, NotificationService.class).setAction(action);
    }

    @Nullable
    public static ResultReceiver getResultReceiver(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        }
        @SuppressWarnings("deprecation")
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        return receiver;
    }

    @NonNull
    private static Context getAppContext(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }

    private static void completeRandomWindowResult(@NonNull Handler resultHandler,
                                                   @NonNull AtomicBoolean resultDelivered,
                                                   @NonNull RandomWindowResultCallback callback,
                                                   int resultCode) {
        if (!resultDelivered.compareAndSet(false, true)) {
            return;
        }
        if (Looper.myLooper() == resultHandler.getLooper()) {
            callback.onComplete(resultCode);
        } else {
            resultHandler.post(() -> callback.onComplete(resultCode));
        }
    }

    private static void completeReleaseMemoryResult(@NonNull Handler resultHandler,
                                                    @NonNull AtomicBoolean resultDelivered,
                                                    @NonNull ReleaseMemoryResultCallback callback,
                                                    int releasedWindowCount) {
        if (!resultDelivered.compareAndSet(false, true)) {
            return;
        }
        if (Looper.myLooper() == resultHandler.getLooper()) {
            callback.onComplete(releasedWindowCount);
        } else {
            resultHandler.post(() -> callback.onComplete(releasedWindowCount));
        }
    }

    private static void dispatchForeground(@NonNull Context context, @NonNull Intent intent) {
        ContextCompat.startForegroundService(context, intent);
    }

    private static void dispatchCommand(@NonNull Context context, @NonNull Intent intent) {
        try {
            context.startService(intent);
        } catch (IllegalStateException e) {
            intent.putExtra(EXTRA_STARTED_AS_FOREGROUND_SERVICE, true);
            ContextCompat.startForegroundService(context, intent);
        }
    }
}
