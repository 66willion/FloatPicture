package tool.xfy9326.floatpicture.Activities;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.View.GlobalSettingsFragment;

final class MainDrawerController {
    private static final int PAGE_MENU = 0;
    private static final int PAGE_GLOBAL_SETTINGS = 1;
    private static final int PAGE_ABOUT = 2;
    private static final String GLOBAL_SETTINGS_FRAGMENT_TAG = "drawer_global_settings";

    private final MainActivity activity;
    private final DrawerLayout drawerLayout;
    private final Runnable hideAllWindowsAction;
    private final Runnable releaseMemoryAction;
    private final Runnable enterBatchEditModeAction;
    private final Runnable launchBatchImportPickerAction;
    @Nullable
    private View menuPage;
    @Nullable
    private View globalSettingsPage;
    @Nullable
    private View aboutPage;
    private int currentPage = PAGE_MENU;

    MainDrawerController(@NonNull MainActivity activity,
                         @NonNull DrawerLayout drawerLayout,
                         @NonNull Runnable hideAllWindowsAction,
                         @NonNull Runnable releaseMemoryAction,
                         @NonNull Runnable enterBatchEditModeAction,
                         @NonNull Runnable launchBatchImportPickerAction) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.hideAllWindowsAction = hideAllWindowsAction;
        this.releaseMemoryAction = releaseMemoryAction;
        this.enterBatchEditModeAction = enterBatchEditModeAction;
        this.launchBatchImportPickerAction = launchBatchImportPickerAction;
    }

    void setup() {
        setupPages();
        bindActions();
    }

    boolean isMenuPage() {
        return currentPage == PAGE_MENU;
    }

    void showMenuPage() {
        showPage(PAGE_MENU);
    }

    private void bindActions() {
        bindPageAction(R.id.main_drawer_global_settings, () -> showPage(PAGE_GLOBAL_SETTINGS));
        bindAction(R.id.main_drawer_close_all_windows, hideAllWindowsAction);
        bindAction(R.id.main_drawer_release_memory, releaseMemoryAction);
        bindAction(R.id.main_drawer_batch_edit, enterBatchEditModeAction);
        bindAction(R.id.main_drawer_batch_import, launchBatchImportPickerAction);
        bindPageAction(R.id.main_drawer_about, () -> showPage(PAGE_ABOUT));
        bindAction(R.id.main_drawer_back_to_launcher, () -> activity.moveTaskToBack(true));
        bindAction(R.id.main_drawer_exit, () -> ApplicationMethods.CloseApplication(activity));
        bindPageAction(R.id.main_drawer_global_back, () -> showPage(PAGE_MENU));
        bindPageAction(R.id.main_drawer_about_back, () -> showPage(PAGE_MENU));
        bindAction(
                R.id.main_drawer_about_open_source,
                () -> activity.startActivity(new Intent(activity, LicenseActivity.class))
        );
    }

    private void bindAction(int viewId, @NonNull Runnable action) {
        View actionView = activity.findViewById(viewId);
        if (actionView == null) {
            return;
        }
        actionView.setOnClickListener(view -> {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
            drawerLayout.post(action);
        });
    }

    private void bindPageAction(int viewId, @NonNull Runnable action) {
        View actionView = activity.findViewById(viewId);
        if (actionView == null) {
            return;
        }
        actionView.setOnClickListener(view -> action.run());
    }

    private void setupPages() {
        menuPage = activity.findViewById(R.id.main_drawer_menu_page);
        globalSettingsPage = activity.findViewById(R.id.main_drawer_global_page);
        aboutPage = activity.findViewById(R.id.main_drawer_about_page);
        TextView aboutVersion = activity.findViewById(R.id.main_drawer_about_version);
        if (aboutVersion != null) {
            aboutVersion.setText(activity.getString(R.string.application_version) + ApplicationMethods.getApplicationVersion(activity));
        }
        showPage(PAGE_MENU, false);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                showPage(PAGE_MENU, false);
            }
        });
    }

    private void showPage(int page) {
        showPage(page, true);
    }

    private void showPage(int page, boolean animate) {
        if (page == currentPage && menuPage != null && menuPage.getVisibility() == View.VISIBLE) {
            return;
        }
        if (page == PAGE_GLOBAL_SETTINGS) {
            ensureGlobalSettingsFragment();
        }
        View targetPage = getPageView(page);
        View previousPage = getPageView(currentPage);
        currentPage = page;
        if (targetPage == null) {
            return;
        }
        if (previousPage != null && previousPage != targetPage) {
            hidePage(previousPage, animate);
        }
        showPageView(targetPage, animate);
    }

    @Nullable
    private View getPageView(int page) {
        if (page == PAGE_GLOBAL_SETTINGS) {
            return globalSettingsPage;
        }
        if (page == PAGE_ABOUT) {
            return aboutPage;
        }
        return menuPage;
    }

    private void showPageView(@NonNull View pageView, boolean animate) {
        pageView.setVisibility(View.VISIBLE);
        pageView.bringToFront();
        if (!animate) {
            pageView.setAlpha(1f);
            pageView.setTranslationX(0f);
            return;
        }
        float offset = activity.getResources().getDisplayMetrics().density * 16f;
        pageView.setAlpha(0f);
        pageView.setTranslationX(offset);
        pageView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(160L)
                .start();
    }

    private void hidePage(@NonNull View pageView, boolean animate) {
        if (!animate) {
            pageView.setVisibility(View.GONE);
            pageView.setAlpha(1f);
            pageView.setTranslationX(0f);
            return;
        }
        float offset = -activity.getResources().getDisplayMetrics().density * 10f;
        pageView.animate()
                .alpha(0f)
                .translationX(offset)
                .setDuration(120L)
                .withEndAction(() -> {
                    pageView.setVisibility(View.GONE);
                    pageView.setAlpha(1f);
                    pageView.setTranslationX(0f);
                })
                .start();
    }

    private void ensureGlobalSettingsFragment() {
        if (activity.getSupportFragmentManager().findFragmentByTag(GLOBAL_SETTINGS_FRAGMENT_TAG) != null) {
            return;
        }
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.main_drawer_global_settings_container,
                        new GlobalSettingsFragment(),
                        GLOBAL_SETTINGS_FRAGMENT_TAG
                )
                .commit();
    }
}
