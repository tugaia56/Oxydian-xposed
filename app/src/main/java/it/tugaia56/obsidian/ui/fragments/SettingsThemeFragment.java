package it.tugaia56.obsidian.ui.fragments;

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

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.OverlayUtil;
import it.tugaia56.obsidian.utils.overlay.compiler.SettingsThemeCompiler;

/**
 * Stile Impostazioni OOS — compila l'overlay per com.android.settings a partire dal bundle
 * fisso (colori, drawable, animazioni impronta) estratto dal tema Substratum
 * type3_OxygenOS_16 dell'utente. Nessuna opzione: un solo overlay statico, Applica/Disabilita
 * come SettingsIconsFragment ma senza selezione pack (un solo "pack" possibile).
 */
public class SettingsThemeFragment extends Fragment {

    private static final String TAG = "SettingsThemeFragment";
    private static final String PREFIX = "ObsidianComponent";
    private static final String OVERLAY = PREFIX + "SST1.overlay";
    private static final String KEY_APPLIED = "settings_theme_applied";
    private static final String KEY_PENDING_REBOOT = "settings_theme_pending_reboot";

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
        mActive = mApplied && OverlayUtil.isOverlayEnabled(OVERLAY);
        if (mActive) mPendingReboot = false;

        rebuild();
    }

    private void rebuild() {
        mRv.setAdapter(new ConcatAdapter(new NoticeAdapter(), new CardAdapter(), new ButtonsAdapter()));
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
                erroredOut = SettingsThemeCompiler.buildOverlay();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                erroredOut = true;
            }
            boolean success = !erroredOut;
            boolean nowActive = success && OverlayUtil.isOverlayEnabled(OVERLAY);
            if (success) {
                ObsidianPrefs.putBoolean(KEY_APPLIED, true);
                ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, !nowActive);
                // 2026-09-05: serve al fallback file-property di SettingsCardBackgroundMod per
                // le app che non possono mai vedere il ContentProvider — vedi lì.
                it.tugaia56.obsidian.utils.DstFabricatedUtil.saveBootProps();
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
        if (mBusy || !mApplied) return;

        setBusy(true);
        new Thread(() -> {
            OverlayUtil.disableOverlays(OVERLAY);
            ObsidianPrefs.putBoolean(KEY_APPLIED, false);
            ObsidianPrefs.putBoolean(KEY_PENDING_REBOOT, false);
            it.tugaia56.obsidian.utils.DstFabricatedUtil.saveBootProps();
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
            tv.setText(R.string.settings_theme_notice);
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
            desc.setText(R.string.settings_theme_card_desc);
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
            if (mBusy) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.loading_dialog_wait);
            } else if (!mApplied) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_theme_status_none);
            } else if (mActive) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_icons_status_active);
            } else if (mPendingReboot) {
                h.status.setVisibility(View.VISIBLE);
                h.status.setText(R.string.settings_icons_status_pending_reboot);
            } else {
                h.status.setVisibility(View.GONE);
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
