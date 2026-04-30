package tool.xfy9326.floatpicture.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.provider.Settings;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
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
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.ThemeMethods;
import tool.xfy9326.floatpicture.Services.PureOverlayQuickToggleController;
import tool.xfy9326.floatpicture.Services.TrustedOverlayAccessibilityService;
import tool.xfy9326.floatpicture.Utils.Config;

public class GlobalSettingsFragment extends PreferenceFragmentCompat {
    private LayoutInflater inflater;
    private SharedPreferences sharedPreferences;
    private PureOverlayQuickToggleController pureOverlayQuickToggleController;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflater = LayoutInflater.from(requireActivity());
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        pureOverlayQuickToggleController = new PureOverlayQuickToggleController(requireContext());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_global_settings);
        PreferenceSet();
        updatePureOverlayQuickToggleSummary();
        updateTrustedOverlaySummary();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        recyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        recyclerView.setClipToPadding(false);
        int horizontalPadding = Math.round(8 * getResources().getDisplayMetrics().density);
        int verticalPadding = Math.round(12 * getResources().getDisplayMetrics().density);
        recyclerView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        recyclerView.setItemAnimator(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePureOverlayQuickToggleSummary();
        updateTrustedOverlaySummary();
    }

    @Override
    public void onDestroy() {
        if (pureOverlayQuickToggleController != null) {
            pureOverlayQuickToggleController.hidePreview();
            pureOverlayQuickToggleController = null;
        }
        super.onDestroy();
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
        requirePreference(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE).setOnPreferenceClickListener(preference -> {
            showPureOverlayQuickToggleDialog();
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

    private void updatePureOverlayQuickToggleSummary() {
        Preference preference = findPreference(Config.PREFERENCE_PURE_OVERLAY_QUICK_TOGGLE);
        if (preference == null) {
            return;
        }
        preference.setSummary(PureOverlayQuickToggleController.buildSummary(requireContext()));
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
            Integer editTextValue = parsePictureQuality(editText);
            if (editTextValue == null) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_global_picture_quality_warn);
                return false;
            }
            seekBar.setProgress(editTextValue);
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

    private void showPureOverlayQuickToggleDialog() {
        Point maxPosition = PureOverlayQuickToggleController.getPositionBounds(requireContext());
        Point currentPosition = PureOverlayQuickToggleController.resolveSavedPosition(requireContext());
        boolean enabled = PureOverlayQuickToggleController.isEnabled(requireContext());
        PureOverlayQuickToggleController previewController = pureOverlayQuickToggleController;
        View dialogView = inflater.inflate(
                R.layout.dialog_set_pure_overlay_quick_toggle,
                requireActivity().findViewById(R.id.layout_dialog_set_pure_overlay_quick_toggle)
        );
        final SeekBar seekBarX = dialogView.findViewById(R.id.seekbar_set_pure_overlay_quick_toggle_x);
        seekBarX.setMax(maxPosition.x);
        seekBarX.setProgress(currentPosition.x);
        final EditText editTextX = dialogView.findViewById(R.id.edittext_set_pure_overlay_quick_toggle_x);
        editTextX.setText(String.valueOf(currentPosition.x));
        final SeekBar seekBarY = dialogView.findViewById(R.id.seekbar_set_pure_overlay_quick_toggle_y);
        seekBarY.setMax(maxPosition.y);
        seekBarY.setProgress(currentPosition.y);
        final EditText editTextY = dialogView.findViewById(R.id.edittext_set_pure_overlay_quick_toggle_y);
        editTextY.setText(String.valueOf(currentPosition.y));
        final CheckBox showCheckBox = dialogView.findViewById(R.id.checkbox_show_pure_overlay_quick_toggle);
        showCheckBox.setChecked(enabled);
        final int[] positionXTemp = {currentPosition.x};
        final int[] positionYTemp = {currentPosition.y};
        if (previewController != null) {
            previewController.showPreview(positionXTemp[0], positionYTemp[0]);
        }
        seekBarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                positionXTemp[0] = progress;
                editTextX.setText(String.valueOf(progress));
                if (previewController != null) {
                    previewController.showPreview(positionXTemp[0], positionYTemp[0]);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        seekBarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                positionYTemp[0] = progress;
                editTextY.setText(String.valueOf(progress));
                if (previewController != null) {
                    previewController.showPreview(positionXTemp[0], positionYTemp[0]);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editTextX.setOnEditorActionListener((v, actionId, event) -> {
            Integer parsedValue = parseQuickTogglePosition(editTextX, maxPosition.x);
            if (parsedValue == null) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_picture_position_warn);
                return false;
            }
            positionXTemp[0] = parsedValue;
            editTextX.setText(String.valueOf(parsedValue));
            seekBarX.setProgress(parsedValue);
            if (previewController != null) {
                previewController.showPreview(positionXTemp[0], positionYTemp[0]);
            }
            return false;
        });
        editTextY.setOnEditorActionListener((v, actionId, event) -> {
            Integer parsedValue = parseQuickTogglePosition(editTextY, maxPosition.y);
            if (parsedValue == null) {
                ApplicationMethods.showToast(requireContext(), R.string.settings_picture_position_warn);
                return false;
            }
            positionYTemp[0] = parsedValue;
            editTextY.setText(String.valueOf(parsedValue));
            seekBarY.setProgress(parsedValue);
            if (previewController != null) {
                previewController.showPreview(positionXTemp[0], positionYTemp[0]);
            }
            return false;
        });
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireActivity());
        dialog.setTitle(R.string.settings_global_pure_overlay_quick_toggle);
        dialog.setView(dialogView);
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Integer parsedX = parseQuickTogglePosition(editTextX, maxPosition.x);
            Integer parsedY = parseQuickTogglePosition(editTextY, maxPosition.y);
            int savedX = parsedX != null ? parsedX : positionXTemp[0];
            int savedY = parsedY != null ? parsedY : positionYTemp[0];
            PureOverlayQuickToggleController.saveSettings(requireContext(), showCheckBox.isChecked(), savedX, savedY);
            updatePureOverlayQuickToggleSummary();
            if (previewController != null) {
                previewController.hidePreview();
            }
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> {
            if (previewController != null) {
                previewController.hidePreview();
            }
        });
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(dialogInterface -> {
            if (previewController != null) {
                previewController.hidePreview();
            }
        });
    }

    @Nullable
    private Integer parseQuickTogglePosition(@NonNull EditText editText, int maxValue) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            if (value < 0 || value > maxValue) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Integer parsePictureQuality(@NonNull EditText editText) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            if (value <= 0 || value > 100) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
