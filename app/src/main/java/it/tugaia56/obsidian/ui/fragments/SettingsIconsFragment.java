package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.OverlayUtil;
import it.tugaia56.obsidian.utils.overlay.manager.SettingsIconsResourceManager;

/**
 * Stile Icone Impostazioni — porting reale di OC: compila due APK overlay (per
 * com.android.settings e per Obsidian stesso) via aapt2/zipalign/firma custom e li installa
 * via root come overlay di partizione (/system/product/overlay + modulo Magisk per la
 * persistenza — niente "pm install", che su un dispositivo retail fallisce sempre per firma
 * non corrispondente). Essendo un overlay di partizione serve un riavvio prima che
 * PackageManagerService lo scansioni e "Abilita" possa attivarlo davvero. La personalizzazione
 * avanzata di colore/forma di OC non è stata portata: si applicano valori di default.
 */
public class SettingsIconsFragment extends Fragment {

    private static final String TAG = "SettingsIconsFragment";
    private static final String PREFIX = "ObsidianComponent";
    private static final String KEY_SELECTED_SET = "settings_icons_selected_set";
    private static final String KEY_PENDING_REBOOT = "settings_icons_pending_reboot";
    private static final String KEY_BG_COLOR = "settings_icons_bg_color";
    private static final String KEY_BG_SHAPE = "settings_icons_bg_shape";
    private static final String KEY_BG_SOLID = "settings_icons_bg_solid";
    private static final String KEY_ICON_COLOR = "settings_icons_icon_color";

    /** HOS (iconSet 3) e OOS (iconSet 5) usano le 4 opzioni sfondo/icona — i pack PUI e
     *  OOS Stock (iconSet 6, forma+colore reali, niente sfondo) le ignorano. */
    private static final int HOS_ICON_SET = 3;
    private static final int OOS_ICON_SET = 5;
    private static final int STOCK_ICON_SET = 6;

    private record Pack(String title, int iconSet, int wifi, int wallpaper, int battery, int about) {}

    private final List<Pack> mPacks = List.of(
            new Pack("PUI v1", 1, R.drawable.settings_wifi_pui_v1, R.drawable.settings_wallpaper_pui_v1,
                    R.drawable.settings_battery_pui_v1, R.drawable.settings_about_pui_v1),
            new Pack("PUI v2", 2, R.drawable.settings_wifi_pui_v2, R.drawable.settings_wallpaper_pui_v2,
                    R.drawable.settings_battery_pui_v2, R.drawable.settings_about_pui_v2),
            new Pack("PUI v3", 4, R.drawable.settings_wifi_pui_v3, R.drawable.settings_wallpaper_pui_v3,
                    R.drawable.settings_battery_pui_v3, R.drawable.settings_about_pui_v3),
            new Pack("HOS", 3, R.drawable.settings_wifi_hos, R.drawable.settings_wallpaper_hos,
                    R.drawable.settings_battery_hos, R.drawable.settings_about_hos),
            // OOS: sfondo personalizzabile come HOS ma icone reali estratte dal vero Settings.apk
            // di questo dispositivo (forma originale, colore da "Colore Icona"). Anteprima con
            // le vere icone estratte, colori reali (niente tinta forzata viola in PackAdapter).
            new Pack("OOS", OOS_ICON_SET, R.drawable.settings_wifi_oos_real, R.drawable.settings_wallpaper_oos_real,
                    R.drawable.settings_battery_oos_real, R.drawable.settings_about_oos_real),
            // OOS Stock: stesse icone reali estratte, ma colore originale invariato e niente
            // sfondo — nessuna opzione.
            new Pack("OOS Stock", STOCK_ICON_SET, R.drawable.settings_wifi_oos_real, R.drawable.settings_wallpaper_oos_real,
                    R.drawable.settings_battery_oos_real, R.drawable.settings_about_oos_real)
    );

    private int mSelected = -1;
    private int mAppliedSet = -1;
    private boolean mPendingReboot = false;
    private boolean mActive = false;
    private boolean mBusy = false;
    private int mBgColor = 0;
    private int mBgShape = 0;
    private boolean mBgSolid = false;
    private int mIconColor = 0;
    private RecyclerView mRv;
    private ButtonsAdapter mButtonsAdapter;

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

