package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.utils.DarkShadowUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.FabricatedUtil;

/**
 * Stile Barra di Navigazione — Navigazione con Gesture, Pack Icone Barra di Navigazione
 * (overlay reale su com.android.systemui, vedi NavbarIconsFragment/NavbarIconsCompiler), e
 * Colore Icone Barra di Navigazione: overlay fabbricato per pillola gesture/handle (qui) +
 * hook Xposed diretto (NavbarIconColorMod) per i tre tasti Indietro/Home/Recenti, perché
 * quei tasti leggono risorse condivise con la barra di stato che non si possono sovrascrivere
 * via overlay senza colorare anche quella.
 */
public class NavbarStyleFragment extends Fragment {

    private static final String NAVCOLOR_OVERLAY = "NAVCOLOR";
    private static final String[] NAVCOLOR_NAMES = {
        "NAVCOLOR_0", "NAVCOLOR_1", "NAVCOLOR_2",
    };

    private final List<DarkShadowItem> navColorItems = new ArrayList<>();
    private DarkShadowColorListener navColorAdapter;
    private int mPendingDialogId = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
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
        RecyclerView rv = (RecyclerView) view;

        ListWidgetAdapter.ListItem gestureNav = new ListWidgetAdapter.ListItem(
                getString(R.string.gesture_navigation_title), getString(R.string.gesture_navigation_summary),
                () -> navigate(new GestureNavigationFragment(), getString(R.string.gesture_navigation_title)));

        ListWidgetAdapter.ListItem navbarIcons = new ListWidgetAdapter.ListItem(
                getString(R.string.navbar_icons_title), getString(R.string.navbar_icons_summary),
                () -> it.tugaia56.obsidian.utils.OxydianThemeLauncher.open(requireContext()));

        ListWidgetAdapter topRows = new ListWidgetAdapter(List.of(gestureNav, navbarIcons));

        navColorItems.clear();
        int navColor = ObsidianPrefs.getInt(DarkShadowUtils.PREF_PREFIX + NAVCOLOR_OVERLAY, 0xFFFFFFFF);
        boolean navOn = ObsidianPrefs.getBoolean(DarkShadowUtils.PREF_PREFIX + NAVCOLOR_OVERLAY + "_on", false);
        navColorItems.add(new DarkShadowItem(
                getString(R.string.navcolor_title),
                NAVCOLOR_OVERLAY,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                navColor, navOn
        ));
        navColorAdapter = new DarkShadowColorListener(
                navColorItems, this::onNavColorEnabled, this::onNavColorDisabled, this::onNavColorSwatch);

        rv.setAdapter(new ConcatAdapter(
                topRows,
                new SectionTitleAdapter(List.of(getString(R.string.navcolor_title))),
                navColorAdapter
        ));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }

    // ── Nav bar icon color callbacks ────────────────────────────────────────────

    private void onNavColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        DarkShadowUtils.saveColor(item);
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> applyNavColors(item.getColor(), ctx)).start();
    }

    private void onNavColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(DarkShadowUtils.PREF_PREFIX + NAVCOLOR_OVERLAY + "_on", false);
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> disableNavColors(ctx)).start();
    }

    private void onNavColorSwatch(DarkShadowItem item, int dialogId) {
        mPendingDialogId = dialogId;
        String colorKey = DarkShadowUtils.PREF_PREFIX + NAVCOLOR_OVERLAY;
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(colorKey + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(colorKey + "_use_accent", useAccent);
                    if (useAccent) {
                        item.setColor(ObsidianTheme.accentColor());
                        onNavColorEnabled(item);
                        if (navColorAdapter != null) navColorAdapter.notifyDataSetChanged();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() != mPendingDialogId) return;
        mPendingDialogId = -1;

        DarkShadowItem item = navColorItems.get(0);
        item.setColor(event.color());
        ObsidianPrefs.putBoolean(DarkShadowUtils.PREF_PREFIX + item.getOverlayName() + "_use_accent", false); // picking a colour implies custom
        ObsidianPrefs.putInt(DarkShadowUtils.PREF_PREFIX + item.getOverlayName(), event.color());
        if (navColorAdapter != null) navColorAdapter.notifyDataSetChanged();
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> applyNavColors(event.color(), ctx)).start();
    }

    private void applyNavColors(int color, Context ctx) {
        String hex = String.format("0x%08X", 0xFFFFFFFFL & color);
        String sui = Constants.SYSTEM_UI;
        // I tasti Indietro/Home/Recenti NON sono coperti da questo overlay: leggono
        // ?attr/singleToneColor, che risolve su dark|light_mode_icon_color_single_tone —
        // le stesse due risorse lette ANCHE dalla barra di stato (overlay su di esse
        // coloriva pure quella). Per quei tre tasti c'è invece NavbarIconColorMod, un hook
        // Xposed diretto su NavigationBarView.mLightIconColor/mDarkIconColor — scope
        // solo-navbar, live, nessun riavvio. Qui restano solo home handle (pillola gesture)
        // e navigation_bar_icon_color, nomi specifici della navbar, sicuri da sovrascrivere.
        FabricatedUtil.buildAndEnableOverlays(
            new Object[]{sui, "NAVCOLOR_0", "color", "navigation_bar_home_handle_dark_color",  hex},
            new Object[]{sui, "NAVCOLOR_1", "color", "navigation_bar_home_handle_light_color",  hex},
            new Object[]{sui, "NAVCOLOR_2", "color", "navigation_bar_icon_color",                hex}
        );
        AppUtils.showRestartReminder(ctx);
    }

    private void disableNavColors(Context ctx) {
        FabricatedUtil.disableOverlays(NAVCOLOR_NAMES);
        AppUtils.showRestartReminder(ctx);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
