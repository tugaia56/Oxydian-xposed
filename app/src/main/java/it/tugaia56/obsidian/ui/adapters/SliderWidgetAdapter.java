package it.tugaia56.obsidian.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.function.Consumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos;

public class SliderWidgetAdapter extends RecyclerView.Adapter<SliderWidgetAdapter.VH> {

    public static class SliderItem {
        public String title;
        public int    value;
        public int    min;
        public int    max;
        public String suffix;
        /**
         * Valore di default per il pulsante ripristino.
         * Usa {@code -1} (default) per nascondere il pulsante.
         */
        public int    defaultValue;
        /** Incremento per ogni "scatto" del cursore — 1 di default (com'era prima). */
        public int    step = 1;
        /** Tacche fisse NON equidistanti (es. -50,-20,0,20,50,200) — quando impostato, il
         *  cursore internamente si muove su un indice 0..stops.length-1 equidistante (così le
         *  tacche restano ugualmente spaziate da toccare/trascinare), ma value/onChanged/
         *  onLivePreview/etichetta usano sempre il vero numero cercato in questo array, mai
         *  l'indice grezzo. Null (default) = comportamento normale min..max/step invariato. */
        public int[]  stops;
        public Consumer<Integer> onChanged;
        /** Optional: fires on every drag step for live preview; null by default. */
        public Consumer<Integer> onLivePreview;
        /** Position within a merged-card row group; SINGLE = standalone pill (default). */
        public GroupPos groupPos = GroupPos.SINGLE;
        /** Lighter/bordered background instead of the normal card colour — for options
         *  revealed by tapping a row above them. */
        public boolean nested = false;

        /** Costruttore senza pulsante ripristino (retrocompatibile). */
        public SliderItem(String title, int value, int min, int max, String suffix,
                          Consumer<Integer> onChanged) {
            this(title, value, min, max, suffix, -1, onChanged);
        }

        /** Costruttore con pulsante ripristino al valore {@code defaultValue}. */
        public SliderItem(String title, int value, int min, int max, String suffix,
                          int defaultValue, Consumer<Integer> onChanged) {
            this.title        = title;
            this.value        = value;
            this.min          = min;
            this.max          = max;
            this.suffix       = suffix != null ? suffix : "";
            this.defaultValue = defaultValue;
            this.onChanged    = onChanged;
        }
    }

    private final List<SliderItem> items;

    public SliderWidgetAdapter(List<SliderItem> items) { this.items = items; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_widget_slider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SliderItem item = items.get(pos);
        h.title.setText(item.title);

        if (item.stops != null && item.stops.length > 0) {
            // Tacche fisse: il cursore si muove su un indice equidistante 0..N-1, non sul
            // vero valore — evita l'illusione di "scorrimento continuo" quando le tacche
            // reali non sono equidistanti (es. -50,-20,0,20,50,200).
            int idx = nearestStopIndex(item.stops, item.value);
            h.slider.setValueFrom(0);
            h.slider.setValueTo(item.stops.length - 1);
            h.slider.setStepSize(1);
            h.slider.setValue(idx);
        } else {
            // Clamp value to [min, max], poi allinea allo step — con step > 1 il valore salvato
            // in precedenza (con step 1) potrebbe non essere un multiplo esatto, e lo Slider
            // di Material lancia un'eccezione se il valore non è allineato allo stepSize.
            float clamped = Math.max(item.min, Math.min(item.max, item.value));
            if (item.step > 1) {
                clamped = item.min + Math.round((clamped - item.min) / (float) item.step) * item.step;
            }
            h.slider.setValueFrom(item.min);
            h.slider.setValueTo(item.max);
            h.slider.setStepSize(item.step);
            h.slider.setValue(clamped);
        }
        h.slider.setTickVisible(false);
        h.value.setText(item.value + item.suffix);

        // ── DST-based colours + rounded card background ──────────────────────
        int accent = ObsidianTheme.accentColor();
        ColorStateList accentList   = ColorStateList.valueOf(accent);
        ColorStateList inactiveList = ColorStateList.valueOf(
                Color.argb(0x40, Color.red(accent), Color.green(accent), Color.blue(accent)));
        h.slider.setTrackActiveTintList(accentList);
        h.slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        h.slider.setTrackInactiveTintList(inactiveList);
        h.slider.setHaloTintList(accentList);
        h.value.setTextColor(accent);

        h.itemView.setBackground(item.nested
                ? ObsidianTheme.nestedGroupBackground(h.itemView.getContext(), item.groupPos)
                : ObsidianTheme.groupBackground(h.itemView.getContext(), item.groupPos));

        if (h.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) h.itemView.getLayoutParams();
            ObsidianTheme.applyGroupMargins(h.itemView.getContext(), lp, item.groupPos);
            h.itemView.setLayoutParams(lp);
        }

