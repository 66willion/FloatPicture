package tool.xfy9326.floatpicture.View;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.NonNull;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

final class PictureSettingsAppearanceOptions {
    private PictureSettingsAppearanceOptions() {
    }

    @NonNull
    static String formatCornerMaskSummary(@NonNull Context context, int mask) {
        return formatSelectionSummary(
                context,
                mask,
                new int[]{
                        Config.MASK_CORNER_TOP_LEFT,
                        Config.MASK_CORNER_TOP_RIGHT,
                        Config.MASK_CORNER_BOTTOM_LEFT,
                        Config.MASK_CORNER_BOTTOM_RIGHT
                },
                new int[]{
                        R.string.position_top_left,
                        R.string.position_top_right,
                        R.string.position_bottom_left,
                        R.string.position_bottom_right
                }
        );
    }

    @NonNull
    static String formatEdgeMaskSummary(@NonNull Context context, int mask) {
        return formatSelectionSummary(
                context,
                mask,
                new int[]{
                        Config.MASK_EDGE_TOP,
                        Config.MASK_EDGE_BOTTOM,
                        Config.MASK_EDGE_LEFT,
                        Config.MASK_EDGE_RIGHT
                },
                new int[]{
                        R.string.position_top,
                        R.string.position_bottom,
                        R.string.position_left,
                        R.string.position_right
                }
        );
    }

    @NonNull
    static CheckBox[] getOptionCheckBoxes(@NonNull View view) {
        return new CheckBox[]{
                view.findViewById(R.id.checkbox_option_1),
                view.findViewById(R.id.checkbox_option_2),
                view.findViewById(R.id.checkbox_option_3),
                view.findViewById(R.id.checkbox_option_4)
        };
    }

    static void bindOptionCheckBoxes(CheckBox[] checkBoxes, int[] labelResIds, int[] optionBits, int mask) {
        for (int index = 0; index < checkBoxes.length; index++) {
            checkBoxes[index].setText(labelResIds[index]);
            checkBoxes[index].setChecked((mask & optionBits[index]) != 0);
        }
    }

    static int resolveCheckedMask(CheckBox[] checkBoxes, int[] optionBits) {
        int mask = 0;
        for (int index = 0; index < checkBoxes.length; index++) {
            if (checkBoxes[index].isChecked()) {
                mask |= optionBits[index];
            }
        }
        return mask;
    }

    @NonNull
    private static String formatSelectionSummary(@NonNull Context context,
                                                 int mask,
                                                 int[] optionBits,
                                                 int[] labelResIds) {
        int fullMask = 0;
        for (int optionBit : optionBits) {
            fullMask |= optionBit;
        }
        if ((mask & fullMask) == 0) {
            return context.getString(R.string.selection_none);
        }
        if ((mask & fullMask) == fullMask) {
            return context.getString(R.string.selection_all);
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (int index = 0; index < optionBits.length; index++) {
            if ((mask & optionBits[index]) == 0) {
                continue;
            }
            if (summaryBuilder.length() > 0) {
                summaryBuilder.append(' ');
            }
            summaryBuilder.append(context.getString(labelResIds[index]));
        }
        return summaryBuilder.toString();
    }
}
