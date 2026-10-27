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
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Bordo sfumato (accento + 1 o 2 colori scelti) per dialog, popup, notifiche, toast e finestre.
 * La scelta e' salvata qui e scritta come proprieta' di sistema, che
 * {@link it.tugaia56.obsidian.xposed.utils.GradientBorder} legge in ogni processo.
 */
public class GradientBorderFragment extends Fragment {

    private static final String KEY_ON = "grad_border_on";
    private static final String KEY_W = "grad_border_w";   // mezzi dp, di serie 3 = 1,5 dp
    private static final int DEF_W = 3;
    private static final String KEY_C1 = "grad_border_c1";
    private static final String KEY_C1_ON = "grad_border_c1_custom";
    private static final String KEY_C2 = "grad_border_c2";
    private static final String KEY_C3 = "grad_border_c3";
    private static final String KEY_C3_ON = "grad_border_c3_on";
    private static final int DEF_C2 = 0xFFFF7AA8;
    private static final int DEF_C3 = 0xFF4DD0E1;
    private static final int DIALOG_C1 = KEY_C1.hashCode();
    private static final int DIALOG_C2 = KEY_C2.hashCode();
    private static final int DIALOG_C3 = KEY_C3.hashCode();

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
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_note))));

        SwitchWidgetAdapter.SwitchItem on = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.gb_title), getString(R.string.gb_summary),
                ObsidianPrefs.getBoolean(KEY_ON, false), null);
        on.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ON, on.checked);
            applyProps();
        };
        chain.add(new SwitchWidgetAdapter(List.of(on)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_colors))));
        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c1),
                ObsidianPrefs.getBoolean(KEY_C1_ON, false) ? hex(c1()) : getString(R.string.gb_c1_summary), this::showC1Dialog));
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c2), hex(c2()), this::pickC2));
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c3),
                ObsidianPrefs.getBoolean(KEY_C3_ON, true) ? hex(c3()) : getString(R.string.gb_c3_none), this::showC3Dialog));
        GroupUtils.addGroup(chain, rows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_thickness_section))));
        GroupUtils.addGroup(chain, List.of(new it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter.SliderItem(
                getString(R.string.gb_thickness), ObsidianPrefs.getInt(KEY_W, DEF_W), 1, 8, "", DEF_W,
                value -> { ObsidianPrefs.putInt(KEY_W, value); applyProps(); })));

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private static int c1() { return ObsidianPrefs.getInt(KEY_C1, 0xFF908DFF) | 0xFF000000; }
    private static int c2() { return ObsidianPrefs.getInt(KEY_C2, DEF_C2) | 0xFF000000; }
    private static int c3() { return ObsidianPrefs.getInt(KEY_C3, DEF_C3) | 0xFF000000; }
    private static String hex(int c) { return String.format("#%06X", 0xFFFFFF & c); }

    private void showC1Dialog() {
        String[] entries = {getString(R.string.gb_c1_accent), getString(R.string.gb_c3_pick)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gb_c1)
                .setItems(entries, (d, which) -> {
                    if (which == 0) {
                        ObsidianPrefs.putBoolean(KEY_C1_ON, false);
                        applyProps();
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_C1, c1(), false, true, true);
                    }
                })
                .show());
    }

    private void pickC2() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_C2, c2(), false, true, true);
        }
    }

    private void showC3Dialog() {
        String[] entries = {getString(R.string.gb_c3_pick), getString(R.string.gb_c3_none)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gb_c3)
                .setItems(entries, (d, which) -> {
                    if (which == 0) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_C3, c3(), false, true, true);
                        }
                    } else {
                        ObsidianPrefs.putBoolean(KEY_C3_ON, false);
                        applyProps();
                        rebuild();
                    }
                })
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent e) {
        if (e.dialogId() == DIALOG_C1) {
            ObsidianPrefs.putInt(KEY_C1, e.color() | 0xFF000000);
            ObsidianPrefs.putBoolean(KEY_C1_ON, true);
        } else if (e.dialogId() == DIALOG_C2) {
            ObsidianPrefs.putInt(KEY_C2, e.color() | 0xFF000000);
        } else if (e.dialogId() == DIALOG_C3) {
            ObsidianPrefs.putInt(KEY_C3, e.color() | 0xFF000000);
            ObsidianPrefs.putBoolean(KEY_C3_ON, true);
        } else {
            return;
        }
        applyProps();
        rebuild();
    }

    /** Scrive le proprieta' di sistema lette da GradientBorder (le app non vedono le preferenze di Oxydian). */
    private static void applyProps() {
        final boolean on = ObsidianPrefs.getBoolean(KEY_ON, false);
        final boolean c1on = ObsidianPrefs.getBoolean(KEY_C1_ON, false);
        final int c1 = c1();
        final int c2 = c2();
        final boolean c3on = ObsidianPrefs.getBoolean(KEY_C3_ON, true);
        final int c3 = c3();
        new Thread(() -> Shell.cmd(
                "setprop persist.obsidian.grad_on " + (on ? "1" : "0"),
                "setprop persist.obsidian.grad_c1 " + (c1on ? String.valueOf(c1) : "''"),
                "setprop persist.obsidian.grad_w " + ObsidianPrefs.getInt(KEY_W, DEF_W),
                "setprop persist.obsidian.grad_c2 " + c2,
                "setprop persist.obsidian.grad_c3 " + (c3on ? String.valueOf(c3) : "''")).exec()).start();
    }
}
