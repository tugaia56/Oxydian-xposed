package it.tugaia56.obsidian.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos;

public class ListWidgetAdapter extends RecyclerView.Adapter<ListWidgetAdapter.VH> {

    public static class ListItem {
        public final String title;
        public String valueSummary;
        public final Runnable onClick;
        /** false renders valueSummary muted grey (matches switch summaries) instead of accent-coloured. */
        public boolean useAccentColor = true;
        /** Position within a merged-card row group; SINGLE = standalone pill (default). */
        public GroupPos groupPos = GroupPos.SINGLE;
        /** Lighter/bordered background instead of the normal card colour — for options
         *  revealed by tapping a row above them. */
        public boolean nested = false;
        /** Se presente, al posto della freccia c'e' un pulsantino con questa scritta che esegue onAction. */
        public String actionLabel;
        public Runnable onAction;

        public ListItem(String title, String valueSummary, Runnable onClick) {
            this.title        = title;
            this.valueSummary = valueSummary;
            this.onClick      = onClick;
        }
    }

    private final List<ListItem> items;

    public ListWidgetAdapter(List<ListItem> items) { this.items = items; }

    public List<ListItem> getItems() { return items; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_widget_list, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ListItem item = items.get(pos);
        h.title.setText(item.title);
        h.value.setText(item.valueSummary != null ? item.valueSummary : "");
        h.value.setVisibility(item.valueSummary == null || item.valueSummary.isEmpty() ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> { if (item.onClick != null) item.onClick.run(); });
        if (item.actionLabel != null) {
            int acc = ObsidianTheme.accentColor();
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setCornerRadius(1000f);
            pill.setColor((acc & 0x00FFFFFF) | 0x33000000);
            pill.setStroke((int) (1.5f * h.itemView.getResources().getDisplayMetrics().density), acc);
            h.action.setBackground(pill);
            h.action.setTextColor(acc);
            h.action.setText(item.actionLabel);
            h.action.setVisibility(View.VISIBLE);
            h.action.setOnClickListener(v -> { if (item.onAction != null) item.onAction.run(); });
            h.chevron.setVisibility(View.GONE);
        } else {
            h.action.setVisibility(View.GONE);
            h.action.setOnClickListener(null);
            h.chevron.setVisibility(View.VISIBLE);
        }

        // ── DST-based colours + grouped/standalone card background ───────────
        h.title.setTextColor(ObsidianTheme.textColor());
        h.value.setTextColor(item.useAccentColor ? ObsidianTheme.accentColor() : ObsidianTheme.textColor(0x99));
        h.itemView.setBackground(item.nested
                ? ObsidianTheme.nestedGroupBackground(h.itemView.getContext(), item.groupPos)
                : ObsidianTheme.groupBackground(h.itemView.getContext(), item.groupPos));

        if (h.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) h.itemView.getLayoutParams();
            ObsidianTheme.applyGroupMargins(h.itemView.getContext(), lp, item.groupPos);
            h.itemView.setLayoutParams(lp);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, value, action, chevron;

        VH(View v) {
            super(v);
            action = v.findViewById(R.id.listAction);
            chevron = v.findViewById(R.id.listChevron);
            title = v.findViewById(R.id.listTitle);
            value = v.findViewById(R.id.listValue);
        }
    }
}
