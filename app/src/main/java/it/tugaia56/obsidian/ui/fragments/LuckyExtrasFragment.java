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
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * "Extra OOS" — mod system_server/app (reboot richiesto) portate da LuckyTool (github.com/
 * luckyzyx/LuckyTool, per ColorOS — stesso codebase di OOS). Tolte perché già native in OOS:
 * "Mostra RAM nei Recenti"/"Layout Recenti impilato" (Impostazioni → Gestione attività recenti,
 * 2026-09-02), "Nascondi notifica VPN attiva" (Impostazioni → Notifiche e Impostazioni rapide →
 * Barra di stato → VPN, 2026-09-03) — entrambe confermate dall'utente via screenshot.
 * "Supporto App 32-bit Forzato" tolta 2026-09-03: device senza runtime 32-bit, inutile qui.
 */
public class LuckyExtrasFragment extends Fragment {

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
        mRv = rv;
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rebuild();
    }

    private void rebuild() {
        SwitchWidgetAdapter.SwitchItem adbItem = simpleSysItem(
                R.string.lucky_adb_confirm, R.string.lucky_adb_confirm_summary, "DST_LUCKY_ADB_NO_CONFIRM");
        SwitchWidgetAdapter.SwitchItem powerItem = simpleSysItem(
                R.string.lucky_power_menu, R.string.lucky_power_menu_summary, "DST_LUCKY_FAST_POWER_MENU");
        SwitchWidgetAdapter.SwitchItem flashlightItem = simpleSysItem(
                R.string.lucky_volume_flashlight, R.string.lucky_volume_flashlight_summary, "DST_LUCKY_VOLUME_FLASHLIGHT");
        SwitchWidgetAdapter.SwitchItem multiAppItem = simpleSysItem(
                R.string.lucky_multiapp, R.string.lucky_multiapp_summary, "DST_LUCKY_MULTIAPP_NO_BLACKLIST");
        SwitchWidgetAdapter.SwitchItem pngShotItem = simpleSysItem(
                R.string.lucky_png_screenshot, R.string.lucky_png_screenshot_summary, "DST_LUCKY_PNG_SCREENSHOT");
        SwitchWidgetAdapter.SwitchItem longshotItem = simpleSysItem(
                R.string.lucky_longshot_limit, R.string.lucky_longshot_limit_summary, "DST_LUCKY_LONGSHOT_NO_LIMIT");

        SwitchWidgetAdapter sysAdapter = new SwitchWidgetAdapter(List.of(
                adbItem, powerItem, flashlightItem, multiAppItem,
                pngShotItem, longshotItem));

        mRv.setAdapter(new ConcatAdapter(sysAdapter));
    }

    private SwitchWidgetAdapter.SwitchItem simpleSysItem(int titleRes, int summaryRes, String prefKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                getString(titleRes), getString(summaryRes),
                ObsidianPrefs.getBoolean(prefKey, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(prefKey, item.checked);
            new Thread(() -> {
                DstFabricatedUtil.saveBootProps();
                requireActivity().runOnUiThread(() -> AppUtils.showRebootReminder(requireContext()));
            }).start();
        };
        return item;
    }
}
