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
    private static final String KEY_DIR = "grad_border_dir";   // 0 diagonale ... 5 radiale
    private static final String KEY_W = "grad_border_w";   // mezzi dp, di serie 3 = 1,5 dp
    private static final int DEF_W = 3;
    private static final String KEY_C1 = "grad_border_c1";
    private static final String KEY_C1_ON = "grad_border_c1_custom";
    private static final String KEY_C2 = "grad_border_c2";
    private static final String KEY_C3 = "grad_border_c3";
    private static final String KEY_C3_ON = "grad_border_c3_on";
    private static final String KEY_C2_ACC = "grad_border_c2_accent";   // colore 2 = accento del Theme
    private static final String KEY_C3_ACC = "grad_border_c3_accent";   // colore 3 = accento del Theme
    private static final int DEF_C2 = 0xFFFF7AA8;
    private static final int DEF_C3 = 0xFF4DD0E1;
    private static final int DIALOG_C1 = KEY_C1.hashCode();
    private static final int DIALOG_C2 = KEY_C2.hashCode();
    private static final int DIALOG_C3 = KEY_C3.hashCode();

    private RecyclerView mRv;
    private PreviewView mPreview;

    /** Riquadro di anteprima: scheda scura con il bordo sfumato dei colori e dello spessore scelti. */
    private final class PreviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return 1; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float d = parent.getResources().getDisplayMetrics().density;
            PreviewView v = new PreviewView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(96 * d));
            lp.setMargins(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), Math.round(8 * d));
            v.setLayoutParams(lp);
            mPreview = v;
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((PreviewView) holder.itemView).refresh();
        }
    }

    private static final class PreviewView extends View {
        private final android.graphics.Paint mFill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint mRing = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint mText = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PreviewView(android.content.Context c) {
            super(c);
            mFill.setColor(0xFF1B2029);
            mRing.setStyle(android.graphics.Paint.Style.STROKE);
            mText.setColor(0xFFFFFFFF);
            mText.setTextAlign(android.graphics.Paint.Align.CENTER);
        }

        void refresh() { invalidate(); }

        @Override protected void onDraw(android.graphics.Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int first = ObsidianPrefs.getBoolean(KEY_C1_ON, false) ? c1() : ObsidianTheme.overlayAccent();
            boolean three = ObsidianPrefs.getBoolean(KEY_C3_ON, true);
            int acc = ObsidianTheme.overlayAccent();
            int s2 = ObsidianPrefs.getBoolean(KEY_C2_ACC, false) ? acc : c2();
            int s3 = ObsidianPrefs.getBoolean(KEY_C3_ACC, false) ? acc : c3();
            int[] colors = three ? new int[]{first, s2, s3} : new int[]{first, s2};
            float w = Math.max(1f, ObsidianPrefs.getInt(KEY_W, DEF_W) * 0.5f * d);
            float half = w / 2f;
            android.graphics.RectF r = new android.graphics.RectF(half, half, getWidth() - half, getHeight() - half);
            float radius = 24 * d;
            c.drawRoundRect(r, radius, radius, mFill);
            mRing.setStrokeWidth(w);
            mRing.setShader(it.tugaia56.obsidian.xposed.utils.GradientBorder.makeShader(r, colors,
                    ObsidianPrefs.getInt(KEY_DIR, 0)));
            c.drawRoundRect(r, radius - half, radius - half, mRing);
            mText.setTextSize(16 * d);
            c.drawText(getResources().getString(R.string.gb_preview), getWidth() / 2f,
                    getHeight() / 2f + 6 * d, mText);
        }
    }

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
        // interruttore per primo, con la spiegazione come sottotitolo
        SwitchWidgetAdapter.SwitchItem on = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.gb_title), getString(R.string.gb_note),
                ObsidianPrefs.getBoolean(KEY_ON, false), null);
        on.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ON, on.checked);
            applyProps();
        };
        chain.add(new SwitchWidgetAdapter(List.of(on)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_colors))));
        chain.add(new PreviewAdapter());
        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c1), "", this::showC1Dialog));
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c2), "", this::showC2Dialog));
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.gb_c3), "", this::showC3Dialog));
        GroupUtils.addGroup(chain, rows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_direction_section))));
        GroupUtils.addGroup(chain, List.of(new ListWidgetAdapter.ListItem(getString(R.string.gb_direction),
                dirNames()[Math.max(0, Math.min(5, ObsidianPrefs.getInt(KEY_DIR, 0)))], this::showDirDialog)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gb_thickness_section))));
        GroupUtils.addGroup(chain, List.of(new it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter.SliderItem(
                getString(R.string.gb_thickness), ObsidianPrefs.getInt(KEY_W, DEF_W), 1, 8, "", DEF_W,
                value -> {
                    ObsidianPrefs.putInt(KEY_W, value);
                    applyProps();
                    if (mPreview != null) mPreview.refresh();
                })));

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

    private String[] dirNames() { return getResources().getStringArray(R.array.gb_directions); }

    private void showDirDialog() {
        final int[] sel = {Math.max(0, Math.min(5, ObsidianPrefs.getInt(KEY_DIR, 0)))};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gb_direction)
                .setSingleChoiceItems(dirNames(), sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(KEY_DIR, sel[0]);
                    applyProps();
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showC2Dialog() {
        String[] entries = {getString(R.string.gb_c1_accent), getString(R.string.gb_c3_pick)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gb_c2)
                .setItems(entries, (d, which) -> {
                    if (which == 0) {
                        ObsidianPrefs.putBoolean(KEY_C2_ACC, true);
                        applyProps();
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_C2, c2(), false, true, true);
                    }
                })
                .show());
    }

    private void showC3Dialog() {
        String[] entries = {getString(R.string.gb_c1_accent), getString(R.string.gb_c3_pick), getString(R.string.gb_c3_none)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gb_c3)
                .setItems(entries, (d, which) -> {
                    if (which == 0) {
                        ObsidianPrefs.putBoolean(KEY_C3_ACC, true);
                        ObsidianPrefs.putBoolean(KEY_C3_ON, true);
                        applyProps();
                        rebuild();
                    } else if (which == 1) {
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
            ObsidianPrefs.putBoolean(KEY_C2_ACC, false);
        } else if (e.dialogId() == DIALOG_C3) {
            ObsidianPrefs.putInt(KEY_C3, e.color() | 0xFF000000);
            ObsidianPrefs.putBoolean(KEY_C3_ACC, false);
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
        final boolean c2acc = ObsidianPrefs.getBoolean(KEY_C2_ACC, false);
        final boolean c3acc = ObsidianPrefs.getBoolean(KEY_C3_ACC, false);
        new Thread(() -> Shell.cmd(
                "setprop persist.obsidian.grad_on " + (on ? "1" : "0"),
                "setprop persist.obsidian.grad_c1 " + (c1on ? String.valueOf(c1) : "''"),
                "setprop persist.obsidian.grad_w " + ObsidianPrefs.getInt(KEY_W, DEF_W),
                "setprop persist.obsidian.grad_dir " + ObsidianPrefs.getInt(KEY_DIR, 0),
                "setprop persist.obsidian.grad_c2 " + (c2acc ? "accent" : String.valueOf(c2)),
                "setprop persist.obsidian.grad_c3 " + (!c3on ? "''" : c3acc ? "accent" : String.valueOf(c3))).exec()).start();
    }
}