        // ── Pulsante ripristino ───────────────────────────────────────────────
        if (item.defaultValue >= 0) {
            h.resetBtn.setVisibility(View.VISIBLE);
            // Colorato (accento in trasparenza) quando il valore è diverso dal default,
            // così si vede a colpo d'occhio se lo slider è stato modificato.
            updateResetBtnAppearance(h.resetBtn, item.value == item.defaultValue);
            h.resetBtn.setOnClickListener(v -> {
                int def = item.stops != null && item.stops.length > 0
                        ? item.defaultValue
                        : Math.max(item.min, Math.min(item.max, item.defaultValue));
                item.value = def;
                h.slider.setValue(item.stops != null && item.stops.length > 0
                        ? nearestStopIndex(item.stops, def) : def);
                h.value.setText(def + item.suffix);
                updateResetBtnAppearance(h.resetBtn, true);
                if (item.onChanged != null) item.onChanged.accept(def);
            });
        } else {
            h.resetBtn.setVisibility(View.GONE);
            h.resetBtn.setOnClickListener(null);
        }

        // ── Listeners cursore ─────────────────────────────────────────────────
        h.slider.clearOnChangeListeners();
        h.slider.clearOnSliderTouchListeners();

        h.slider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int mapped = item.stops != null && item.stops.length > 0
                        ? item.stops[(int) value] : (int) value;
                item.value = mapped;
                h.value.setText(mapped + item.suffix);
                updateResetBtnAppearance(h.resetBtn, item.value == item.defaultValue);
                if (item.onLivePreview != null) item.onLivePreview.accept(mapped);
            }
        });

        h.slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {}
            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                if (item.onChanged != null) item.onChanged.accept(item.value);
            }
        });
    }

    /** Indice della tacca più vicina al valore reale corrente — usato per posizionare il
     *  cursore quando item.value non coincide esattamente con una tacca (es. valore salvato
     *  prima che questa riga passasse a usare le tacche fisse). */
    private static int nearestStopIndex(int[] stops, int value) {
        int best = 0, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < stops.length; i++) {
            int dist = Math.abs(stops[i] - value);
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    /** Icona piena + anello di accento quando il valore è diverso dal default — stesso stile
     *  usato per gli elementi "selezionati" altrove nell'app, molto più visibile di un
     *  semplice sfondo tinto in trasparenza. */
    private static void updateResetBtnAppearance(ImageButton resetBtn, boolean atDefault) {
        resetBtn.setBackgroundTintList(null);
        if (atDefault) {
            resetBtn.clearColorFilter();
            resetBtn.setBackgroundResource(R.drawable.obs_reset_btn_bg);
        } else {
            resetBtn.setColorFilter(ObsidianTheme.accentColor());
            int strokePx = Math.round(2 * resetBtn.getResources().getDisplayMetrics().density);
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(ObsidianTheme.cardColor());
            ring.setStroke(strokePx, ObsidianTheme.accentColor());
            resetBtn.setBackground(ring);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView    title, value;
        Slider      slider;
        ImageButton resetBtn;

        VH(View v) {
            super(v);
            title    = v.findViewById(R.id.sliderTitle);
            value    = v.findViewById(R.id.sliderValue);
            slider   = v.findViewById(R.id.seekBar);
            resetBtn = v.findViewById(R.id.sliderReset);
        }
    }
}
