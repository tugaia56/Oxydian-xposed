package it.tugaia56.obsidian.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.OverlayUtil;
import it.tugaia56.obsidian.utils.overlay.compiler.LauncherThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.SettingsThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.SystemUIThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.VideoThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.FloatAssistantThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.AppRecoverThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.ContentPortalThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.DeskClockThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.OShareThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.CalculatorThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.GamesThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.EyeProtectThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.GalleryThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.CameraThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.BrowserThemeCompiler;
import it.tugaia56.obsidian.utils.overlay.compiler.WirelessSettingsThemeCompiler;

/**
 * Stile Dark Shadow Theme per Obsidian — 2026-09-11: schermata unica che sostituisce le 3
 * separate (SettingsThemeFragment/SystemUIThemeFragment/LauncherThemeFragment, lasciate intatte
 * ma non più linkate dall'hub), con un solo Applica/Disabilita che compila ed abilita in un
 * colpo solo i 3 overlay statici (SST1/SUT1/LT1). Stessa logica, stessi compiler — solo unita.
 *
 * NB: qui la registrazione degli overlay da parte di OverlayManagerService resta il problema
 * non risolto documentato in project_ksu_next_migration/project_session_20260910_uncommitted —
 * "Applica" compila sempre correttamente, ma "riavvia per attivarlo" può restare per sempre su
 * questo device. Questa schermata non cambia quel comportamento, solo lo presenta in un posto
 * solo invece di tre.
 */
public class SubstratumStyleFragment extends Fragment {

    private static final String TAG = "SubstratumStyleFragment";
    private static final String PREFIX = "ObsidianComponent";
    private static final String SST_OVERLAY = PREFIX + "SST1.overlay";
    private static final String SUT_OVERLAY = PREFIX + "SUT1.overlay";
    private static final String LT_OVERLAY  = PREFIX + "LT1.overlay";
    private static final String VT_OVERLAY  = PREFIX + "VT1.overlay";
    private static final String FA_OVERLAY  = PREFIX + "FA1.overlay";
    private static final String AR_OVERLAY  = PREFIX + "AR1.overlay";
    private static final String CP_OVERLAY  = PREFIX + "CP1.overlay";
    private static final String DC_OVERLAY  = PREFIX + "DC1.overlay";
    private static final String OS_OVERLAY  = PREFIX + "OS1.overlay";
    private static final String CA_OVERLAY  = PREFIX + "CA1.overlay";
    private static final String GA_OVERLAY  = PREFIX + "GA1.overlay";
    private static final String EP_OVERLAY  = PREFIX + "EP1.overlay";
    private static final String GL_OVERLAY  = PREFIX + "GL1.overlay";
    private static final String CM_OVERLAY  = PREFIX + "CM1.overlay";
    private static final String BR_OVERLAY  = PREFIX + "BR1.overlay";
    private static final String WS_OVERLAY  = PREFIX + "WS1.overlay";
    private static final String KEY_APPLIED = "substratum_style_applied";
    private static final String KEY_PENDING_REBOOT = "substratum_style_pending_reboot";

    private boolean mApplied = false;
    private boolean mActive = false;
    private boolean mPendingReboot = false;
    private boolean mBusy = false;
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

        mApplied = ObsidianPrefs.getBoolean(KEY_APPLIED, false);
        mPendingReboot = ObsidianPrefs.getBoolean(KEY_PENDING_REBOOT, false);
        mActive = mApplied && allActive();
        if (mActive) mPendingReboot = false;

