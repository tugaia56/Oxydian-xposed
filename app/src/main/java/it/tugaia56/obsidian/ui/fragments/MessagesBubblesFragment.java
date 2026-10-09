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
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Google Messaggi: aspetto delle bolle della chat. L'app Messaggi non vede le preferenze di Oxydian,
 * quindi le scelte (salvate qui) vengono anche scritte come proprieta' di sistema, che la mod
 * {@link it.tugaia56.obsidian.xposed.hooks.messages.MessagesBubbleMod} legge ogni pochi secondi.
 * Serve la spunta su "Messaggi" nell'ambito di Oxydian in LSPosed.
 */
public class MessagesBubblesFragment extends Fragment {

    private static final String PREF_OUT_MODE = "msg_bubble_out_mode";      // 0 originale, 1 scura, 2 accento 50%
    private static final String PREF_BORDER_ON = "msg_bubble_border_on";
    private static final String PREF_BORDER_W = "msg_bubble_border_width";  // mezzi dp, 1..8
    private static final String PREF_TAIL = "msg_bubble_tail";

    // Colore del bordo: 0 = accento, 1 = colore scelto (picker). Predefiniti: ricevute bianco, inviate accento.
    private static final String PREF_BORDER_IN_MODE = "msg_bubble_border_in_mode";
    private static final String PREF_BORDER_IN_COLOR = "msg_bubble_border_in_color";
    private static final String PREF_BORDER_OUT_MODE = "msg_bubble_border_out_mode";
    private static final String PREF_BORDER_OUT_COLOR = "msg_bubble_border_out_color";
    private static final int DIALOG_IN = PREF_BORDER_IN_COLOR.hashCode();
    private static final int DIALOG_OUT = PREF_BORDER_OUT_COLOR.hashCode();

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
        if (event.dialogId() == DIALOG_IN) {
            ObsidianPrefs.putInt(PREF_BORDER_IN_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_BORDER_IN_MODE, 1);
        } else if (event.dialogId() == DIALOG_OUT) {
            ObsidianPrefs.putInt(PREF_BORDER_OUT_COLOR, event.color());
            ObsidianPrefs.putInt(PREF_BORDER_OUT_MODE, 1);
        } else {
            return;
        }
        applyProps();
        rebuild();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.msg_note))));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.msg_section_out))));
        List<Object> outRows = new ArrayList<>();
        outRows.add(new ListWidgetAdapter.ListItem(getString(R.string.msg_out_title), outLabel(), this::showOutDialog));
        GroupUtils.addGroup(chain, outRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.msg_section_shape))));
        List<Object> shapeRows = new ArrayList<>();
        shapeRows.add(borderSwitch());
        if (ObsidianPrefs.getBoolean(PREF_BORDER_ON, true)) {
            shapeRows.add(new ListWidgetAdapter.ListItem(getString(R.string.msg_border_in_title),
                    borderLabel(PREF_BORDER_IN_MODE, PREF_BORDER_IN_COLOR, 1, 0xFFFFFFFF),
                    () -> showBorderDialog(PREF_BORDER_IN_MODE, PREF_BORDER_IN_COLOR, DIALOG_IN, 1, 0xFFFFFFFF, R.string.msg_border_in_title)));
            shapeRows.add(new ListWidgetAdapter.ListItem(getString(R.string.msg_border_out_title),
                    borderLabel(PREF_BORDER_OUT_MODE, PREF_BORDER_OUT_COLOR, 0, 0xFF908DFF),
                    () -> showBorderDialog(PREF_BORDER_OUT_MODE, PREF_BORDER_OUT_COLOR, DIALOG_OUT, 0, 0xFF908DFF, R.string.msg_border_out_title)));
            shapeRows.add(new SliderWidgetAdapter.SliderItem(
                    getString(R.string.msg_border_width), ObsidianPrefs.getInt(PREF_BORDER_W, 2), 1, 8, "", 2,
                    value -> { ObsidianPrefs.putInt(PREF_BORDER_W, value); applyProps(); }));
        }
        SwitchWidgetAdapter.SwitchItem tail = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.msg_tail_title), getString(R.string.msg_tail_summary),
                ObsidianPrefs.getBoolean(PREF_TAIL, true), null);
        tail.onChanged = () -> { ObsidianPrefs.putBoolean(PREF_TAIL, tail.checked); applyProps(); };
        shapeRows.add(tail);
        GroupUtils.addGroup(chain, shapeRows);

        android.os.Parcelable state = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (state != null && mRv.getLayoutManager() != null) mRv.getLayoutManager().onRestoreInstanceState(state);
    }

    private SwitchWidgetAdapter.SwitchItem borderSwitch() {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.msg_border_title), getString(R.string.msg_border_summary),
                ObsidianPrefs.getBoolean(PREF_BORDER_ON, true), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BORDER_ON, item.checked);
            applyProps();
            rebuild();
        };
        return item;
    }

    private String borderLabel(String modeKey, String colorKey, int defMode, int defColor) {
        if (ObsidianPrefs.getInt(modeKey, defMode) == 0) return getString(R.string.msg_border_accent);
        return String.format("#%06X", ObsidianPrefs.getInt(colorKey, defColor) & 0xFFFFFF);
    }

    private void showBorderDialog(String modeKey, String colorKey, int dialogId, int defMode, int defColor, int titleRes) {
        String[] entries = {getString(R.string.msg_border_accent), getString(R.string.msg_border_custom)};
        final int[] sel = {ObsidianPrefs.getInt(modeKey, defMode)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (sel[0] == 0) {
                        ObsidianPrefs.putInt(modeKey, 0);
                        applyProps();
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId,
                                ObsidianPrefs.getInt(colorKey, defColor) | 0xFF000000, false, true, true);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    private String outLabel() {
        String[] e = getResources().getStringArray(R.array.msg_out_entries);
        int m = ObsidianPrefs.getInt(PREF_OUT_MODE, 2);
        return e[Math.max(0, Math.min(e.length - 1, m))];
    }

    private void showOutDialog() {
        String[] entries = getResources().getStringArray(R.array.msg_out_entries);
        final int[] sel = {ObsidianPrefs.getInt(PREF_OUT_MODE, 2)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.msg_out_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_OUT_MODE, sel[0]);
                    applyProps();
                    rebuild();
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    /** Scrive le scelte come proprieta' di sistema (le legge la mod dentro l'app Messaggi). */
    private static void applyProps() {
        final int out = ObsidianPrefs.getInt(PREF_OUT_MODE, 2);
        final int border = ObsidianPrefs.getBoolean(PREF_BORDER_ON, true) ? ObsidianPrefs.getInt(PREF_BORDER_W, 2) : 0;
        final int tail = ObsidianPrefs.getBoolean(PREF_TAIL, true) ? 1 : 0;
        final String bIn = ObsidianPrefs.getInt(PREF_BORDER_IN_MODE, 1) == 0 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_BORDER_IN_COLOR, 0xFFFFFFFF) | 0xFF000000);
        final String bOut = ObsidianPrefs.getInt(PREF_BORDER_OUT_MODE, 0) == 0 ? "accent"
                : String.valueOf(ObsidianPrefs.getInt(PREF_BORDER_OUT_COLOR, 0xFF908DFF) | 0xFF000000);
        new Thread(() -> Shell.cmd(
                "setprop persist.obsidian.msg_out_dark " + out,
                "setprop persist.obsidian.msg_border " + border,
                "setprop persist.obsidian.msg_tail " + tail,
                "setprop persist.obsidian.msg_border_in " + bIn,
                "setprop persist.obsidian.msg_border_out " + bOut).exec()).start();
    }
}
