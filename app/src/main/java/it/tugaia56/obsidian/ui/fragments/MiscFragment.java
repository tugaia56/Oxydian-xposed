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

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Misc hub — mirrors OC's "Varie" section (only the 4 items requested):
 *   1. Remove floating rotation button
 *   2. Remove USB dialog window
 *   3. Power menu (full)
 *   4. Show entry in Settings
 */
public class MiscFragment extends Fragment {

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

        SwitchWidgetAdapter.SwitchItem rotationItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_misc_rotation_btn),
                getString(R.string.nav_misc_rotation_btn_summary),
                ObsidianPrefs.getBoolean("misc_remove_rotate_floating", false),
                null);
        rotationItem.onChanged = () ->
                ObsidianPrefs.putBoolean("misc_remove_rotate_floating", rotationItem.checked);

        SwitchWidgetAdapter.SwitchItem usbItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_misc_usb_dialog),
                getString(R.string.nav_misc_usb_dialog_summary),
                ObsidianPrefs.getBoolean("remove_usb_dialog", false),
                null);
        usbItem.onChanged = () ->
                ObsidianPrefs.putBoolean("remove_usb_dialog", usbItem.checked);

        SwitchWidgetAdapter.SwitchItem settingsEntryItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_misc_settings_entry),
                getString(R.string.nav_misc_settings_entry_summary),
                ObsidianPrefs.getBoolean("show_entry_settings", false),
                null);
        settingsEntryItem.onChanged = () ->
                ObsidianPrefs.putBoolean("show_entry_settings", settingsEntryItem.checked);

        SwitchWidgetAdapter.SwitchItem screenshotItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_screenshot_enabler),
                getString(R.string.nav_screenshot_enabler_summary),
                ObsidianPrefs.getBoolean("DST_SCREENSHOT_ENABLER_ON", false),
                null);
        screenshotItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("DST_SCREENSHOT_ENABLER_ON", screenshotItem.checked);
            new Thread(() -> {
                it.tugaia56.obsidian.utils.DstFabricatedUtil.saveBootProps();
                requireActivity().runOnUiThread(() ->
                        it.tugaia56.obsidian.utils.AppUtils.showRebootReminder(requireContext()));
            }).start();
        };

        SwitchWidgetAdapter togglesAdapter = new SwitchWidgetAdapter(
                List.of(rotationItem, usbItem, settingsEntryItem, screenshotItem));

        List<NavAdapter.NavItem> items = List.of(

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_misc_power_menu),
                        getString(R.string.nav_misc_power_menu_summary),
                        () -> navigate(new PowerMenuFragment(),
                                getString(R.string.nav_misc_power_menu))),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_corepatch),
                        getString(R.string.nav_corepatch_summary),
                        () -> navigate(new CorePatchFragment(),
                                getString(R.string.nav_corepatch))),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_lucky_extras),
                        getString(R.string.nav_lucky_extras_summary),
                        () -> navigate(new LuckyExtrasFragment(),
                                getString(R.string.nav_lucky_extras)))
        );
        rv.setAdapter(new ConcatAdapter(togglesAdapter, new NavAdapter(items, 0xFFFF6E40))); // deep orange accent, colore categoria "Varie"
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
