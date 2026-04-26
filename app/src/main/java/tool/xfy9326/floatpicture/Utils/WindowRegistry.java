package tool.xfy9326.floatpicture.Utils;

import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.View.FloatImageView;

public final class WindowRegistry {
    private final int maxSize;
    private final LinkedHashMap<String, View> views;

    public WindowRegistry(int maxSize) {
        this.maxSize = maxSize;
        this.views = new LinkedHashMap<>(maxSize, 0.75f, true);
    }

    public synchronized void register(String id, View view) {
        if (id == null || id.isEmpty() || view == null) {
            return;
        }
        views.put(id, view);
        trimDetachedOverflow();
    }

    public synchronized LinkedHashMap<String, View> snapshot() {
        return new LinkedHashMap<>(views);
    }

    public synchronized int size() {
        return views.size();
    }

    public synchronized View get(String id) {
        return id == null ? null : views.get(id);
    }

    public synchronized boolean unregister(String id) {
        if (id == null || !views.containsKey(id)) {
            return false;
        }
        View removedView = views.remove(id);
        if (removedView instanceof FloatImageView floatImageView) {
            floatImageView.refreshDrawableState();
        }
        return true;
    }

    public synchronized boolean unregisterIfSame(String id, View expectedView) {
        if (id == null || expectedView == null || views.get(id) != expectedView) {
            return false;
        }
        return unregister(id);
    }

    private void trimDetachedOverflow() {
        while (views.size() > maxSize) {
            String removableId = null;
            View removableView = null;
            for (Map.Entry<String, View> entry : views.entrySet()) {
                View candidate = entry.getValue();
                if (!(candidate instanceof FloatImageView floatImageView) || !floatImageView.isAttachedToWindow()) {
                    removableId = entry.getKey();
                    removableView = candidate;
                    break;
                }
            }
            if (removableId == null) {
                return;
            }
            views.remove(removableId);
            if (removableView instanceof FloatImageView floatImageView) {
                ImageMethods.releasePictureView(floatImageView);
            }
        }
    }
}
