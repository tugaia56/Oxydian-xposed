package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;

public class HomeFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 12, 0, 24);
        rv.setClipToPadding(false);
        LayoutAnimationController anim = AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_slide_up);
        rv.setLayoutAnimation(anim);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        // ── Header adapter (single static item) ───────────────────────────────
        RecyclerView.Adapter<RecyclerView.ViewHolder> headerAdapter =
                new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                      int viewType) {
                        View v = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_home_header, parent, false);
                        it.tugaia56.obsidian.utils.DeviceInfo.applyTagline(v);
                        return new RecyclerView.ViewHolder(v) {};
                    }
                    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,
                                                          int pos) {}
                    @Override public int getItemCount() { return 1; }
                };

        // ── Nav items ─────────────────────────────────────────────────────────
        List<NavAdapter.NavItem> items = List.of(

                new NavAdapter.NavItem(
                        R.drawable.ic_drawing,
                        getString(R.string.nav_oxytheme),
                        getString(R.string.nav_oxytheme_summary),
                        () -> it.tugaia56.obsidian.utils.OxydianThemeLauncher.open(requireContext())),

                new NavAdapter.NavItem(
                        R.drawable.ic_palette,
                        getString(R.string.nav_system_colors),
                        getString(R.string.nav_system_colors_summary),
                        () -> navigate(new SystemColorsFragment(),
                                getString(R.string.nav_system_colors))),

                new NavAdapter.NavItem(
                        R.drawable.ic_qs,
                        getString(R.string.nav_quick_settings),
                        getString(R.string.nav_quick_settings_summary),
                        () -> navigate(new QsFragment(),
                                getString(R.string.nav_quick_settings))),

                new NavAdapter.NavItem(
                        R.drawable.ic_lock,
                        getString(R.string.nav_lock_screen),
                        getString(R.string.nav_lock_screen_summary),
                        () -> navigate(new LockScreenFragment(),
                                getString(R.string.nav_lock_screen))),

                new NavAdapter.NavItem(
                        R.drawable.ic_notifications,
                        getString(R.string.nav_statusbar),
                        getString(R.string.nav_statusbar_summary),
                        () -> navigate(new StatusbarFragment(),
                                getString(R.string.nav_statusbar))),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_settings),
                        getString(R.string.nav_settings_summary),
                        () -> navigate(new SettingsFragment(),
                                getString(R.string.nav_settings)))
        );

        rv.setAdapter(new ConcatAdapter(headerAdapter, new NavAdapter(items)));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
