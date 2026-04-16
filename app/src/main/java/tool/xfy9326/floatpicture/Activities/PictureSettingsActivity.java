package tool.xfy9326.floatpicture.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;

public class PictureSettingsActivity extends AppCompatActivity {
    private PictureSettingsFragment mPictureSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ViewSet();
        fragmentSet(savedInstanceState);
        initBackPressedCallback();
    }

    private void ViewSet() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        Intent intent = getIntent();
        if (actionBar != null && intent != null) {
            if (!intent.getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false)) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    private void fragmentSet(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            mPictureSettingsFragment = new PictureSettingsFragment();
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.layout_picture_settings_content, mPictureSettingsFragment);
            fragmentTransaction.commit();
        } else {
            mPictureSettingsFragment = (PictureSettingsFragment) getSupportFragmentManager().findFragmentById(R.id.layout_picture_settings_content);
        }
    }

    private void initBackPressedCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mPictureSettingsFragment != null) {
                    mPictureSettingsFragment.exit();
                }
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_picture_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_picture_settings_save) {
            // 禁用保存按钮，防止用户在后台 IO 期间重复点击
            item.setEnabled(false);
            mPictureSettingsFragment.saveAllData(() -> {
                setSuccessResult();
                finish();
            });
        } else if (itemId == android.R.id.home) {
            mPictureSettingsFragment.exit();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void setSuccessResult() {
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false)) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(Config.INTENT_PICTURE_EDIT_POSITION, intent.getIntExtra(Config.INTENT_PICTURE_EDIT_POSITION, -1));
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_OK);
        }
    }

    @Override
    protected void onDestroy() {
        if (mPictureSettingsFragment != null) {
            mPictureSettingsFragment.clearEditView();
        }
        super.onDestroy();
    }
}
