package tool.xfy9326.floatpicture.View;

import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.formatThreeDecimal;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.parsePercentProgress;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.parseThreeDecimalFloat;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.percentProgressToRatio;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.roundToThreeDecimals;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.toRatioPercentProgress;
import static tool.xfy9326.floatpicture.View.PictureSettingsValueFormatter.toThreeDecimalProgress;

import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.OverlayRuntimeController;
import tool.xfy9326.floatpicture.R;

final class PictureSettingsDialogController {
    private static final int THREE_DECIMAL_SCALE = 1000;
    private static final long BITMAP_PREVIEW_THROTTLE_MS = 120L;
    private static final long LIGHT_PREVIEW_THROTTLE_MS = 32L;

    private final PreferenceFragmentCompat fragment;
    private final LayoutInflater inflater;
    private final Callbacks callbacks;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    PictureSettingsDialogController(@NonNull PreferenceFragmentCompat fragment,
                                    @NonNull LayoutInflater inflater,
                                    @NonNull Callbacks callbacks) {
        this.fragment = fragment;
        this.inflater = inflater;
        this.callbacks = callbacks;
    }

    void showSizeDialog(@NonNull SizeDialogRequest request, @NonNull FloatResultListener listener) {
        View view = inflateDialogView(R.layout.dialog_set_size, R.id.layout_dialog_set_size);
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.requireContext());
        dialog.setTitle(R.string.settings_picture_resize);
        dialog.setCancelable(false);
        TextView name = view.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_picture_resize_size);
        final SeekBar seekBar = view.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(Math.max(1, toThreeDecimalProgress(request.maxSize)));
        seekBar.setProgress(Math.max(1, Math.min(seekBar.getMax(), toThreeDecimalProgress(request.preview.zoom))));
        final EditText editText = view.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(request.preview.zoom));
        final float[] zoomTemp = {roundToThreeDecimals(request.preview.zoom)};
        final PreviewUpdateThrottler previewThrottler = new PreviewUpdateThrottler(BITMAP_PREVIEW_THROTTLE_MS);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > 0) {
                    zoomTemp[0] = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                    editText.setText(formatThreeDecimal(zoomTemp[0]));
                    Runnable previewUpdate = () -> showPreview(
                            request.preview,
                            zoomTemp[0],
                            request.preview.degree,
                            request.preview.alpha,
                            request.preview.positionX,
                            request.preview.positionY,
                            request.preview.touchAndMove,
                            request.preview.overLayout,
                            fromUser ? OverlayRuntimeController.PREVIEW_MODE_OUTLINE : OverlayRuntimeController.PREVIEW_MODE_FULL,
                            false,
                            request.preview.touchAndMove
                    );
                    if (fromUser) {
                        previewThrottler.submit(previewUpdate);
                    } else {
                        previewThrottler.runNow(previewUpdate);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() > 0) {
                    previewThrottler.runNow(() -> showPreview(
                            request.preview,
                            zoomTemp[0],
                            request.preview.degree,
                            request.preview.alpha,
                            request.preview.positionX,
                            request.preview.positionY,
                            request.preview.touchAndMove,
                            request.preview.overLayout,
                            OverlayRuntimeController.PREVIEW_MODE_FULL,
                            false,
                            request.preview.touchAndMove
                    ));
                }
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float editTextValue = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (editTextValue > 0 && (request.batchMode || request.preview.overLayout || editTextValue <= request.maxSize)) {
                    zoomTemp[0] = editTextValue;
                    editText.setText(formatThreeDecimal(zoomTemp[0]));
                    boolean updatedBySeekBar = false;
                    if (zoomTemp[0] <= request.maxSize) {
                        int progress = Math.max(1, Math.min(seekBar.getMax(), toThreeDecimalProgress(zoomTemp[0])));
                        if (seekBar.getProgress() != progress) {
                            seekBar.setProgress(progress);
                            updatedBySeekBar = true;
                        }
                    }
                    if (!updatedBySeekBar) {
                        previewThrottler.runNow(() -> showPreview(
                                request.preview,
                                zoomTemp[0],
                                request.preview.degree,
                                request.preview.alpha,
                                request.preview.positionX,
                                request.preview.positionY,
                                request.preview.touchAndMove,
                                request.preview.overLayout,
                                OverlayRuntimeController.PREVIEW_MODE_FULL,
                                false,
                                request.preview.touchAndMove
                        ));
                    }
                } else {
                    showToast(R.string.settings_picture_resize_warn);
                }
            } catch (NumberFormatException ignored) {
                showToast(R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            if (inputValue != null && inputValue > 0f && (request.batchMode || request.preview.overLayout || inputValue <= request.maxSize)) {
                listener.onConfirmed(inputValue);
            } else {
                listener.onConfirmed(zoomTemp[0]);
            }
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> callbacks.showWorkingWindowPreview(request.preview.alpha));
        dialog.setView(view);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> {
            previewThrottler.cancel();
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
    }

    void showDegreeDialog(@NonNull PreviewValues preview, @NonNull FloatResultListener listener) {
        View view = inflateDialogView(R.layout.dialog_set_size, R.id.layout_dialog_set_size);
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.requireContext());
        dialog.setTitle(R.string.settings_picture_degree);
        dialog.setCancelable(false);
        TextView name = view.findViewById(R.id.textview_set_size);
        name.setText(R.string.degree);
        final SeekBar seekBar = view.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(360 * THREE_DECIMAL_SCALE);
        seekBar.setProgress(Math.min(seekBar.getMax(), toThreeDecimalProgress(preview.degree)));
        final EditText editText = view.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(preview.degree));
        final float[] degreeTemp = {roundToThreeDecimals(preview.degree)};
        final PreviewUpdateThrottler previewThrottler = new PreviewUpdateThrottler(BITMAP_PREVIEW_THROTTLE_MS);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                degreeTemp[0] = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                editText.setText(formatThreeDecimal(degreeTemp[0]));
                Runnable previewUpdate = () -> showPreview(
                        preview,
                        preview.zoom,
                        degreeTemp[0],
                        preview.alpha,
                        preview.positionX,
                        preview.positionY,
                        preview.touchAndMove,
                        preview.overLayout,
                        fromUser ? OverlayRuntimeController.PREVIEW_MODE_LOW_RES : OverlayRuntimeController.PREVIEW_MODE_FULL,
                        false,
                        preview.touchAndMove
                );
                if (fromUser) {
                    previewThrottler.submit(previewUpdate);
                } else {
                    previewThrottler.runNow(previewUpdate);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                previewThrottler.runNow(() -> showPreview(
                        preview,
                        preview.zoom,
                        degreeTemp[0],
                        preview.alpha,
                        preview.positionX,
                        preview.positionY,
                        preview.touchAndMove,
                        preview.overLayout,
                        OverlayRuntimeController.PREVIEW_MODE_FULL,
                        false,
                        preview.touchAndMove
                ));
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float editTextValue = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (editTextValue >= 0 && editTextValue <= 360) {
                    degreeTemp[0] = editTextValue;
                    editText.setText(formatThreeDecimal(degreeTemp[0]));
                    int progress = Math.min(seekBar.getMax(), toThreeDecimalProgress(degreeTemp[0]));
                    if (seekBar.getProgress() != progress) {
                        seekBar.setProgress(progress);
                    } else {
                        previewThrottler.runNow(() -> showPreview(
                                preview,
                                preview.zoom,
                                degreeTemp[0],
                                preview.alpha,
                                preview.positionX,
                                preview.positionY,
                                preview.touchAndMove,
                                preview.overLayout,
                                OverlayRuntimeController.PREVIEW_MODE_FULL,
                                false,
                                preview.touchAndMove
                        ));
                    }
                } else {
                    showToast(R.string.settings_number_warn);
                }
            } catch (NumberFormatException ignored) {
                showToast(R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            if (inputValue != null && inputValue >= 0f && inputValue <= 360f) {
                listener.onConfirmed(inputValue);
            } else {
                listener.onConfirmed(degreeTemp[0]);
            }
            callbacks.showWorkingWindowPreview(preview.alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> callbacks.showWorkingWindowPreview(preview.alpha));
        dialog.setView(view);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> {
            previewThrottler.cancel();
            callbacks.showWorkingWindowPreview(preview.alpha);
        });
    }

    void showAlphaDialog(@NonNull PreviewValues preview, @NonNull FloatResultListener listener) {
        View view = inflateDialogView(R.layout.dialog_set_size, R.id.layout_dialog_set_size);
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.requireContext());
        dialog.setTitle(R.string.settings_picture_alpha);
        dialog.setCancelable(false);
        TextView name = view.findViewById(R.id.textview_set_size);
        name.setText(R.string.transparency);
        final SeekBar seekBar = view.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(THREE_DECIMAL_SCALE);
        seekBar.setProgress(Math.min(seekBar.getMax(), toThreeDecimalProgress(preview.alpha)));
        final EditText editText = view.findViewById(R.id.edittext_set_size);
        enableDecimalInput(editText);
        editText.setText(formatThreeDecimal(preview.alpha));
        final float[] alphaTemp = {roundToThreeDecimals(preview.alpha)};
        final PreviewUpdateThrottler previewThrottler = new PreviewUpdateThrottler(LIGHT_PREVIEW_THROTTLE_MS);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaTemp[0] = roundToThreeDecimals(((float) progress) / THREE_DECIMAL_SCALE);
                editText.setText(formatThreeDecimal(alphaTemp[0]));
                Runnable previewUpdate = () -> showAlphaPreview(preview, alphaTemp[0]);
                if (fromUser) {
                    previewThrottler.submit(previewUpdate);
                } else {
                    previewThrottler.runNow(previewUpdate);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            try {
                float editTextValue = roundToThreeDecimals(Float.parseFloat(v.getText().toString().trim()));
                if (editTextValue >= 0 && editTextValue <= 1) {
                    alphaTemp[0] = editTextValue;
                    editText.setText(formatThreeDecimal(alphaTemp[0]));
                    int progress = Math.min(seekBar.getMax(), toThreeDecimalProgress(alphaTemp[0]));
                    if (seekBar.getProgress() != progress) {
                        seekBar.setProgress(progress);
                    } else {
                        previewThrottler.runNow(() -> showAlphaPreview(preview, alphaTemp[0]));
                    }
                } else {
                    showToast(R.string.settings_number_warn);
                }
            } catch (NumberFormatException ignored) {
                showToast(R.string.settings_number_warn);
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Float inputValue = parseThreeDecimalFloat(editText);
            float confirmedAlpha;
            if (inputValue != null && inputValue >= 0f && inputValue <= 1f) {
                confirmedAlpha = inputValue;
            } else {
                confirmedAlpha = alphaTemp[0];
            }
            listener.onConfirmed(confirmedAlpha);
            previewThrottler.runNow(() -> showAlphaPreview(preview, confirmedAlpha));
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> {
            previewThrottler.cancel();
            showAlphaPreview(preview, preview.alpha);
        });
        dialog.setView(view);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> previewThrottler.cancel());
    }

    void showAppearanceDialog(@NonNull AppearanceDialogRequest request,
                              @NonNull AppearanceResultListener listener) {
        View view = inflateDialogView(R.layout.dialog_set_appearance_options, R.id.layout_dialog_set_appearance_options);
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.requireContext());
        dialog.setTitle(request.titleResId);
        dialog.setCancelable(false);
        TextView name = view.findViewById(R.id.textview_set_size);
        name.setText(request.valueLabelResId);
        TextView positionsLabel = view.findViewById(R.id.textview_set_positions);
        positionsLabel.setText(request.optionsLabelResId);
        final SeekBar seekBar = view.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(request.maxPercent);
        seekBar.setProgress(toRatioPercentProgress(request.targetRatio));
        final EditText editText = view.findViewById(R.id.edittext_set_size);
        enableIntegerInput(editText);
        editText.setText(String.valueOf(toRatioPercentProgress(request.targetRatio)));
        final CheckBox[] optionCheckBoxes = PictureSettingsAppearanceOptions.getOptionCheckBoxes(view);
        PictureSettingsAppearanceOptions.bindOptionCheckBoxes(
                optionCheckBoxes,
                request.optionLabelResIds,
                request.optionBits,
                request.targetMask
        );
        final float[] ratioTemp = {request.targetRatio};
        final int[] maskTemp = {request.targetMask};
        final PreviewUpdateThrottler previewThrottler = new PreviewUpdateThrottler(BITMAP_PREVIEW_THROTTLE_MS);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ratioTemp[0] = percentProgressToRatio(progress);
                editText.setText(String.valueOf(progress));
                maskTemp[0] = PictureSettingsAppearanceOptions.resolveCheckedMask(optionCheckBoxes, request.optionBits);
                Runnable previewUpdate = () -> showAppearancePreview(request, ratioTemp[0], maskTemp[0]);
                if (fromUser) {
                    previewThrottler.submit(previewUpdate);
                } else {
                    previewThrottler.runNow(previewUpdate);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            Integer progress = parsePercentProgress(editText);
            if (progress != null && progress >= 0 && progress <= request.maxPercent) {
                ratioTemp[0] = percentProgressToRatio(progress);
                maskTemp[0] = PictureSettingsAppearanceOptions.resolveCheckedMask(optionCheckBoxes, request.optionBits);
                editText.setText(String.valueOf(progress));
                if (seekBar.getProgress() != progress) {
                    seekBar.setProgress(progress);
                } else {
                    previewThrottler.runNow(() -> showAppearancePreview(request, ratioTemp[0], maskTemp[0]));
                }
            } else {
                showToast(R.string.settings_number_warn);
            }
            return false;
        });
        for (CheckBox optionCheckBox : optionCheckBoxes) {
            optionCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                maskTemp[0] = PictureSettingsAppearanceOptions.resolveCheckedMask(optionCheckBoxes, request.optionBits);
                previewThrottler.runNow(() -> showAppearancePreview(request, ratioTemp[0], maskTemp[0]));
            });
        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            Integer progress = parsePercentProgress(editText);
            float targetRatio = progress != null && progress >= 0 && progress <= request.maxPercent
                    ? percentProgressToRatio(progress)
                    : ratioTemp[0];
            int targetMask = PictureSettingsAppearanceOptions.resolveCheckedMask(optionCheckBoxes, request.optionBits);
            listener.onConfirmed(targetRatio, targetMask);
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> callbacks.showWorkingWindowPreview(request.preview.alpha));
        dialog.setView(view);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> {
            previewThrottler.cancel();
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
    }

    void showPositionDialog(@NonNull PositionDialogRequest request,
                            @NonNull PositionResultListener listener) {
        callbacks.showPreview(
                request.preview.zoom,
                request.preview.degree,
                request.preview.alpha,
                request.preview.cornerRadiusRatio,
                request.preview.cornerRadiusMask,
                request.preview.edgeFeatherRatio,
                request.preview.edgeFeatherMask,
                request.preview.positionX,
                request.preview.positionY,
                request.touchableEdit,
                request.preview.overLayout,
                OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                false,
                false
        );
        final PreviewUpdateThrottler previewThrottler = new PreviewUpdateThrottler(LIGHT_PREVIEW_THROTTLE_MS);

        View view = inflateDialogView(R.layout.dialog_set_position, R.id.layout_dialog_set_position);
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.requireContext());
        dialog.setTitle(R.string.settings_picture_position);
        dialog.setCancelable(false);
        final SeekBar seekBarX = view.findViewById(R.id.seekbar_set_position_x);
        if (!request.preview.overLayout) {
            seekBarX.setMax(request.windowSize.x);
            seekBarX.setProgress(request.preview.positionX);
        }
        final EditText editTextX = view.findViewById(R.id.edittext_set_position_x);
        editTextX.setText(String.valueOf(request.preview.positionX));
        final SeekBar seekBarY = view.findViewById(R.id.seekbar_set_position_y);
        if (!request.preview.overLayout) {
            seekBarY.setMax(request.windowSize.y);
            seekBarY.setProgress(request.preview.positionY);
        }
        final EditText editTextY = view.findViewById(R.id.edittext_set_position_y);
        editTextY.setText(String.valueOf(request.preview.positionY));
        if (request.preview.overLayout) {
            editTextX.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            editTextY.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        final int[] positionXTemp = {request.preview.positionX};
        final int[] positionYTemp = {request.preview.positionY};
        seekBarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                positionXTemp[0] = progress;
                editTextX.setText(String.valueOf(progress));
                Runnable previewUpdate = () -> showPositionPreview(request, positionXTemp[0], positionYTemp[0]);
                if (fromUser) {
                    previewThrottler.submit(previewUpdate);
                } else {
                    previewThrottler.runNow(previewUpdate);
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
            try {
                int editTextValue = Integer.parseInt(v.getText().toString());
                if (request.preview.overLayout || (editTextValue >= 0 && editTextValue <= request.windowSize.x)) {
                    positionXTemp[0] = editTextValue;
                    if (!request.preview.overLayout) {
                        seekBarX.setProgress(editTextValue);
                    }
                    previewThrottler.runNow(() -> showPositionPreview(request, positionXTemp[0], positionYTemp[0]));
                } else {
                    showToast(R.string.settings_picture_position_warn);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        });
        seekBarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                positionYTemp[0] = progress;
                editTextY.setText(String.valueOf(progress));
                Runnable previewUpdate = () -> showPositionPreview(request, positionXTemp[0], positionYTemp[0]);
                if (fromUser) {
                    previewThrottler.submit(previewUpdate);
                } else {
                    previewThrottler.runNow(previewUpdate);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editTextY.setOnEditorActionListener((v, actionId, event) -> {
            try {
                int editTextValue = Integer.parseInt(v.getText().toString());
                if (request.preview.overLayout || (editTextValue >= 0 && editTextValue <= request.windowSize.y)) {
                    positionYTemp[0] = editTextValue;
                    if (!request.preview.overLayout) {
                        seekBarY.setProgress(editTextValue);
                    }
                    previewThrottler.runNow(() -> showPositionPreview(request, positionXTemp[0], positionYTemp[0]));
                } else {
                    showToast(R.string.settings_picture_position_warn);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        });
        if (request.preview.overLayout) {
            seekBarX.setEnabled(false);
            seekBarY.setEnabled(false);
        }
        if (request.touchableEdit) {
            dialog.setNeutralButton(R.string.save_moved_position, (dialog1, which) -> {
                previewThrottler.cancel();
                Point previewPosition = callbacks.getPreviewPosition(positionXTemp[0], positionYTemp[0]);
                listener.onConfirmed(previewPosition.x, previewPosition.y);
                callbacks.showWorkingWindowPreview(request.preview.alpha);
            });
        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            int positionX;
            int positionY;
            if (request.preview.overLayout) {
                try {
                    positionX = Integer.parseInt(editTextX.getText().toString());
                    positionY = Integer.parseInt(editTextY.getText().toString());
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    positionX = positionXTemp[0];
                    positionY = positionYTemp[0];
                }
            } else {
                positionX = positionXTemp[0];
                positionY = positionYTemp[0];
            }
            previewThrottler.cancel();
            listener.onConfirmed(positionX, positionY);
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> {
            previewThrottler.cancel();
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
        dialog.setView(view);
        AlertDialog alertDialog = dialog.show();
        alertDialog.setOnDismissListener(d -> {
            previewThrottler.cancel();
            callbacks.showWorkingWindowPreview(request.preview.alpha);
        });
    }

    private void showAppearancePreview(@NonNull AppearanceDialogRequest request, float targetRatio, int targetMask) {
        if (request.cornerRadiusTarget) {
            callbacks.showPreview(
                    request.preview.zoom,
                    request.preview.degree,
                    request.preview.alpha,
                    targetRatio,
                    targetMask,
                    request.preview.edgeFeatherRatio,
                    request.preview.edgeFeatherMask,
                    request.preview.positionX,
                    request.preview.positionY,
                    request.preview.touchAndMove,
                    request.preview.overLayout,
                    OverlayRuntimeController.PREVIEW_MODE_FULL,
                    false,
                    request.preview.touchAndMove
            );
            return;
        }
        callbacks.showPreview(
                request.preview.zoom,
                request.preview.degree,
                request.preview.alpha,
                request.preview.cornerRadiusRatio,
                request.preview.cornerRadiusMask,
                targetRatio,
                targetMask,
                request.preview.positionX,
                request.preview.positionY,
                request.preview.touchAndMove,
                request.preview.overLayout,
                OverlayRuntimeController.PREVIEW_MODE_FULL,
                false,
                request.preview.touchAndMove
        );
    }

    private void showPositionPreview(@NonNull PositionDialogRequest request, int positionX, int positionY) {
        callbacks.showPreview(
                request.preview.zoom,
                request.preview.degree,
                request.preview.alpha,
                request.preview.cornerRadiusRatio,
                request.preview.cornerRadiusMask,
                request.preview.edgeFeatherRatio,
                request.preview.edgeFeatherMask,
                positionX,
                positionY,
                request.touchableEdit,
                request.preview.overLayout,
                OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                false,
                false
        );
    }

    private void showAlphaPreview(@NonNull PreviewValues preview, float alpha) {
        callbacks.showPreview(
                preview.zoom,
                preview.degree,
                alpha,
                preview.cornerRadiusRatio,
                preview.cornerRadiusMask,
                preview.edgeFeatherRatio,
                preview.edgeFeatherMask,
                preview.positionX,
                preview.positionY,
                preview.touchAndMove,
                preview.overLayout,
                OverlayRuntimeController.PREVIEW_MODE_MOVE_ONLY,
                false,
                preview.touchAndMove
        );
    }

    private void showPreview(@NonNull PreviewValues preview,
                             float zoom,
                             float degree,
                             float alpha,
                             int positionX,
                             int positionY,
                             boolean touchAndMove,
                             boolean overLayout,
                             int previewMode,
                             boolean reloadSource,
                             boolean useRuntimePosition) {
        callbacks.showPreview(
                zoom,
                degree,
                alpha,
                preview.cornerRadiusRatio,
                preview.cornerRadiusMask,
                preview.edgeFeatherRatio,
                preview.edgeFeatherMask,
                positionX,
                positionY,
                touchAndMove,
                overLayout,
                previewMode,
                reloadSource,
                useRuntimePosition
        );
    }

    @NonNull
    private View inflateDialogView(@LayoutRes int layoutResId, @IdRes int rootId) {
        return inflater.inflate(layoutResId, fragment.requireActivity().findViewById(rootId));
    }

    private void enableDecimalInput(EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    private void enableIntegerInput(EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
    }

    private void showToast(@StringRes int stringResId) {
        ApplicationMethods.showToast(fragment.requireContext(), stringResId);
    }

    private final class PreviewUpdateThrottler {
        private final Runnable flushRunnable = this::flush;
        private final long throttleMs;
        private Runnable pendingUpdate;
        private long lastRunTime;

        private PreviewUpdateThrottler(long throttleMs) {
            this.throttleMs = throttleMs;
        }

        void submit(@NonNull Runnable update) {
            pendingUpdate = update;
            long now = SystemClock.uptimeMillis();
            long delay = Math.max(0L, throttleMs - (now - lastRunTime));
            mainHandler.removeCallbacks(flushRunnable);
            mainHandler.postDelayed(flushRunnable, delay);
        }

        void runNow(@NonNull Runnable update) {
            cancel();
            lastRunTime = SystemClock.uptimeMillis();
            update.run();
        }

        void cancel() {
            pendingUpdate = null;
            mainHandler.removeCallbacks(flushRunnable);
        }

        private void flush() {
            Runnable update = pendingUpdate;
            pendingUpdate = null;
            if (update == null) {
                return;
            }
            lastRunTime = SystemClock.uptimeMillis();
            update.run();
        }
    }

    interface Callbacks {
        void showPreview(float zoom,
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
                         boolean reloadSource,
                         boolean useRuntimePosition);

        void showWorkingWindowPreview(float alpha);

        @NonNull
        Point getPreviewPosition(int fallbackX, int fallbackY);
    }

    interface FloatResultListener {
        void onConfirmed(float value);
    }

    interface AppearanceResultListener {
        void onConfirmed(float ratio, int mask);
    }

    interface PositionResultListener {
        void onConfirmed(int positionX, int positionY);
    }

    static final class PreviewValues {
        final float zoom;
        final float degree;
        final float alpha;
        final float cornerRadiusRatio;
        final int cornerRadiusMask;
        final float edgeFeatherRatio;
        final int edgeFeatherMask;
        final int positionX;
        final int positionY;
        final boolean touchAndMove;
        final boolean overLayout;

        PreviewValues(float zoom,
                      float degree,
                      float alpha,
                      float cornerRadiusRatio,
                      int cornerRadiusMask,
                      float edgeFeatherRatio,
                      int edgeFeatherMask,
                      int positionX,
                      int positionY,
                      boolean touchAndMove,
                      boolean overLayout) {
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
        }
    }

    static final class SizeDialogRequest {
        final boolean batchMode;
        final float maxSize;
        final PreviewValues preview;

        SizeDialogRequest(boolean batchMode, float maxSize, @NonNull PreviewValues preview) {
            this.batchMode = batchMode;
            this.maxSize = maxSize;
            this.preview = preview;
        }
    }

    static final class AppearanceDialogRequest {
        final boolean cornerRadiusTarget;
        final int titleResId;
        final int valueLabelResId;
        final int optionsLabelResId;
        final int maxPercent;
        final int[] optionLabelResIds;
        final int[] optionBits;
        final float targetRatio;
        final int targetMask;
        final PreviewValues preview;

        AppearanceDialogRequest(boolean cornerRadiusTarget,
                                @StringRes int titleResId,
                                @StringRes int valueLabelResId,
                                @StringRes int optionsLabelResId,
                                int maxPercent,
                                @NonNull int[] optionLabelResIds,
                                @NonNull int[] optionBits,
                                float targetRatio,
                                int targetMask,
                                @NonNull PreviewValues preview) {
            this.cornerRadiusTarget = cornerRadiusTarget;
            this.titleResId = titleResId;
            this.valueLabelResId = valueLabelResId;
            this.optionsLabelResId = optionsLabelResId;
            this.maxPercent = maxPercent;
            this.optionLabelResIds = optionLabelResIds;
            this.optionBits = optionBits;
            this.targetRatio = targetRatio;
            this.targetMask = targetMask;
            this.preview = preview;
        }
    }

    static final class PositionDialogRequest {
        final boolean touchableEdit;
        final Point windowSize;
        final PreviewValues preview;

        PositionDialogRequest(boolean touchableEdit,
                              @NonNull Point windowSize,
                              @NonNull PreviewValues preview) {
            this.touchableEdit = touchableEdit;
            this.windowSize = windowSize;
            this.preview = preview;
        }
    }
}