        rebuild();
    }

    private boolean allActive() {
        return OverlayUtil.isOverlayEnabled(SST_OVERLAY)
                && OverlayUtil.isOverlayEnabled(SUT_OVERLAY)
                && OverlayUtil.isOverlayEnabled(LT_OVERLAY)
                && OverlayUtil.isOverlayEnabled(VT_OVERLAY)
                && OverlayUtil.isOverlayEnabled(FA_OVERLAY)
                && OverlayUtil.isOverlayEnabled(AR_OVERLAY)
                && OverlayUtil.isOverlayEnabled(CP_OVERLAY)
                && OverlayUtil.isOverlayEnabled(DC_OVERLAY)
                && OverlayUtil.isOverlayEnabled(OS_OVERLAY)
                && OverlayUtil.isOverlayEnabled(CA_OVERLAY)
                && OverlayUtil.isOverlayEnabled(GA_OVERLAY)
                && OverlayUtil.isOverlayEnabled(EP_OVERLAY)
                && OverlayUtil.isOverlayEnabled(GL_OVERLAY)
                && OverlayUtil.isOverlayEnabled(CM_OVERLAY)
                && OverlayUtil.isOverlayEnabled(BR_OVERLAY)
                && OverlayUtil.isOverlayEnabled(WS_OVERLAY);
    }

    private void rebuild() {
        mRv.setAdapter(new ConcatAdapter(
                new NoticeAdapter(), new CardAdapter(), new ButtonsAdapter(), new HowToActivateAdapter()));
    }

    private void onApplyClicked() {
        if (mBusy) return;

        if (!AppUtils.hasStoragePermission()) {
            AppUtils.requestStoragePermission(requireContext());
            return;
        }

        setBusy(true);
        new Thread(() -> {
            boolean erroredOut;
            try {
                // 2026-09-12: "Sistema OPPO" (OP1, package framework "oplus") tolto di nuovo
                // dalla catena — anche il boot "pulito" (nessun enable dal vivo, solo scansione
                // OMS dei 18 overlay già abilitati) ha causato un blocco reale che ha richiesto
                // un riavvio forzato, non il solito rallentamento auto-risolvente. Overlay/APK
                // rimossi anche dal modulo obsidian_overlayfs (utente, live). Classe compiler e
                // asset bundle lasciati nel progetto, solo scollegati da qui — stesso approccio
                // già usato per le 3 vecchie schermate separate del tema.
                boolean e1 = SettingsThemeCompiler.buildOverlay();
                boolean e2 = SystemUIThemeCompiler.buildOverlay();
                boolean e3 = LauncherThemeCompiler.buildOverlay();
                boolean e4 = VideoThemeCompiler.buildOverlay();
                boolean e5 = FloatAssistantThemeCompiler.buildOverlay();
                boolean e6 = AppRecoverThemeCompiler.buildOverlay();
                boolean e7 = ContentPortalThemeCompiler.buildOverlay();
                boolean e8 = DeskClockThemeCompiler.buildOverlay();
                boolean e9 = OShareThemeCompiler.buildOverlay();
                boolean e10 = CalculatorThemeCompiler.buildOverlay();
                boolean e11 = GamesThemeCompiler.buildOverlay();
                boolean e12 = EyeProtectThemeCompiler.buildOverlay();
                boolean e13 = GalleryThemeCompiler.buildOverlay();
                boolean e14 = CameraThemeCompiler.buildOverlay();
                boolean e15 = BrowserThemeCompiler.buildOverlay();
                boolean e16 = WirelessSettingsThemeCompiler.buildOverlay();
                erroredOut = e1 || e2 || e3 || e4 || e5 || e6 || e7 || e8 || e9 || e10 || e11 || e12 || e13 || e14 || e15 || e16;
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                erroredOut = true;
            }
            boolean success = !erroredOut;
            boolean nowActive = success && allActive();
            if (success) {
                ObsidianPrefs.putBoolean(KEY_APPLIED, true);
                ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, !nowActive);
                // Coerenza con le vecchie schermate (file ancora presenti, solo non linkate).
                ObsidianPrefs.putBoolean("settings_theme_applied", true);
                ObsidianPrefs.putBoolean("systemui_theme_applied", true);
                ObsidianPrefs.putBoolean("launcher_theme_applied", true);
                DstFabricatedUtil.saveBootProps();
            }
            boolean finalSuccess = success;
            boolean finalActive = nowActive;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                setBusy(false);
                if (finalSuccess) {
                    mApplied = true;
                    mActive = finalActive;
                    mPendingReboot = !finalActive;
                    rebuild();
                    // Niente più distinzione "riavvia per attivare" nel toast — un solo
                    // messaggio di conferma, sempre lo stesso, su richiesta dell'utente.
                    Toast.makeText(requireContext(), R.string.toast_applied, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.toast_error, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void onDisableClicked() {
        if (mBusy || !mApplied) return;

        setBusy(true);
        new Thread(() -> {
            OverlayUtil.disableOverlays(SST_OVERLAY, SUT_OVERLAY, LT_OVERLAY, VT_OVERLAY, FA_OVERLAY, AR_OVERLAY, CP_OVERLAY, DC_OVERLAY, OS_OVERLAY, CA_OVERLAY, GA_OVERLAY, EP_OVERLAY, GL_OVERLAY, CM_OVERLAY, BR_OVERLAY, WS_OVERLAY);
            ObsidianPrefs.putBoolean(KEY_APPLIED, false);
            ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, false);
            ObsidianPrefs.putBoolean("settings_theme_applied", false);
            ObsidianPrefs.putBoolean("systemui_theme_applied", false);
            ObsidianPrefs.putBoolean("launcher_theme_applied", false);
            DstFabricatedUtil.saveBootProps();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                setBusy(false);
                mApplied = false;
                mPendingReboot = false;
                mActive = false;
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
            tv.setText(R.string.dst_substratum_style_notice);
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

    // ── Come attivare (doppio riavvio + ritardo di boot) ───────────────────────

    private class HowToActivateAdapter extends RecyclerView.Adapter<HowToActivateAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            int padH = dp(16);
            container.setPadding(padH, dp(8), padH, dp(20));
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(requireContext());
            tv.setText(R.string.dst_substratum_style_howto);
            tv.setTextColor(ObsidianTheme.textColor(0xAA));
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(tv);

            TextView link = new TextView(requireContext());
            link.setText(R.string.dst_substratum_style_readme_link);
            link.setTextColor(ObsidianTheme.accentColor());
            link.setTextSize(16);
            link.setGravity(Gravity.CENTER);
            link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linkLp.topMargin = dp(10);
            link.setLayoutParams(linkLp);
            link.setOnClickListener(v -> showReadmeDialog());
            container.addView(link);

            return new VH(container);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {}
        @Override public int getItemCount() { return 1; }
    }

    private void showReadmeDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dst_substratum_style_readme_link)
                .setMessage(R.string.dst_substratum_style_readme_full)
                .setPositiveButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    // ── Card ─────────────────────────────────────────────────────────────────

    private class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            ImageView check;
            VH(View v) { super(v); }
        }

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

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));

            LinearLayout titleCol = new LinearLayout(requireContext());
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleCol.setLayoutParams(titleColLp);

            LinearLayout titleRow = new LinearLayout(requireContext());
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(requireContext());
            title.setText(R.string.settings_theme_card_title);
            title.setTextColor(ObsidianTheme.textColor());
            title.setTextSize(15);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            titleRow.addView(title);

            ImageView check = new ImageView(requireContext());
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            checkLp.setMarginStart(dp(8));
            check.setLayoutParams(checkLp);
            check.setImageDrawable(requireContext().getDrawable(android.R.drawable.checkbox_on_background));
            check.setImageTintList(ColorStateList.valueOf(0xFF7C4DFF));
            check.setVisibility(View.INVISIBLE);
            titleRow.addView(check);

            titleCol.addView(titleRow);

            TextView desc = new TextView(requireContext());
            desc.setText(R.string.dst_substratum_style_card_desc);
            desc.setTextColor(0x99FFFFFF);
            desc.setTextSize(13);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = dp(4);
            desc.setLayoutParams(descLp);
            titleCol.addView(desc);

            row.addView(titleCol);
            card.addView(row);

            VH h = new VH(card);
            h.card = card;
            h.check = check;
            return h;
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            h.check.setVisibility(mApplied ? View.VISIBLE : View.INVISIBLE);
            h.check.setImageTintList(ColorStateList.valueOf(mActive ? 0xFF7C4DFF : ObsidianTheme.textColor()));
            h.card.setStrokeColor(mApplied ? 0xFF7C4DFF : ObsidianTheme.textColor(0x33));
            h.card.setAlpha(mBusy ? 0.6f : 1f);
        }

        @Override public int getItemCount() { return 1; }
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
            // Un solo stato positivo ("Tema attivo") invece di distinguere attivo/in-attesa-
            // di-riavvio, su richiesta dell'utente — niente più promemoria di riavvio qui.
            if (mBusy) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.loading_dialog_wait);
            } else if (!mApplied) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.dst_substratum_style_status_none);
            } else {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.dst_substratum_style_status_active);
            }

            h.disable.setVisibility(mApplied ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams applyLp = (LinearLayout.LayoutParams) h.apply.getLayoutParams();
            applyLp.setMarginEnd(mApplied ? dp(8) : 0);
            h.apply.setLayoutParams(applyLp);

            h.apply.setEnabled(!mBusy);
            h.disable.setEnabled(!mBusy && mApplied);
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
