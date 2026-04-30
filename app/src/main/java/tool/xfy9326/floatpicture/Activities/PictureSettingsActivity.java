package tool.xfy9326.floatpicture.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
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
        View saveButton = findViewById(R.id.picture_settings_button_save);
        if (saveButton != null) {
            saveButton.bringToFront();
            saveButton.setTranslationZ(18f);
            saveButton.setOnClickListener(view -> savePictureSettings(view));
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

    private void savePictureSettings(View saveButton) {
        if (mPictureSettingsFragment == null || saveButton == null || !saveButton.isEnabled()) {
            return;
        }
        // 禁用保存按钮，防止用户在后台 IO 期间重复点击
        saveButton.setEnabled(false);
        mPictureSettingsFragment.saveAllData(
                () -> {
                    setSuccessResult();
                    finish();
                },
                () -> saveButton.setEnabled(true)
        );
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
        if (mPictureSettingsFragment != null && isFinishing() && !isChangingConfigurations()) {
            mPictureSettingsFragment.clearEditView();
        }
        super.onDestroy();
    }
}
