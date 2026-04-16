package tool.xfy9326.floatpicture.Methods;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Services.NotificationService;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ApplicationMethods {
    private static volatile boolean waitDoubleClick;

    public static final class MemoryReleaseResult {
        private final int releasedWindowCount;
        private final int deletedTempFileCount;

        public MemoryReleaseResult(int releasedWindowCount, int deletedTempFileCount) {
            this.releasedWindowCount = releasedWindowCount;
            this.deletedTempFileCount = deletedTempFileCount;
        }

        public int getReleasedWindowCount() {
            return releasedWindowCount;
        }

        public int getDeletedTempFileCount() {
            return deletedTempFileCount;
        }
    }

    public static void startNotificationControl(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true)) {
            NotificationService.start(context);
        }
    }

    private static void closeNotificationControl(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL, true)) {
            context.stopService(NotificationService.createIntent(context, Config.INTENT_ACTION_NOTIFICATION_START));
        }
    }

    public static String getApplicationVersion(Context mContext) {
        try {
            PackageInfo packageInfo = getPackageInfoCompat(mContext);
            return packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static PackageInfo getPackageInfoCompat(Context context) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return getLegacyPackageInfo(packageManager, packageName);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo getLegacyPackageInfo(PackageManager packageManager, String packageName)
            throws PackageManager.NameNotFoundException {
        return packageManager.getPackageInfo(packageName, 0);
    }

    public static void CloseApplication(Activity mActivity) {
        ManageMethods.CloseAllWindows(mActivity);
        closeNotificationControl(mActivity);
        mActivity.finish();
    }

    public static void disableNavigationViewScrollbars(NavigationView navigationView) {
        if (navigationView != null) {
            navigationView.setVerticalScrollBarEnabled(false);
        }
    }

    public static void DoubleClickCloseSnackBar(final Activity mActivity, boolean isDoubleClick) {
        if (isDoubleClick && waitDoubleClick) {
            CloseApplication(mActivity);
        } else {
            CoordinatorLayout coordinatorLayout = mActivity.findViewById(R.id.main_layout_content);
            Snackbar snackbar = Snackbar.make(coordinatorLayout, R.string.action_warn_double_click_close_application, Snackbar.LENGTH_SHORT);
            snackbar.setAction(R.string.action_back_to_launcher, v -> mActivity.moveTaskToBack(true));
            snackbar.setActionTextColor(ContextCompat.getColor(mActivity, R.color.colorPrimary));
            snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<>() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    waitDoubleClick = false;
                    super.onDismissed(transientBottomBar, event);
                }
            });
            waitDoubleClick = true;
            snackbar.show();
        }
    }

    public static void ClearUselessTemp(final Context mContext) {
        new Thread(() -> clearUselessTempSync(mContext)).start();
    }

    public static MemoryReleaseResult releaseMemory(Context context) {
        int releasedWindowCount = ManageMethods.releaseInactiveWindowMemory(context);
        int deletedTempFileCount = clearUselessTempSync(context);
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        System.runFinalization();
        runtime.gc();
        return new MemoryReleaseResult(releasedWindowCount, deletedTempFileCount);
    }

    private static int clearUselessTempSync(Context mContext) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictureList = pictureData.getListArray();
        HashSet<String> validIds = new HashSet<>();
        if (pictureList != null) {
            validIds.addAll(pictureList.keySet());
        }
        int deletedCount = 0;
        deletedCount += clearOrphanFiles(new File(Config.getOriginalPictureDir()), validIds);
        deletedCount += clearOrphanFiles(new File(Config.getPictureDir()), validIds);
        deletedCount += clearOrphanFiles(new File(Config.getPictureTempDir()), validIds);
        return deletedCount;
    }

    private static int clearOrphanFiles(File directory, Set<String> validIds) {
        if (!directory.exists()) {
            return 0;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        int deletedCount = 0;
        for (File file : files) {
            // 目录只做保护性跳过，不递归删除
            if (file.isDirectory()) {
                continue;
            }
            if (!validIds.contains(file.getName())) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }
}
