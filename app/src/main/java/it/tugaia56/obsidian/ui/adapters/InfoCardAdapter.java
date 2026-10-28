package it.tugaia56.obsidian.ui.adapters;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos;

/** Riquadro con un testo informativo, con lo stesso sfondo delle altre voci (non cliccabile). */
public class InfoCardAdapter extends RecyclerView.Adapter<InfoCardAdapter.VH> {

    private final String text;

    public InfoCardAdapter(String text) { this.text = text; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView t = new TextView(parent.getContext());
        float d = parent.getResources().getDisplayMetrics().density;
        int p = Math.round(16 * d);
        t.setPadding(p, p, p, p);
        t.setTextSize(13);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
        return new VH(t);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TextView t = (TextView) h.itemView;
        t.setText(text);
        t.setTextColor(ObsidianTheme.textColor(0x99));
        t.setBackground(ObsidianTheme.groupBackground(t.getContext(), GroupPos.SINGLE));
        ViewGroup.LayoutParams lp = t.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ObsidianTheme.applyGroupMargins(t.getContext(), (ViewGroup.MarginLayoutParams) lp, GroupPos.SINGLE);
            t.setLayoutParams(lp);
        }
    }

    @Override public int getItemCount() { return 1; }

    static class VH extends RecyclerView.ViewHolder {
        VH(TextView v) { super(v); }
    }
}
