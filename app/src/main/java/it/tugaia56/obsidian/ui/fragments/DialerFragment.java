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
import it.tugaia56.obsidian.xposed.hooks.dialer.DialerKeypadMod;

/**
 * Telefono Google: sfondo, bordo e colore dei numeri del tastierino. Le scelte (salvate qui) vengono anche scritte come
 * proprieta' di sistema, che la mod {@link DialerKeypadMod} legge dentro l'app Telefono.
 * Serve la spunta su "Telefono" nell'ambito di Oxydian in LSPosed.
 */
public class DialerFragment extends Fragment {

    private static final String PREF_FILL_MODE = "dial_key_fill_mode";       // 0 originale, 1 accento, 2 colore scelto
    private static final String PREF_FILL_COLOR = "dial_key_fill_color";
    private static final String PREF_BORDER_ON = "dial_key_border_on";
    private static final String PREF_BORDER_W = "dial_key_border_width";     // mezzi dp, 1..8
    private static final String PREF_BORDER_MODE = "dial_key_border_mode";   // 0 accento, 1 colore scelto
    private static final String PREF_BORDER_COLOR = "dial_key_border_color";
    private static final String PREF_NUM_MODE = "dial_number_mode";          // 0 originale, 1 accento, 2 colore scelto
    private static final String PREF_NUM_COLOR = "dial_number_color";
    private static final int DIALOG_FILL = PREF_FILL_COLOR.hashCode();
    private static final int DIALOG_NUM = PREF_NUM_COLOR.hashCode();
    private static final int DIALOG_BORDER = PREF_BORDER_COLOR.hashCode();

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
        if (event.dialogId() == DIALOG_FILL) {
            ObsidianPrefs.putInt(PREF_FILL_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_FILL_MODE, 2);
        } else if (event.dialogId() == DIALOG_NUM) {
            ObsidianPrefs.putInt(PREF_NUM_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_NUM_MODE, 2);
        } else if (event.dialogId() == DIALOG_BORDER) {
            ObsidianPrefs.putInt(PREF_BORDER_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_BORDER_MODE, 1);
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
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.dial_note))));
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.dial_section_keys))));

        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.dial_fill_title), fillLabel(), this::showFillDialog));
        SwitchWidgetAdapter.SwitchItem border = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.dial_border_title), getString(R.string.dial_border_summary),
                ObsidianPrefs.getBoolean(PREF_BORDER_ON, false), null);
        border.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BORDER_ON, border.checked);
            applyProps();
            rebuild();
        };
        rows.add(border);
        if (ObsidianPrefs.getBoolean(PREF_BORDER_ON, false)) {
            rows.add(new ListWidgetAdapter.ListItem(getString(R.string.dial_border_color_title), borderLabel(), this::showBorderDialog));
            rows.add(new SliderWidgetAdapter.SliderItem(
                    getString(R.string.msg_border_width), ObsidianPrefs.getInt(PREF_BORDER_W, 3), 1, 8, "", 3,
                    value -> { ObsidianPrefs.putInt(PREF_BORDER_W, value); applyProps(); }));
        }
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.dial_number_title), numberLabel(), this::showNumberDialog));
        GroupUtils.addGroup(chain, rows);

        android.os.Parcelable state = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (state != null && mRv.getLayoutManager() != null) mRv.getLayoutManager().onRestoreInstanceState(state);
    }

    private String fillLabel() {
        int m = ObsidianPrefs.getInt(PREF_FILL_MODE, 0);
        if (m == 1) return getString(R.string.msg_border_accent);
        if (m == 2) return String.format("#%08X", ObsidianPrefs.getInt(PREF_FILL_COLOR, defaultColor()));
        return getString(R.string.msg_in_original);
    }

    private String numberLabel() {
        int m = ObsidianPrefs.getInt(PREF_NUM_MODE, 0);
        if (m == 1) return getString(R.string.msg_border_accent);
        if (m == 2) return String.format("#%06X", ObsidianPrefs.getInt(PREF_NUM_COLOR, defaultColor()) & 0xFFFFFF);
        return getString(R.string.msg_in_original);
    }

    private void showNumberDialog() {
        String[] entries = {getString(R.string.msg_in_original), getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(PREF_NUM_MODE, 0)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dial_number_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (sel[0] == 2) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_NUM,
                                    ObsidianPrefs.getInt(PREF_NUM_COLOR, defaultColor()) | 0xFF000000, false, true, true);
                        }
                        return;
                    }
                    ObsidianPrefs.putInt(PREF_NUM_MODE, sel[0]);
                    applyProps();
                    rebuild();
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    private String borderLabel() {
        if (ObsidianPrefs.getInt(PREF_BORDER_MODE, 0) == 0) return getString(R.string.msg_border_accent);
        return String.format("#%06X", ObsidianPrefs.getInt(PREF_BORDER_COLOR, defaultColor()) & 0xFFFFFF);
    }

    private static int defaultColor() {
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.themeAccent(0xFF908DFF);
    }

    private void showFillDialog() {
        String[] entries = {getString(R.string.msg_in_original), getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(PREF_FILL_MODE, 0)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dial_fill_title)
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

    private void showBorderDialog() {
        String[] entries = {getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(PREF_BORDER_MODE, 0)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dial_border_color_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (sel[0] == 0) {
                        ObsidianPrefs.putInt(PREF_BORDER_MODE, 0);
                        applyProps();
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_BORDER,
                                ObsidianPrefs.getInt(PREF_BORDER_COLOR, defaultColor()) | 0xFF000000, false, true, true);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    /** Scrive le scelte come proprieta' di sistema (le legge la mod dentro l'app Telefono). */
    private static void applyProps() {
        final int fill = ObsidianPrefs.getInt(PREF_FILL_MODE, 0);
        final int fillColor = ObsidianPrefs.getInt(PREF_FILL_COLOR, defaultColor());
        final int width = ObsidianPrefs.getBoolean(PREF_BORDER_ON, false) ? ObsidianPrefs.getInt(PREF_BORDER_W, 3) : 0;
        final String border = ObsidianPrefs.getInt(PREF_BORDER_MODE, 0) == 0 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_BORDER_COLOR, defaultColor()) | 0xFF000000);
        final int numMode = ObsidianPrefs.getInt(PREF_NUM_MODE, 0);
        final String number = numMode == 0 ? "0" : numMode == 1 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_NUM_COLOR, defaultColor()) | 0xFF000000);
        new Thread(() -> Shell.cmd(
                "setprop " + DialerKeypadMod.PROP_NUMBER + " " + number,
                "setprop " + DialerKeypadMod.PROP_FILL + " " + fill,
                "setprop " + DialerKeypadMod.PROP_FILL_COLOR + " " + fillColor,
                "setprop " + DialerKeypadMod.PROP_BORDER + " " + width,
                "setprop " + DialerKeypadMod.PROP_BORDER_COLOR + " " + border).exec()).start();
    }
}
