package it.tugaia56.obsidian.ui.adapters;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos;

/**
 * Wires a heterogeneous ordered list of row items (switch/slider/list items) into a
 * ConcatAdapter chain so they render as one continuous merged card — matching OC/OOS's
 * per-category card look — instead of each row being its own separate rounded pill.
 */
public final class GroupUtils {
    private GroupUtils() {}

    /**
     * Adds each item in {@code rows} to {@code chain} as its own single-item adapter,
     * assigning TOP/MIDDLE/BOTTOM/SINGLE group positions so the whole list renders as
     * one seamless card. Accepts {@link SwitchWidgetAdapter.SwitchItem},
     * {@link SliderWidgetAdapter.SliderItem}, {@link ListWidgetAdapter.ListItem} and
     * {@link DualSliderWidgetAdapter.DualSliderItem} (no "nested" field on that last one,
     * so the {@code nested} flag is a no-op for it).
     */
    public static void addGroup(List<RecyclerView.Adapter<?>> chain, List<?> rows) {
        addGroup(chain, rows, false);
    }

    /** Same as {@link #addGroup(List, List)}, but marks every row "nested" (lighter/bordered
     *  background instead of the normal card colour) — for options revealed by tapping a
     *  row above them, so they read as belonging to it instead of blending into the rest. */
    public static void addGroup(List<RecyclerView.Adapter<?>> chain, List<?> rows, boolean nested) {
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            GroupPos pos = n == 1 ? GroupPos.SINGLE
                    : i == 0 ? GroupPos.TOP
                    : i == n - 1 ? GroupPos.BOTTOM
                    : GroupPos.MIDDLE;
            Object row = rows.get(i);
            if (row instanceof SwitchWidgetAdapter.SwitchItem s) {
                s.groupPos = pos;
                s.nested = nested;
                chain.add(new SwitchWidgetAdapter(List.of(s)));
            } else if (row instanceof SliderWidgetAdapter.SliderItem s) {
                s.groupPos = pos;
                s.nested = nested;
                chain.add(new SliderWidgetAdapter(List.of(s)));
            } else if (row instanceof ListWidgetAdapter.ListItem s) {
                s.groupPos = pos;
                s.nested = nested;
                chain.add(new ListWidgetAdapter(List.of(s)));
            } else if (row instanceof DualSliderWidgetAdapter.DualSliderItem s) {
                s.groupPos = pos;
                chain.add(new DualSliderWidgetAdapter(List.of(s)));
            }
        }
    }
}
