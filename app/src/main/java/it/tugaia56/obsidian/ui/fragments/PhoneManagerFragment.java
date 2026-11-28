package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.hooks.settings.PhoneManagerBarMod;

/**
 * Gestione telefono: bordo della barretta in basso. Le scelte (salvate qui) vengono anche scritte come proprieta' di sistema,
 * che la mod {@link PhoneManagerBarMod} legge dentro l'app. Serve la spunta su "Gestione telefono" nell'ambito di Oxydian.
 */
public class PhoneManagerFragment extends Fragment {

    private static final String PREF_ON = "pm_bar_border_on";
    private static final String PREF_W = "pm_bar_border_width";     // mezzi dp, 1..8
    private static final String PREF_MODE = "pm_bar_border_mode";   // 0 accento, 1 colore scelto
    private static final String PREF_COLOR = "pm_bar_border_color";
    private static final String PREF_FILL_MODE = "pm_bar_fill_mode";       // 0 originale, 1 accento, 2 colore scelto
    private static final String PREF_FILL_COLOR = "pm_bar_fill_color";
    private static final int DIALOG_COLOR = PREF_COLOR.hashCode();
    private static final int DIALOG_FILL = PREF_FILL_COLOR.hashCode();

    private RecyclerView mRv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_COLOR) {
            ObsidianPrefs.putInt(PREF_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_MODE, 1);
        } else if (event.dialogId() == DIALOG_FILL) {
            ObsidianPrefs.putInt(PREF_FILL_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_FILL_MODE, 2);
        } else {
            return;
        }
        applyProps();
        rebuild();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 12, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRv = (RecyclerView) view;
        rebuild();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.pmbar_note))));

        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.pmbar_fill_title), fillLabel(), this::showFillDialog));
        SwitchWidgetAdapter.SwitchItem border = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.pmbar_border_title), getString(R.string.pmbar_border_summary),
                ObsidianPrefs.getBoolean(PREF_ON, false), null);
        border.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_ON, border.checked);
            applyProps();
            rebuild();
        };
        rows.add(border);
        if (ObsidianPrefs.getBoolean(PREF_ON, false)) {
            rows.add(new ListWidgetAdapter.ListItem(getString(R.string.dial_border_color_title), colorLabel(), this::showColorDialog));
            rows.add(new SliderWidgetAdapter.SliderItem(
                    getString(R.string.msg_border_width), ObsidianPrefs.getInt(PREF_W, 3), 1, 8, "", 3,
                    value -> { ObsidianPrefs.putInt(PREF_W, value); applyProps(); }));
        }
        GroupUtils.addGroup(chain, rows);

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private String fillLabel() {
        int m = ObsidianPrefs.getInt(PREF_FILL_MODE, 0);
        if (m == 1) return getString(R.string.msg_border_accent);
        if (m == 2) return String.format("#%08X", ObsidianPrefs.getInt(PREF_FILL_COLOR, defaultColor()));
        return getString(R.string.msg_in_original);
    }

    private void showFillDialog() {
        String[] entries = {getString(R.string.msg_in_original), getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(PREF_FILL_MODE, 0)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pmbar_fill_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (sel[0] == 2) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_FILL,
                                    ObsidianPrefs.getInt(PREF_FILL_COLOR, defaultColor()), true, true, true);
                        }
                        return;
                    }
                    ObsidianPrefs.putInt(PREF_FILL_MODE, sel[0]);
                    applyProps();
                    rebuild();
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    private String colorLabel() {
        if (ObsidianPrefs.getInt(PREF_MODE, 0) == 0) return getString(R.string.msg_border_accent);
        return String.format("#%06X", ObsidianPrefs.getInt(PREF_COLOR, defaultColor()) & 0xFFFFFF);
    }

    private static int defaultColor() {
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.themeAccent(0xFF908DFF);
    }

    private void showColorDialog() {
        String[] entries = {getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(PREF_MODE, 0)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dial_border_color_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (sel[0] == 0) {
                        ObsidianPrefs.putInt(PREF_MODE, 0);
                        applyProps();
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_COLOR,
                                ObsidianPrefs.getInt(PREF_COLOR, defaultColor()) | 0xFF000000, false, true, true);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    /** Scrive le scelte come proprieta' di sistema (le legge la mod dentro Gestione telefono). */
    private static void applyProps() {
        final int width = ObsidianPrefs.getBoolean(PREF_ON, false) ? ObsidianPrefs.getInt(PREF_W, 3) : 0;
        final String color = ObsidianPrefs.getInt(PREF_MODE, 0) == 0 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_COLOR, defaultColor()) | 0xFF000000);
        final int fillMode = ObsidianPrefs.getInt(PREF_FILL_MODE, 0);
        final String fill = fillMode == 0 ? "0" : fillMode == 1 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_FILL_COLOR, defaultColor()));
        new Thread(() -> Shell.cmd(
                "setprop " + PhoneManagerBarMod.PROP_FILL + " " + fill,
                "setprop " + PhoneManagerBarMod.PROP_BORDER + " " + width,
                "setprop " + PhoneManagerBarMod.PROP_COLOR + " " + color).exec()).start();
    }
}
