package tool.xfy9326.floatpicture.View;

import android.widget.EditText;

import androidx.annotation.Nullable;

import java.util.Locale;

final class PictureSettingsValueFormatter {
    private static final int THREE_DECIMAL_SCALE = 1000;
    private static final int PERCENT_SCALE = 100;

    private PictureSettingsValueFormatter() {
    }

    static float roundToThreeDecimals(float value) {
        return Math.round(value * THREE_DECIMAL_SCALE) / (float) THREE_DECIMAL_SCALE;
    }

    static int toThreeDecimalProgress(float value) {
        return Math.round(roundToThreeDecimals(value) * THREE_DECIMAL_SCALE);
    }

    static int toRatioPercentProgress(float ratio) {
        return Math.round(clampRatio(ratio) * PERCENT_SCALE);
    }

    static String formatThreeDecimal(float value) {
        return String.format(Locale.US, "%.3f", roundToThreeDecimals(value));
    }

    @Nullable
    static Float parseThreeDecimalFloat(EditText editText) {
        try {
            return roundToThreeDecimals(Float.parseFloat(editText.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    static Integer parsePercentProgress(EditText editText) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static float percentProgressToRatio(int progress) {
        return clampRatio(progress / (float) PERCENT_SCALE);
    }

    static String formatRatioPercent(float ratio) {
        return toRatioPercentProgress(ratio) + "%";
    }

    private static float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }
}