        mAppliedSet = ObsidianPrefs.getInt(KEY_SELECTED_SET, -1);
        mPendingReboot = ObsidianPrefs.getBoolean(KEY_PENDING_REBOOT, false);
        mActive = mAppliedSet >= 0 && OverlayUtil.isOverlayEnabled(PREFIX + "SIP1.overlay");
        if (mActive) mPendingReboot = false;
        if (mAppliedSet >= 0) {
            for (int i = 0; i < mPacks.size(); i++) {
                if (mPacks.get(i).iconSet() == mAppliedSet) { mSelected = i; break; }
            }
        }

        mBgColor = ObsidianPrefs.getInt(KEY_BG_COLOR, 0);
        mBgShape = ObsidianPrefs.getInt(KEY_BG_SHAPE, 0);
        mBgSolid = ObsidianPrefs.getBoolean(KEY_BG_SOLID, false);
        mIconColor = ObsidianPrefs.getInt(KEY_ICON_COLOR, 0);

        rebuild();
    }

    private boolean packHasOptions(int idx) {
        if (idx < 0) return false;
        int set = mPacks.get(idx).iconSet();
        return set == HOS_ICON_SET || set == OOS_ICON_SET;
    }

    private void rebuild() {
        rebuild(false);
    }

    private void rebuild(boolean scrollToSelection) {
        mButtonsAdapter = new ButtonsAdapter();

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(new NoticeAdapter());
        int scrollTargetPos = -1;

        if (mSelected < 0) {
            // Niente selezionato: lista intera, pulsanti in fondo (disabilitati finché non
            // si sceglie un pack).
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < mPacks.size(); i++) all.add(i);
            chain.add(new PackAdapter(all));
            chain.add(mButtonsAdapter);
        } else {
            // Le opzioni (se il pack le usa) vanno subito sopra la card selezionata, e
            // Applica/Disabilita subito sotto — non più fissi in fondo alla lista.
            List<Integer> before = new ArrayList<>();
            for (int i = 0; i < mSelected; i++) before.add(i);
            List<Integer> after = new ArrayList<>();
            for (int i = mSelected + 1; i < mPacks.size(); i++) after.add(i);

            int runningCount = 1; // NoticeAdapter
            if (!before.isEmpty()) {
                chain.add(new PackAdapter(before));
                runningCount += before.size();
            }
            // Lo scroll punta all'inizio delle opzioni (se presenti) o direttamente alla
            // card selezionata, cioè al primo elemento nuovo che comparirebbe fuori
            // dall'attuale vista dopo il tap.
            scrollTargetPos = runningCount;
            if (packHasOptions(mSelected)) {
                List<RecyclerView.Adapter<?>> options = buildOptionAdapters();
                chain.addAll(options);
                runningCount += options.size();
            }
            chain.add(new PackAdapter(List.of(mSelected)));
            runningCount += 1;
            chain.add(mButtonsAdapter);
            runningCount += 1;
            if (!after.isEmpty()) chain.add(new PackAdapter(after));
        }

        mRv.setAdapter(new ConcatAdapter(chain));

        if (scrollToSelection && scrollTargetPos >= 0) {
            RecyclerView.LayoutManager lm = mRv.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                ((LinearLayoutManager) lm).scrollToPositionWithOffset(scrollTargetPos, dp(8));
            }
        }
    }

    private void onPackTapped(int pos) {
        if (mBusy) return;
        mSelected = (mSelected == pos) ? -1 : pos;
        rebuild(true);
    }

    private void onApplyClicked() {
        if (mBusy || mSelected < 0) return;

        if (!AppUtils.hasStoragePermission()) {
            AppUtils.requestStoragePermission(requireContext());
            return;
        }

        setBusy(true);
        Pack pack = mPacks.get(mSelected);
        new Thread(() -> {
            boolean erroredOut;
            try {
                erroredOut = SettingsIconsResourceManager.buildOverlay(
                        pack.iconSet(), mBgColor, mBgShape, mBgSolid, mIconColor);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                erroredOut = true;
            }
            boolean success = !erroredOut;
            // enableOverlays (dentro buildOverlay) è ora bloccante, quindi qui lo stato reale
            // è già consultabile: se il pacchetto overlay era già noto a PMS da un riavvio
            // precedente, "Applica" per un pack diverso lo attiva subito, senza bisogno di
            // un altro riavvio.
            boolean nowActive = success && OverlayUtil.isOverlayEnabled(PREFIX + "SIP1.overlay");
            if (success) {
                ObsidianPrefs.putInt(KEY_SELECTED_SET, pack.iconSet());
                ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, !nowActive);
            }
            boolean finalSuccess = success;
            boolean finalActive = nowActive;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                setBusy(false);
                if (finalSuccess) {
                    mAppliedSet = pack.iconSet();
                    mActive = finalActive;
                    mPendingReboot = !finalActive;
                    rebuild();
                    Toast.makeText(requireContext(),
                            finalActive ? R.string.toast_applied : R.string.settings_icons_build_ok,
                            finalActive ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), R.string.toast_error, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void onDisableClicked() {
        if (mBusy || mAppliedSet < 0) return;

        setBusy(true);
        new Thread(() -> {
            OverlayUtil.disableOverlays(PREFIX + "SIP1.overlay", PREFIX + "SIP2.overlay");
            ObsidianPrefs.putInt(KEY_SELECTED_SET, -1);
            ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, false);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                setBusy(false);
                mAppliedSet = -1;
                mPendingReboot = false;
                mActive = false;
                mSelected = -1;
                rebuild();
                Toast.makeText(requireContext(), R.string.toast_applied, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        rebuild();
    }

    // ── Notice row ───────────────────────────────────────────────────────────

    private class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(requireContext());
            tv.setText(R.string.settings_icons_preview_notice);
            tv.setTextColor(0x99FFFFFF);
            tv.setTextSize(13);
            tv.setGravity(Gravity.START);
            int padH = dp(16);
            tv.setPadding(padH, dp(4), padH, dp(12));
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {}
        @Override public int getItemCount() { return 1; }
    }

    // ── Opzioni pack HOS/OOS (colore/forma sfondo, colore icona) ───────────────

    private List<RecyclerView.Adapter<?>> buildOptionAdapters() {
        String[] colorEntries = getResources().getStringArray(R.array.settings_icons_color_entries);
        String[] shapeEntries = getResources().getStringArray(R.array.settings_icons_shape_entries);
        // Nota: per OOS il colore di ogni icona è "cotto" nel path estratto da Settings.apk,
        // quindi questa opzione potrebbe non avere alcun effetto visibile lì — mostrata comunque
        // per coerenza con HOS.
        boolean showIconColor = true;

        ListWidgetAdapter.ListItem bgColorItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_icons_opt_bg_color), colorEntries[mBgColor],
                () -> showChoiceDialog(getString(R.string.settings_icons_opt_bg_color), colorEntries, mBgColor, choice -> {
                    mBgColor = choice;
                    ObsidianPrefs.putInt(KEY_BG_COLOR, choice);
                    rebuild();
                }));

        SwitchWidgetAdapter.SwitchItem solidItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.settings_icons_opt_bg_solid), null, mBgSolid, null);
        solidItem.onChanged = () -> {
            mBgSolid = solidItem.checked;
            ObsidianPrefs.putBoolean(KEY_BG_SOLID, mBgSolid);
        };

        ListWidgetAdapter.ListItem bgShapeItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_icons_opt_bg_shape), shapeEntries[mBgShape],
                () -> showChoiceDialog(getString(R.string.settings_icons_opt_bg_shape), shapeEntries, mBgShape, choice -> {
                    mBgShape = choice;
                    ObsidianPrefs.putInt(KEY_BG_SHAPE, choice);
                    rebuild();
                }));

        ListWidgetAdapter.ListItem iconColorItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_icons_opt_icon_color), colorEntries[mIconColor],
                () -> showChoiceDialog(getString(R.string.settings_icons_opt_icon_color), colorEntries, mIconColor, choice -> {
                    mIconColor = choice;
                    ObsidianPrefs.putInt(KEY_ICON_COLOR, choice);
                    rebuild();
                }));

        List<Object> rows = new ArrayList<>(List.of(bgColorItem, solidItem, bgShapeItem));
        if (showIconColor) rows.add(iconColorItem);
        List<RecyclerView.Adapter<?>> adapters = new ArrayList<>();
        GroupUtils.addGroup(adapters, rows);
        return adapters;
    }

    private interface ChoicePicked { void onPicked(int choice); }

    private void showChoiceDialog(String title, String[] entries, int current, ChoicePicked onPicked) {
        int[] selected = { current };
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> onPicked.onPicked(selected[0]))
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Pack cards ───────────────────────────────────────────────────────────

    private class PackAdapter extends RecyclerView.Adapter<PackAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView title;
            ImageView check;
            ImageView[] previews = new ImageView[4];
            FrameLayout[] cells = new FrameLayout[4];
            VH(View v) { super(v); }
        }

        private final List<Integer> indices;
        PackAdapter(List<Integer> indices) { this.indices = indices; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(dp(12), dp(5), dp(12), dp(5));
            card.setLayoutParams(cardLp);
            card.setRadius(dp(18));
            card.setCardElevation(0);
            card.setCardBackgroundColor(ObsidianTheme.cardColor());
            card.setStrokeColor(ObsidianTheme.textColor(0x33));
            card.setStrokeWidth(dp(1));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));

            LinearLayout titleCol = new LinearLayout(requireContext());
            titleCol.setOrientation(LinearLayout.HORIZONTAL);
            titleCol.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleCol.setLayoutParams(titleColLp);

            TextView title = new TextView(requireContext());
            title.setTextColor(ObsidianTheme.textColor());
            title.setTextSize(15);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            titleCol.addView(title);

            ImageView check = new ImageView(requireContext());
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            checkLp.setMarginStart(dp(8));
            check.setLayoutParams(checkLp);
            check.setImageDrawable(requireContext().getDrawable(android.R.drawable.checkbox_on_background));
            check.setImageTintList(ColorStateList.valueOf(0xFF7C4DFF));
            check.setVisibility(View.INVISIBLE);
            titleCol.addView(check);

            row.addView(titleCol);

            GridLayout grid = new GridLayout(requireContext());
            grid.setColumnCount(2);
            grid.setRowCount(2);
            ImageView[] previews = new ImageView[4];
            FrameLayout[] cells = new FrameLayout[4];
            for (int i = 0; i < 4; i++) {
                FrameLayout cell = new FrameLayout(requireContext());
                GridLayout.LayoutParams cellLp = new GridLayout.LayoutParams();
                cellLp.width = dp(36);
                cellLp.height = dp(36);
                cell.setLayoutParams(cellLp);

                ImageView iv = new ImageView(requireContext());
                FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(22), dp(22));
                ivLp.gravity = Gravity.CENTER;
                iv.setLayoutParams(ivLp);
                cell.addView(iv);
                grid.addView(cell);
                previews[i] = iv;
                cells[i] = cell;
            }
            row.addView(grid);

            card.addView(row);

            VH h = new VH(card);
            h.card = card;
            h.title = title;
            h.check = check;
            h.previews = previews;
            h.cells = cells;
            return h;
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            int idx = indices.get(pos);
            Pack pack = mPacks.get(idx);
            h.title.setText(pack.title());
            h.previews[0].setImageResource(pack.wifi());
            h.previews[1].setImageResource(pack.wallpaper());
            h.previews[2].setImageResource(pack.battery());
            h.previews[3].setImageResource(pack.about());
            // OOS Stock mostra le vere icone estratte coi loro colori reali (niente tinta).
            // OOS mostra le stesse forme ma bianche dentro il cerchio, per distinguerla da HOS.
            // Gli altri pack (PUI/HOS) restano con la tinta viola generica di sempre.
            ColorStateList previewTint;
            if (pack.iconSet() == STOCK_ICON_SET) {
                previewTint = null;
            } else if (pack.iconSet() == OOS_ICON_SET) {
                previewTint = ColorStateList.valueOf(Color.WHITE);
            } else {
                previewTint = ColorStateList.valueOf(0xFF908DFF);
            }
            for (ImageView preview : h.previews) preview.setImageTintList(previewTint);
            // Solo OOS applica davvero uno sfondo circolare dietro l'icona: l'anteprima lo
            // mostra, OOS Stock resta piatta (coerente con quello che i due pack producono).
            boolean showCircle = pack.iconSet() == OOS_ICON_SET;
            for (FrameLayout cell : h.cells) {
                if (showCircle) {
                    android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
                    circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    circle.setColor(0x33908DFF);
                    cell.setBackground(circle);
                } else {
                    cell.setBackground(null);
                }
            }

            boolean selected = idx == mSelected;
            boolean isActive = pack.iconSet() == mAppliedSet;
            h.check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            h.check.setImageTintList(ColorStateList.valueOf(isActive ? 0xFF7C4DFF : ObsidianTheme.textColor()));
            h.card.setStrokeColor(selected ? 0xFF7C4DFF : ObsidianTheme.textColor(0x33));
            h.card.setAlpha(mBusy ? 0.6f : 1f);

            h.card.setOnClickListener(v -> onPackTapped(idx));
        }

        @Override public int getItemCount() { return indices.size(); }
    }

    // ── Status + Applica/Disabilita ─────────────────────────────────────────

    private class ButtonsAdapter extends RecyclerView.Adapter<ButtonsAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView status;
            MaterialButton apply;
            MaterialButton disable;
            VH(View v) { super(v); }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(8), dp(16), dp(16));

            TextView status = new TextView(requireContext());
            status.setTextColor(ObsidianTheme.textColor(0xCC));
            status.setTextSize(13);
            status.setPadding(0, 0, 0, dp(10));
            root.addView(status);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            int dstBg = ObsidianTheme.bgColor();
            int accent = ObsidianTheme.accentColor();

            MaterialButton apply = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            apply.setText(R.string.settings_icons_apply);
            apply.setAllCaps(false);
            apply.setTextColor(ObsidianTheme.textColor());
            apply.setTextSize(14);
            apply.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            applyLp.setMarginEnd(dp(8));
            apply.setLayoutParams(applyLp);
            apply.setInsetTop(0);
            apply.setInsetBottom(0);
            apply.setCornerRadius(dp(26));
            apply.setBackgroundTintList(ColorStateList.valueOf(dstBg));
            apply.setStrokeColor(ColorStateList.valueOf(accent));
            apply.setStrokeWidth(dpF(1.5f));

            MaterialButton disable = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            disable.setText(R.string.settings_icons_disable);
            disable.setAllCaps(false);
            disable.setTextColor(ObsidianTheme.textColor());
            disable.setTextSize(14);
            disable.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams disableLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            disable.setLayoutParams(disableLp);
            disable.setInsetTop(0);
            disable.setInsetBottom(0);
            disable.setCornerRadius(dp(26));
            disable.setBackgroundTintList(ColorStateList.valueOf(dstBg));
            disable.setStrokeColor(ColorStateList.valueOf(ObsidianTheme.textColor()));
            disable.setStrokeWidth(dpF(1.5f));

            row.addView(apply);
            row.addView(disable);
            root.addView(row);

            VH h = new VH(root);
            h.status = status;
            h.apply = apply;
            h.disable = disable;
            return h;
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            boolean selectedIsApplied = mSelected >= 0 && mPacks.get(mSelected).iconSet() == mAppliedSet;

            if (mBusy) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.loading_dialog_wait);
            } else if (mAppliedSet < 0) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_icons_status_none);
            } else if (selectedIsApplied && mActive) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_icons_status_active);
            } else if (selectedIsApplied && mPendingReboot) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_icons_status_pending_reboot);
            } else {
                // Un pack diverso da quello selezionato è applicato altrove: nessun messaggio
                // accurato da mostrare qui, meglio nasconderlo che mostrare uno stato sbagliato.
                h.status.setVisibility(View.GONE);
            }

            // Il pulsante Disabilita ha senso solo quando il pack SELEZIONATO è davvero
            // quello attivo — non basta che qualcosa sia applicato altrove, altrimenti
            // compare sotto qualunque card si tocchi, rendendo impossibile capire quale
            // pack sia realmente installato.
            h.disable.setVisibility(selectedIsApplied ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams applyLp = (LinearLayout.LayoutParams) h.apply.getLayoutParams();
            applyLp.setMarginEnd(selectedIsApplied ? dp(8) : 0);
            h.apply.setLayoutParams(applyLp);

            h.apply.setEnabled(!mBusy && mSelected >= 0);
            h.disable.setEnabled(!mBusy && selectedIsApplied);
            h.apply.setAlpha(h.apply.isEnabled() ? 1f : 0.5f);
            h.disable.setAlpha(h.disable.isEnabled() ? 1f : 0.5f);

            h.apply.setOnClickListener(v -> onApplyClicked());
            h.disable.setOnClickListener(v -> onDisableClicked());
        }

        @Override public int getItemCount() { return 1; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int dpF(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
