package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;

/** Le personalizzazioni delle singole app (Google e OnePlus), raccolte in una sola pagina. */
public class AppsFragment extends Fragment {

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

        // ordine alfabetico
        List<NavAdapter.NavItem> items = List.of(
                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_pmbar), getString(R.string.nav_pmbar_summary),
                        () -> navigate(new PhoneManagerFragment(), getString(R.string.nav_pmbar)),
                        0xFFFF9800, "gestione telefono", "phone manager", "barra", "bar", "bordo", "border"),
                new NavAdapter.NavItem(
                        R.drawable.ic_notifications,
                        getString(R.string.nav_contacts), getString(R.string.nav_contacts_summary),
                        () -> navigate(new ContactsFragment(), getString(R.string.nav_contacts)),
                        0xFF2196F3, "contatti", "contacts", "rubrica", "card", "bordo", "border", "google"),
                new NavAdapter.NavItem(
                        R.drawable.ic_notifications,
                        getString(R.string.nav_messages), getString(R.string.nav_messages_summary),
                        () -> navigate(new MessagesBubblesFragment(), getString(R.string.nav_messages)),
                        0xFF4CAF50, "messaggi", "messages", "bolle", "bubble", "chat", "bordo", "border", "google"),
                new NavAdapter.NavItem(
                        R.drawable.ic_notifications,
                        getString(R.string.nav_dialer), getString(R.string.nav_dialer_summary),
                        () -> navigate(new DialerFragment(), getString(R.string.nav_dialer)),
                        0xFF009688, "telefono", "phone", "dialer", "tastierino", "keypad", "tasti", "google"),
                new NavAdapter.NavItem(
                        R.drawable.ic_recents,
                        getString(R.string.nav_launcher), getString(R.string.nav_launcher_summary),
                        () -> navigate(new LauncherFragment(), getString(R.string.nav_launcher)),
                        0xFF009688, "launcher", "recenti", "recents", "task switcher", "multitasking")
        );
        ((RecyclerView) view).setAdapter(new NavAdapter(new java.util.ArrayList<>(
                it.tugaia56.obsidian.ui.PureAndroidFilter.filter(requireContext(), items))));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
