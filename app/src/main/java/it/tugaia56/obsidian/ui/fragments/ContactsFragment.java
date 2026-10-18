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
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Google Contatti: card dell'elenco con o senza bordo. L'app Contatti non vede le preferenze di Oxydian,
 * quindi la scelta (salvata qui) viene anche scritta come proprieta' di sistema, che la mod
 * {@link it.tugaia56.obsidian.xposed.hooks.contacts.ContactsMod} legge. Serve la spunta su Contatti
 * nell'ambito di Oxydian in LSPosed.
 */
public class ContactsFragment extends Fragment {

    private static final String PREF_BORDER = "ct_card_border";   // false = predefinito (senza bordo), true = con bordo

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
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.ct_note))));
        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.ct_card_title), cardLabel(), this::showCardDialog));
        GroupUtils.addGroup(chain, rows);
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private String cardLabel() {
        return getString(ObsidianPrefs.getBoolean(PREF_BORDER, false) ? R.string.ct_card_border : R.string.ct_card_default);
    }

    private void showCardDialog() {
        String[] entries = {getString(R.string.ct_card_default), getString(R.string.ct_card_border)};
        final int[] sel = {ObsidianPrefs.getBoolean(PREF_BORDER, false) ? 1 : 0};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ct_card_title)
                .setSingleChoiceItems(entries, sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putBoolean(PREF_BORDER, sel[0] == 1);
                    applyProps();
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private static void applyProps() {
        final String v = ObsidianPrefs.getBoolean(PREF_BORDER, false) ? "1" : "0";
        new Thread(() -> Shell.cmd("setprop persist.obsidian.ct_border " + v).exec()).start();
    }
}
