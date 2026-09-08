package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Core Patch — porting dei 3 toggle a basso rischio dal modulo "Core Patch" reale (com.coderstory
 * toolkit, versione 4.9 fornita dall'utente 2026-09-02, decompilata con jadx). Gli altri toggle
 * dello screenshot originale (verifica digest, confronto firme, firme installate, verifica utente
 * condiviso) toccano la fusione manuale delle catene di firma tra app con UID condiviso — logica
 * delicata, lasciata volutamente fuori: l'app Core Patch originale resta la scelta giusta per
 * quelle, se mai servissero.
 */
public class CorePatchFragment extends Fragment {

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

        SwitchWidgetAdapter.SwitchItem downgradeItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.corepatch_downgrade),
                getString(R.string.corepatch_downgrade_summary),
                ObsidianPrefs.getBoolean("DST_COREPATCH_DOWNGRADE", true),
                null);
        downgradeItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("DST_COREPATCH_DOWNGRADE", downgradeItem.checked);
            applyAndRemind();
        };

        SwitchWidgetAdapter.SwitchItem bypassBlockItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.corepatch_bypass_block),
                getString(R.string.corepatch_bypass_block_summary),
                ObsidianPrefs.getBoolean("DST_COREPATCH_BYPASS_BLOCK", true),
                null);
        bypassBlockItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("DST_COREPATCH_BYPASS_BLOCK", bypassBlockItem.checked);
            applyAndRemind();
        };

        SwitchWidgetAdapter.SwitchItem disableVerifyItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.corepatch_disable_verify),
                getString(R.string.corepatch_disable_verify_summary),
                ObsidianPrefs.getBoolean("DST_COREPATCH_DISABLE_VERIFY", true),
                null);
        disableVerifyItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("DST_COREPATCH_DISABLE_VERIFY", disableVerifyItem.checked);
            applyAndRemind();
        };

        rv.setAdapter(new SwitchWidgetAdapter(
                List.of(downgradeItem, bypassBlockItem, disableVerifyItem)));
    }

    private void applyAndRemind() {
        new Thread(() -> {
            DstFabricatedUtil.saveBootProps();
            requireActivity().runOnUiThread(() ->
                    it.tugaia56.obsidian.utils.AppUtils.showRebootReminder(requireContext()));
        }).start();
    }
}
