package tool.xfy9326.floatpicture.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Objects;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ThemeMethods;
import tool.xfy9326.floatpicture.Services.TrustedOverlayAccessibilityService;
import tool.xfy9326.floatpicture.Utils.Config;

public class GlobalSettingsFragment extends PreferenceFragmentCompat {
    private LayoutInflater inflater;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflater = LayoutInflater.from(requireActivity());
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_global_settings);
        PreferenceSet();
        updateTrustedOverlaySummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTrustedOverlaySummary();
    }

    @NonNull
    private Preference requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    @NonNull
    private SwitchPreferenceCompat requireSwitchPreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void PreferenceSet() {
        requirePreference(Config.PREFERENCE_THEME_MODE).setOnPreferenceChangeListener((preference, newValue) -> {
            ThemeMethods.applyThemeMode(String.valueOf(newValue));
            return true;
        });
        requirePreference(Config.PREFERENCE_NEW_PICTURE_QUALITY).setOnPreferenceClickListener(preference -> {
            PictureQualitySet();
            return true;
        });
        requireSwitchPreference(Config.PREFERENCE_TRUSTED_OVERLAY_ACCESSIBILITY).setOnPreferenceChangeListener((preference, newValue) -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return false;
        });
        requirePreference(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL).setOnPreferenceChangeListener((preference, newValue) -> {
            ApplicationMethods.showToast(requireContext(), R.string.restart_to_apply_changes);
            return true;
        });
    }

    private void updateTrustedOverlaySummary() {
        SwitchPreferenceCompat preference = findPreference(Config.PREFERENCE_TRUSTED_OVERLAY_ACCESSIBILITY);
        if (preference == null) {
            return;
        }
        boolean authorized = TrustedOverlayAccessibilityService.isAuthorized(requireContext());
        boolean active = TrustedOverlayAccessibilityService.isActive(requireContext());
        preference.setChecked(authorized);
        if (!authorized) {
            preference.setSummary(R.string.settings_global_trusted_overlay_accessibility_sum);
            return;
        }
        preference.setSummary(active
                ? R.string.settings_global_trusted_overlay_accessibility_sum_enabled
                : R.string.settings_global_trusted_overlay_accessibility_sum_authorized);
    }

    private void PictureQualitySet() {
        int picture_size = sharedPreferences.getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireActivity());
        dialog.setTitle(R.string.settings_global_picture_quality);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_global_picture_quality_quality);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setProgress(picture_size);
        seekBar.setMax(100);
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        editText.setText(String.valueOf(picture_size));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                editText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            int edittext_temp = Integer.parseInt(v.getText().toString());
            if (edittext_temp > 0) {
                seekBar.setProgress(edittext_temp);
            } else {
                ApplicationMethods.showToast(requireContext(), R.string.settings_global_picture_quality_warn);
            }
            return false;
        });
        dialog.setView(mView);
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            int quality = seekBar.getProgress();
            if (quality > 0) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, quality);
                editor.apply();
            } else {
                ApplicationMethods.showToast(requireContext(), R.string.settings_global_picture_quality_warn);
            }
        });
        dialog.setNegativeButton(R.string.cancel, null);
        dialog.show();
    }
}
