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

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
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

    private RecyclerView mRv;

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
        new Thread(() -> Shell.cmd(
                "setprop persist.obsidian.msg_out_dark " + out,
                "setprop persist.obsidian.msg_border " + border,
                "setprop persist.obsidian.msg_tail " + tail).exec()).start();
    }
}
