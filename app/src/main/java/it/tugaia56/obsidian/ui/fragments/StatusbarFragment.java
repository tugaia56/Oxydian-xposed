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

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Status Bar sub-screen:
 *  – NavItems: Orologio, Notifiche
 *  – Switch inline: BT, DT-sleep, Luminosità
 *
 * Icone Batteria e Stile Icone Segnale sono stati spostati al livello Home DST
 * (vedi DstTabFragment → "Stile icone Segnale" / SignalStyleFragment).
 */
public class StatusbarFragment extends Fragment {

    private final List<NavAdapter.NavItem> mNavItems = new ArrayList<>();
    private NavAdapter mNavAdapter;

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
        RecyclerView rv = (RecyclerView) view;

        mNavItems.clear();

        // 1. Orologio & Calendario
        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_clock,
                getString(R.string.section_clock_date),
                getString(R.string.nav_clock_date_summary),
                () -> navigate(new ClockDateFragment(),
                        getString(R.string.section_clock_date))));

        // 2. Notifiche
        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_notifications,
                getString(R.string.section_statusbar_notifs),
                getString(R.string.section_statusbar_notifs_summary),
                () -> navigate(new StatusbarNotifsFragment(),
                        getString(R.string.section_statusbar_notifs))));

        mNavAdapter = new NavAdapter(mNavItems, 0xFF7C4DFF); // purple, colore categoria "Barra di Stato"

        // ── Icon switches inline ──────────────────────────────────────────────
        SectionTitleAdapter iconSection = new SectionTitleAdapter(
                List.of(getString(R.string.section_statusbar_icons)));
        SwitchWidgetAdapter iconSwitches = new SwitchWidgetAdapter(buildIconSwitches());

        rv.setAdapter(new ConcatAdapter(mNavAdapter, iconSection, iconSwitches));
    }

    // ── Icon switch items ─────────────────────────────────────────────────────

    private List<SwitchWidgetAdapter.SwitchItem> buildIconSwitches() {
        SwitchWidgetAdapter.SwitchItem s1 = makePrefSwitch(
                getString(R.string.hide_bluetooth_disconnected),
                getString(R.string.hide_bluetooth_disconnected_summary),
                "hide_bluetooth_when_disconnected");
        SwitchWidgetAdapter.SwitchItem s2 = makePrefSwitch(
                getString(R.string.double_tap_sleep_statusbar),
                getString(R.string.double_tap_sleep_statusbar_summary),
                "double_tap_sleep_statusbar");
        SwitchWidgetAdapter.SwitchItem s3 = makePrefSwitch(
                getString(R.string.statusbar_brightness),
                getString(R.string.statusbar_brightness_summary),
                "statusbar_brightness");
        SwitchWidgetAdapter.SwitchItem s4 = makePrefSwitch(
                getString(R.string.block_clipboard_overlay),
                getString(R.string.block_clipboard_overlay_summary),
                "block_clipboard_overlay");
        SwitchWidgetAdapter.SwitchItem s5 = makePrefSwitch(
                getString(R.string.hide_sim1_icon),
                getString(R.string.hide_sim_icon_summary),
                "OBS_STATUSBAR_HIDE_SIM1");
        SwitchWidgetAdapter.SwitchItem s6 = makePrefSwitch(
                getString(R.string.hide_sim2_icon),
                getString(R.string.hide_sim_icon_summary),
                "OBS_STATUSBAR_HIDE_SIM2");
        s1.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.TOP;
        s2.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        s3.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        s4.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        s5.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        s6.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.BOTTOM;
        return List.of(s1, s2, s3, s4, s5, s6);
    }

    private SwitchWidgetAdapter.SwitchItem makePrefSwitch(String title, String summary,
                                                           String prefKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary,
                ObsidianPrefs.getBoolean(prefKey, false),
                null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(prefKey, item.checked);
        return item;
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
