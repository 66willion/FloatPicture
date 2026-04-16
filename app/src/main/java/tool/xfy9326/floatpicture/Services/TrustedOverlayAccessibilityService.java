package tool.xfy9326.floatpicture.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;

import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.Utils.OverlayRuntimeStateStore;

public class TrustedOverlayAccessibilityService extends AccessibilityService {
    private static volatile TrustedOverlayAccessibilityService instance;

    public static boolean isEnabled(Context context) {
        return isAuthorized(context);
    }

    public static boolean isAuthorized(Context context) {
        Context appContext = context.getApplicationContext();
        ComponentName componentName = new ComponentName(appContext, TrustedOverlayAccessibilityService.class);
        AccessibilityManager accessibilityManager = (AccessibilityManager) appContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager != null) {
            for (AccessibilityServiceInfo serviceInfo : accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                String serviceId = serviceInfo.getId();
                if (componentName.flattenToString().equals(serviceId) || componentName.flattenToShortString().equals(serviceId)) {
                    return true;
                }
            }
        }

        String enabledServices = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            String enabledService = splitter.next();
            if (componentName.flattenToString().equals(enabledService) || componentName.flattenToShortString().equals(enabledService)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive(Context context) {
        return instance != null || OverlayRuntimeStateStore.isTrustedOverlayActive(context);
    }

    public static Context getWindowContext(Context fallbackContext) {
        TrustedOverlayAccessibilityService service = instance;
        return service != null ? service : fallbackContext;
    }

    @Nullable
    public static TrustedOverlayAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        OverlayRuntimeStateStore.setTrustedOverlayActive(getApplicationContext(), true);
        OverlayRuntimeController.notifyRuntimeStateChanged(getApplicationContext());
        OverlayRuntimeController.recreateVisibleWindows(getApplicationContext());
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        boolean wasActive = instance == this;
        if (wasActive) {
            instance = null;
            OverlayRuntimeStateStore.setTrustedOverlayActive(getApplicationContext(), false);
            OverlayRuntimeController.notifyRuntimeStateChanged(getApplicationContext());
            OverlayRuntimeController.recreateVisibleWindows(getApplicationContext());
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        boolean wasActive = instance == this;
        if (wasActive) {
            instance = null;
            OverlayRuntimeStateStore.setTrustedOverlayActive(getApplicationContext(), false);
            OverlayRuntimeController.notifyRuntimeStateChanged(getApplicationContext());
            OverlayRuntimeController.recreateVisibleWindows(getApplicationContext());
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
