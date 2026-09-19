package it.tugaia56.obsidian.ui.activity;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.fragments.DarkShadowThemeFragment;
import it.tugaia56.obsidian.ui.fragments.DstTabFragment;
import it.tugaia56.obsidian.ui.fragments.GestioneModsTabFragment;
import it.tugaia56.obsidian.ui.fragments.ImpostazioniTabFragment;
import it.tugaia56.obsidian.ui.fragments.LockScreenOptionsFragment;
import it.tugaia56.obsidian.ui.fragments.BatteryIconFragment;
import it.tugaia56.obsidian.ui.fragments.ClockDateFragment;
import it.tugaia56.obsidian.ui.fragments.PinStyleFragment;
import it.tugaia56.obsidian.ui.fragments.NavbarStyleFragment;
import it.tugaia56.obsidian.ui.fragments.QsFragment;
import it.tugaia56.obsidian.ui.fragments.QuickSettingsFragment;
import it.tugaia56.obsidian.ui.fragments.SettingsAboutFragment;
import it.tugaia56.obsidian.ui.fragments.SettingsFragment;
import it.tugaia56.obsidian.ui.fragments.SettingsGeneralFragment;
import it.tugaia56.obsidian.ui.fragments.SettingsIconsFragment;
import it.tugaia56.obsidian.ui.fragments.SettingsUpdateFragment;
import it.tugaia56.obsidian.ui.fragments.SignalIconsFragment;
import it.tugaia56.obsidian.ui.fragments.SignalStyleFragment;
import it.tugaia56.obsidian.ui.fragments.StatusbarFragment;
import it.tugaia56.obsidian.ui.fragments.SystemColorsFragment;
import it.tugaia56.obsidian.ui.fragments.ThemeStyleFragment;
import it.tugaia56.obsidian.ui.fragments.VolumeStyleFragment;
import it.tugaia56.obsidian.ui.fragments.WifiIconsFragment;
// ── Sotto-schermate: aggiunte per far comparire la destinazione ESATTA nella ricerca
// (prima puntava solo alla card di primo livello, "cartella" invece di "file" — vedi
// buildSearchItems()). ──────────────────────────────────────────────────────────────
import it.tugaia56.obsidian.ui.fragments.AodFragment;
import it.tugaia56.obsidian.ui.fragments.AodClockFragment;
import it.tugaia56.obsidian.ui.fragments.AodWeatherFragment;
import it.tugaia56.obsidian.ui.fragments.AodEdgeLightFragment;
import it.tugaia56.obsidian.ui.fragments.LockScreenFragment;
import it.tugaia56.obsidian.ui.fragments.LockscreenClockFragment;
import it.tugaia56.obsidian.ui.fragments.LockscreenWeatherFragment;
import it.tugaia56.obsidian.ui.fragments.LockscreenWidgetsFragment;
import it.tugaia56.obsidian.ui.fragments.FingerprintIconFragment;
import it.tugaia56.obsidian.ui.fragments.QsSolidBgFragment;
import it.tugaia56.obsidian.ui.fragments.QsHeaderImageFragment;
import it.tugaia56.obsidian.ui.fragments.QsHeaderClockFragment;
import it.tugaia56.obsidian.ui.fragments.ClockStyleFragment;
import it.tugaia56.obsidian.ui.fragments.ClockOraDataFragment;
import it.tugaia56.obsidian.ui.fragments.ClockChipStyleFragment;
import it.tugaia56.obsidian.ui.fragments.StatusbarNotifsFragment;
import it.tugaia56.obsidian.ui.fragments.DstBackgroundFragment;
import it.tugaia56.obsidian.ui.fragments.DstAccentFragment;
import it.tugaia56.obsidian.ui.fragments.VolumePanelFragment;
import it.tugaia56.obsidian.ui.fragments.VolumePanelColorsFragment;
import it.tugaia56.obsidian.ui.fragments.LauncherFragment;
import it.tugaia56.obsidian.ui.fragments.LauncherDockBackgroundFragment;
import it.tugaia56.obsidian.ui.fragments.MiscFragment;
import it.tugaia56.obsidian.ui.fragments.PowerMenuFragment;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ColorUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

public class MainActivity extends AppCompatActivity implements ColorPickerDialogListener {

    private BottomNavigationView mBottomNav;
    private View mNavDivider;

    // ── Global search ─────────────────────────────────────────────────────────
    private FrameLayout mHomeHeaderContainer;   // icon + OBSIDIAN + tagline (fixed)
    private FrameLayout mGlobalSearchContainer; // search bar + drag handle
    private RecyclerView mSearchResultsRv;
    private TextView mSearchEmptyTv;
    private EditText mGlobalSearchEt;
    private LinearLayout mRecentSearchesRow;
    private List<SearchEntry> mAllSearchItems;
    private android.graphics.drawable.Drawable mSearchClearIcon;

    private static final String KEY_RECENT_SEARCHES = "global_search_recent_queries";
    private static final int MAX_RECENT_SEARCHES = 8;

    /** "dst"/"mods"/"settings" — vedi Impostazioni > Generale > Disposizione. */
    public static final String KEY_DEFAULT_TAB = "default_tab";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ObsidianTheme.refreshThemeMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBottomNav             = findViewById(R.id.bottom_nav);
        mNavDivider            = findViewById(R.id.nav_divider);
        mHomeHeaderContainer   = findViewById(R.id.home_header_container);
        mGlobalSearchContainer = findViewById(R.id.global_search_container);
        mSearchResultsRv       = findViewById(R.id.search_results_rv);
        mSearchEmptyTv         = findViewById(R.id.search_empty_tv);

        if (savedInstanceState == null) {
            int defaultTabId = resolveDefaultTabId();
            switchTab(defaultTabId);
            mBottomNav.setSelectedItemId(defaultTabId);
        }

        applyNavBarColor();
        // On a fresh start there's no back stack yet, so the home header/logo is correct.
        // On process recreation (app killed in background then restored) the FragmentManager
        // already restores whatever sub-screen was on top — the action bar must match that,
        // not reset to the home logo, or it looks wrong even though the content is right.
        int restoredBackStackCount = getSupportFragmentManager().getBackStackEntryCount();
        if (restoredBackStackCount > 0) {
            String restoredTitle = getSupportFragmentManager()
                    .getBackStackEntryAt(restoredBackStackCount - 1).getName();
            hideGlobalSearch();
            setSubFragmentActionBar(restoredTitle);
        } else {
            showHomeActionBar();   // also makes search container visible
        }

        mAllSearchItems = buildSearchItems();
        setupGlobalSearch();

        mBottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.tab_search) {
                showSearchTab();
            } else {
                switchTab(item.getItemId());
                showHomeActionBar();
            }
            return true;
        });

        getSupportFragmentManager().addOnBackStackChangedListener(
                () -> mBottomNav.setVisibility(View.VISIBLE));
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_restart_systemui) {
            AppUtils.restartSystemUI(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    /**
     * Navigate to a sub-fragment. Hides the global search bar for the duration.
     */
    public void navigateTo(Fragment fragment, String title) {
        hideGlobalSearch();
        setSubFragmentActionBar(title);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.frag_enter,     R.anim.frag_exit,
                        R.anim.frag_pop_enter, R.anim.frag_pop_exit)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(title)
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        int remaining = getSupportFragmentManager().getBackStackEntryCount();
        if (remaining == 0) {
            showHomeActionBar();
        } else {
            String title = getSupportFragmentManager()
                    .getBackStackEntryAt(remaining - 1).getName();
            setSubFragmentActionBar(title != null ? title : "");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Segui il Sistema: re-check the system's current dark/light state on every resume
        // (not just cold start) so it keeps tracking day/night transitions that happened
        // while the app wasn't open, without needing a live configuration-change listener.
        // Only touches the DST "Preset Sfondo" toggle when Tema is actually set to Sistema —
        // an explicit Chiaro/Scuro choice is never revisited here, only at selection time.
        ObsidianTheme.refreshThemeMode(this);
        if (ObsidianTheme.themeMode() == ObsidianTheme.THEME_SYSTEM) {
            ObsidianTheme.syncBackgroundPresetToTheme();
        }
        int bg = ObsidianTheme.bgColor();
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setBackgroundDrawable(new ColorDrawable(bg));
        applyNavBarColor();
        mBottomNav.setVisibility(View.VISIBLE);
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count > 0) {
            String title = getSupportFragmentManager()
                    .getBackStackEntryAt(count - 1).getName();
            setSubFragmentActionBar(title != null ? title : "");
        } else {
            showHomeActionBar();
        }
    }

    // ── Global search ──────────────────────────────────────────────────────────

    /**
     * Builds the pill-shaped EditText + drag-handle divider inside
     * global_search_container, wires the TextWatcher, and initialises the
     * results RecyclerView. Called once from onCreate() after buildSearchItems().
     */
    private void setupGlobalSearch() {
        float dp = getResources().getDisplayMetrics().density;
        int p16 = Math.round(16 * dp);
        int p10 = Math.round(10 * dp);
        int p8  = Math.round(8  * dp);
        int p6  = Math.round(6  * dp);
        int p4  = Math.round(4  * dp);

        // ── Pill EditText ──────────────────────────────────────────────────────
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.RECTANGLE);
        border.setCornerRadius(28 * dp);
        border.setColor(ObsidianTheme.textColor(20));
        border.setStroke(Math.round(1.5f * dp), ObsidianTheme.textColor(80));

        mGlobalSearchEt = new EditText(this);
        mGlobalSearchEt.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mGlobalSearchEt.setBackground(border);
        mGlobalSearchEt.setHint(getString(R.string.search_mods_hint));
        mGlobalSearchEt.setHintTextColor(ObsidianTheme.textColor(128));
        mGlobalSearchEt.setTextColor(ObsidianTheme.textColor());
        mGlobalSearchEt.setSingleLine(true);
        mGlobalSearchEt.setPadding(p16, p10, p16, p10);
        mGlobalSearchEt.setInputType(InputType.TYPE_CLASS_TEXT);
        mGlobalSearchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_FULLSCREEN);

        android.graphics.drawable.Drawable searchIcon = null;
        android.graphics.drawable.Drawable clearIcon = null;
        try {
            searchIcon = getDrawable(R.drawable.ic_search);
            if (searchIcon != null) {
                searchIcon = searchIcon.mutate();
                searchIcon.setTint(ObsidianTheme.textColor(180));
                int sz = Math.round(18 * dp);
                searchIcon.setBounds(0, 0, sz, sz);
            }
            clearIcon = getDrawable(R.drawable.ic_search_clear);
            if (clearIcon != null) {
                clearIcon = clearIcon.mutate();
                clearIcon.setTint(ObsidianTheme.textColor(180));
                int sz = Math.round(18 * dp);
                clearIcon.setBounds(0, 0, sz, sz);
            }
        } catch (Throwable ignored) {}
        final android.graphics.drawable.Drawable finalSearchIcon = searchIcon;
        final android.graphics.drawable.Drawable finalClearIcon  = clearIcon;
        mSearchClearIcon = clearIcon;
        mGlobalSearchEt.setCompoundDrawablesRelative(finalSearchIcon, null, null, null);
        mGlobalSearchEt.setCompoundDrawablePadding(p8);

        // Tapping the X: clears the typed text if there's any, otherwise clears the
        // recent-searches history (same icon/spot, meaning depends on current state).
        mGlobalSearchEt.setOnTouchListener((v, event) -> {
            if (finalClearIcon == null) return false;
            if (event.getAction() != android.view.MotionEvent.ACTION_UP) return false;
            android.graphics.drawable.Drawable[] drawables = mGlobalSearchEt.getCompoundDrawablesRelative();
            if (drawables[2] == null) return false;
            boolean isRtl = mGlobalSearchEt.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int drawableWidth = drawables[2].getBounds().width();
            boolean hit = isRtl
                    ? event.getX() < mGlobalSearchEt.getPaddingStart() + drawableWidth
                    : event.getX() > mGlobalSearchEt.getWidth() - mGlobalSearchEt.getPaddingEnd() - drawableWidth;
            if (!hit) return false;
            if (mGlobalSearchEt.getText().length() > 0) {
                mGlobalSearchEt.setText("");
            } else {
                clearRecentSearches();
            }
            return true;
        });

        mGlobalSearchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        mGlobalSearchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSearchBarEndIcon();
                filterSearch(s.toString());
                if (mRecentSearchesRow != null) {
                    mRecentSearchesRow.setVisibility(
                            s.length() == 0 && !getRecentSearches().isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });

        // ── Ricerche recenti — pillole cliccabili sotto la barra, cosi non serve
        // riscrivere lo stesso termine ogni volta. Visibili solo a campo vuoto. ─────────
        HorizontalScrollView recentScroll = new HorizontalScrollView(this);
        recentScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams recentScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recentScrollLp.topMargin = p6;
        recentScroll.setLayoutParams(recentScrollLp);
        mRecentSearchesRow = new LinearLayout(this);
        mRecentSearchesRow.setOrientation(LinearLayout.HORIZONTAL);
        recentScroll.addView(mRecentSearchesRow);
        rebuildRecentSearchChips();

        // ── Drag handle — below the search bar ────────────────────────────────
        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                Math.round(40 * dp), Math.round(2 * dp));
        handleLp.gravity   = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = Math.round(10 * dp);
        handleLp.bottomMargin = p4;
        handle.setLayoutParams(handleLp);
        try {
            handle.setBackgroundColor(getColor(R.color.obs_primary));
            handle.setAlpha(0.4f);
        } catch (Throwable t) {
            handle.setBackgroundColor(Color.argb(102, 124, 77, 255)); // fallback
        }

        // ── Vertical container (EditText + handle) ────────────────────────────
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.setPadding(p16, p6, p16, 0);
        col.addView(mGlobalSearchEt);
        col.addView(recentScroll);
        col.addView(handle);

        mGlobalSearchContainer.addView(col);

        // ── Results overlay ───────────────────────────────────────────────────
        mSearchResultsRv.setLayoutManager(new LinearLayoutManager(this));
        mSearchResultsRv.setBackgroundColor(ObsidianTheme.bgColor());
        // Senza sfondo opaco il testo "Nessun risultato" resta trasparente e si vede
        // sovrapposto alla tab sottostante invece di coprirla — si confonde con le card
        // dietro invece di leggersi come un vero stato "nessun risultato".
        if (mSearchEmptyTv != null) {
            mSearchEmptyTv.setBackgroundColor(ObsidianTheme.bgColor());
            mSearchEmptyTv.setTextColor(ObsidianTheme.textColor(0x99));
        }
    }

    private void filterSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            mSearchResultsRv.setVisibility(View.GONE);
            if (mSearchEmptyTv != null) mSearchEmptyTv.setVisibility(View.GONE);
            return;
        }
        // Split into words so a multi-word query like "abilita barra batteria" matches an entry
        // whose title/keywords cover those words separately (e.g. title "Icone Batteria" + a
        // "barra batteria" keyword) — matching the whole query as one substring against a single
        // field never found anything unless someone typed the exact title.
        String[] words = query.toLowerCase(Locale.getDefault()).trim().split("\\s+");
        List<SearchEntry> results = new ArrayList<>();
        for (SearchEntry e : mAllSearchItems) {
            boolean allWordsMatch = true;
            for (String word : words) {
                // e.subtitle is NOT matched here on purpose: for nested entries it's a "lives
                // under X" breadcrumb (e.g. "Barra di Stato"), not a description of what the
                // entry does — matching it made searching "barra" surface every single mod
                // filed under that section (clock style, notifications, ...) regardless of
                // relevance. Top-level section entries keep their own topic words as keywords,
                // so they stay findable without this.
                boolean wordMatch = e.title.toLowerCase(Locale.getDefault()).contains(word);
                if (!wordMatch) {
                    for (String kw : e.keywords) {
                        if (kw != null && kw.toLowerCase(Locale.getDefault()).contains(word)) {
                            wordMatch = true;
                            break;
                        }
                    }
                }
                if (!wordMatch) { allWordsMatch = false; break; }
            }
            if (allWordsMatch) results.add(e);
        }
        if (results.isEmpty()) {
            // Hiding the (empty) results RV here would just reveal the tab underneath —
            // show an explicit "no results" state instead so it can't be mistaken for a
            // broad/unfiltered match list.
            mSearchResultsRv.setVisibility(View.GONE);
            if (mSearchEmptyTv != null) {
                mSearchEmptyTv.setText(getString(R.string.search_no_results, query.trim()));
                mSearchEmptyTv.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (mSearchEmptyTv != null) mSearchEmptyTv.setVisibility(View.GONE);
        String finalQuery = query.trim();
        mSearchResultsRv.setAdapter(new SearchResultAdapter(results, entry -> {
            addRecentSearch(finalQuery);
            // Clear search UI
            mGlobalSearchEt.setText("");
            mSearchResultsRv.setVisibility(View.GONE);
            // Switch tab (triggers listener → switchTab + showHomeActionBar)
            popAllBackStack();
            mBottomNav.setSelectedItemId(entry.tabId);
            // Navigate to sub-fragment if applicable
            if (entry.pageSupplier != null) {
                navigateTo(entry.pageSupplier.get(), entry.pageTitle);
            }
        }));
        mSearchResultsRv.setVisibility(View.VISIBLE);
    }

    // ── Ricerche recenti ──────────────────────────────────────────────────────

    private List<String> getRecentSearches() {
        String stored = ObsidianPrefs.getString(KEY_RECENT_SEARCHES, "");
        List<String> list = new ArrayList<>();
        if (!stored.isEmpty()) {
            for (String s : stored.split("\n")) if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    /** Salva la query in cima alla lista (senza duplicati), max {@link #MAX_RECENT_SEARCHES}. */
    private void addRecentSearch(String query) {
        if (query == null || query.isEmpty()) return;
        List<String> list = getRecentSearches();
        list.removeIf(s -> s.equalsIgnoreCase(query));
        list.add(0, query);
        while (list.size() > MAX_RECENT_SEARCHES) list.remove(list.size() - 1);
        ObsidianPrefs.putString(KEY_RECENT_SEARCHES, String.join("\n", list));
        rebuildRecentSearchChips();
    }

    private void rebuildRecentSearchChips() {
        if (mRecentSearchesRow == null) return;
        mRecentSearchesRow.removeAllViews();
        List<String> recents = getRecentSearches();
        float dp = getResources().getDisplayMetrics().density;
        int pH = Math.round(12 * dp), pV = Math.round(6 * dp), margin = Math.round(6 * dp);
        for (String query : recents) {
            TextView chip = new TextView(this);
            chip.setText(query);
            chip.setTextColor(ObsidianTheme.textColor(200));
            chip.setTextSize(13);
            chip.setSingleLine(true);
            chip.setPadding(pH, pV, pH, pV);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20 * dp);
            bg.setColor(ObsidianTheme.textColor(28));
            bg.setStroke(Math.round(1 * dp), ObsidianTheme.textColor(60));
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(margin);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> mGlobalSearchEt.setText(query));
            mRecentSearchesRow.addView(chip);
        }
        boolean showNow = mGlobalSearchEt != null && mGlobalSearchEt.getText().length() == 0 && !recents.isEmpty();
        mRecentSearchesRow.setVisibility(showNow ? View.VISIBLE : View.GONE);
        updateSearchBarEndIcon();
    }

    private void clearRecentSearches() {
        ObsidianPrefs.putString(KEY_RECENT_SEARCHES, "");
        rebuildRecentSearchChips();
    }

    /** Icona X a destra nella barra: svuota il testo se c'è testo, altrimenti svuota la
     *  cronologia (stessa X, stesso posto — solo il significato cambia in base allo stato). */
    private void updateSearchBarEndIcon() {
        if (mGlobalSearchEt == null || mSearchClearIcon == null) return;
        boolean hasText = mGlobalSearchEt.getText().length() > 0;
        boolean hasHistory = !getRecentSearches().isEmpty();
        android.graphics.drawable.Drawable[] cur = mGlobalSearchEt.getCompoundDrawablesRelative();
        mGlobalSearchEt.setCompoundDrawablesRelative(
                cur[0], null, (hasText || hasHistory) ? mSearchClearIcon : null, null);
    }

    /**
     * Central registry of all searchable items across every tab.
     * Keywords are extra terms not visible in the UI but matched during search.
     */
    private List<SearchEntry> buildSearchItems() {
        List<SearchEntry> all = new ArrayList<>();

        // ── DST tab ───────────────────────────────────────────────────────────
        all.add(new SearchEntry(
                getString(R.string.nav_dst_colors),
                getString(R.string.nav_dst_colors_summary),
                R.drawable.ic_drawing, 0xFF7C4DFF, R.id.tab_dst,
                DarkShadowThemeFragment::new, getString(R.string.nav_dst_colors),
                "substratum", "tema", "theme", "accento", "accent",
                "sfondo", "background", "colore", "color", "pin",
                "icone", "notifica", "toast", "corner", "angolo",
                "dialogo", "dialog", "cpb", "rvd", "preset", "dst"));

        all.add(new SearchEntry(
                getString(R.string.nav_system_colors),
                getString(R.string.nav_system_colors_summary),
                R.drawable.ic_palette, 0xFFE91E63, R.id.tab_dst,
                SystemColorsFragment::new, getString(R.string.nav_system_colors),
                "monet", "colore", "color", "accento", "accent",
                "sistema", "system", "material you"));

        all.add(new SearchEntry(
                getString(R.string.section_pin_style),
                getString(R.string.nav_lock_screen_summary),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_dst,
                PinStyleFragment::new, getString(R.string.section_pin_style),
                "schermata di blocco", "lock screen", "codice",
                "password", "numeri", "numbers", "puntini", "dots"));

        all.add(new SearchEntry(
                getString(R.string.section_statusbar_icon_color),
                getString(R.string.section_statusbar_icon_color_summary),
                R.drawable.ic_palette, 0xFFFFB300, R.id.tab_mods,
                ClockDateFragment::new, getString(R.string.section_clock_date),
                "batteria", "battery", "orologio", "clock",
                "icone", "icons", "colore", "color", "barra di stato"));

        // ── Mods tab ──────────────────────────────────────────────────────────
        all.add(new SearchEntry(
                getString(R.string.nav_statusbar),
                getString(R.string.nav_statusbar_summary),
                R.drawable.ic_notifications, 0xFF7C4DFF, R.id.tab_mods,
                StatusbarFragment::new, getString(R.string.nav_statusbar),
                "batteria", "battery", "orologio", "clock", "icone", "icons",
                "luminosità", "brightness", "controllo luminosità", "frecce", "wifi", "mobile",
                "notifiche", "notifications", "stile icone",
                "bluetooth", "doppio tap", "double tap", "spegnere", "spegni", "screen off",
                "popup appunti", "clipboard", "blocca popup"));

        all.add(new SearchEntry(
                getString(R.string.nav_lock_screen_options),
                getString(R.string.nav_lock_buttons_summary),
                R.drawable.ic_lock, 0xFFFF9800, R.id.tab_mods,
                LockScreenOptionsFragment::new, getString(R.string.nav_lock_screen_options),
                "sostituzione", "operatore", "carrier", "gestore", "iliad",
                "lucchetto", "pulsanti", "sos", "menù accensione", "power menu"));

        all.add(new SearchEntry(
                getString(R.string.nav_quick_settings),
                getString(R.string.nav_quick_settings_summary),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                QsFragment::new, getString(R.string.nav_quick_settings),
                "qs", "intestazione", "header", "sfondo", "background",
                "immagine", "image", "orologio", "clock", "data", "date"));

        all.add(new SearchEntry(
                getString(R.string.nav_quick_settings_panel),
                getString(R.string.nav_quick_settings_panel_summary),
                R.drawable.ic_qs, 0xFFE91E63, R.id.tab_mods,
                QuickSettingsFragment::new, getString(R.string.nav_quick_settings_panel),
                "qs", "tile", "riquadri", "trasparenza", "transparency",
                "sfocatura", "blur", "apertura rapida", "pulldown", "widget",
                "dispositivo", "pannello notifiche", "tendina"));

        all.add(new SearchEntry(
                getString(R.string.nav_theme_style),
                getString(R.string.nav_theme_style_summary),
                R.drawable.ic_ui_styles, 0xFF00BCD4, R.id.tab_dst,
                ThemeStyleFragment::new, getString(R.string.nav_theme_style),
                "notifiche", "notifications", "toast", "angoli", "corners",
                "radius", "dialogo", "dialog", "stile", "style"));

        all.add(new SearchEntry(
                getString(R.string.nav_icon_style),
                getString(R.string.nav_icon_style_summary),
                R.drawable.obs_wifi_aurora_signal_4, 0xFF4CAF50, R.id.tab_dst,
                SignalStyleFragment::new, getString(R.string.nav_icon_style),
                "wifi", "wi-fi", "mobile", "segnale", "signal", "icone", "icons"));

        all.add(new SearchEntry(
                getString(R.string.nav_wifi_icons),
                getString(R.string.nav_wifi_icons_summary),
                R.drawable.obs_wifi_aurora_signal_4, 0xFF00BCD4, R.id.tab_dst,
                WifiIconsFragment::new, getString(R.string.nav_wifi_icons),
                "wifi", "wi-fi", "segnale", "signal", "icone", "icons"));

        all.add(new SearchEntry(
                getString(R.string.nav_signal_icons),
                getString(R.string.nav_signal_icons_summary),
                R.drawable.obs_signal_bars_3, 0xFF4CAF50, R.id.tab_dst,
                SignalIconsFragment::new, getString(R.string.nav_signal_icons),
                "mobile", "segnale", "signal", "icone", "icons"));

        all.add(new SearchEntry(
                getString(R.string.nav_battery_icons),
                getString(R.string.nav_battery_icons_summary),
                R.drawable.ic_battery, 0xFFFF9800, R.id.tab_dst,
                BatteryIconFragment::new, getString(R.string.nav_battery_icons),
                "batteria", "battery", "icone", "icons",
                "barra batteria", "battery bar", "barra", "bar",
                "abilita", "enable", "critico", "critical", "avviso", "warning",
                "carica", "charging", "risparmio energetico", "power save"));

        all.add(new SearchEntry(
                getString(R.string.nav_settings_icons),
                getString(R.string.nav_settings_icons_summary),
                R.drawable.ic_settings, 0xFF673AB7, R.id.tab_dst,
                SettingsIconsFragment::new, getString(R.string.nav_settings_icons),
                "pui", "oos", "icone", "icons", "impostazioni", "settings", "pack"));

        all.add(new SearchEntry(
                getString(R.string.nav_navbar_style),
                getString(R.string.nav_navbar_style_summary),
                R.drawable.ic_nav_icon_bg, 0xFF546E7A, R.id.tab_mods,
                NavbarStyleFragment::new, getString(R.string.nav_navbar_style),
                "navbar", "barra di navigazione", "navigation bar", "gesture",
                "pillola", "pill", "indietro", "back"));

        all.add(new SearchEntry(
                getString(R.string.dark_shadow_preset_cpb),
                getString(R.string.nav_cpb_summary),
                R.drawable.arc_progress, 0xFF4CAF50, R.id.tab_dst,
                null, null,   // just switch to DST tab, user taps the card there
                "barra", "caricamento", "loading", "spinner", "progress",
                "progresso", "animazione", "circolare"));

        all.add(new SearchEntry(
                getString(R.string.dark_shadow_preset_rvd),
                getString(R.string.nav_volume_icon_summary),
                R.drawable.ic_sysui_volume, 0xFFFF9800, R.id.tab_mods,
                VolumeStyleFragment::new, getString(R.string.dark_shadow_preset_rvd),
                "volume", "suono", "audio", "slider", "timeout",
                "posizione", "position", "colore", "color"));

        // ── Settings tab ──────────────────────────────────────────────────────
        all.add(new SearchEntry(
                getString(R.string.nav_settings_general),
                getString(R.string.nav_settings_general_summary),
                R.drawable.ic_settings, 0xFF7C4DFF, R.id.tab_settings,
                SettingsGeneralFragment::new, getString(R.string.nav_settings_general),
                "generale", "general", "lingua", "language", "icona", "icon",
                "tema", "theme", "tab predefinito", "default tab",
                "log aggiuntivi", "extra logs", "debug", "bug report"));

        all.add(new SearchEntry(
                getString(R.string.settings_section),
                getString(R.string.nav_settings_backup_summary),
                R.drawable.ic_reset, 0xFF4CAF50, R.id.tab_settings,
                SettingsFragment::new, getString(R.string.settings_section),
                "backup", "ripristina", "restore", "cancella", "clear", "reset"));

        all.add(new SearchEntry(
                getString(R.string.nav_settings_update),
                getString(R.string.nav_settings_update_summary),
                R.drawable.ic_restart_systemui, 0xFF00BCD4, R.id.tab_settings,
                SettingsUpdateFragment::new, getString(R.string.nav_settings_update),
                "aggiornamento", "update", "versione", "version"));

        all.add(new SearchEntry(
                getString(R.string.nav_settings_about),
                getString(R.string.nav_settings_about_summary),
                R.drawable.ic_settings, 0xFFFF9800, R.id.tab_settings,
                SettingsAboutFragment::new, getString(R.string.nav_settings_about),
                "info", "about", "github", "crediti", "credits", "supporto", "traduci"));

        // ── Sotto-schermate — puntano alla destinazione ESATTA, non solo alla card che la
        // contiene ("file", non "cartella"): il sottotitolo indica in che sezione si trova. ──

        all.add(new SearchEntry(
                getString(R.string.nav_aod), getString(R.string.nav_aod_summary),
                R.drawable.ic_clock, 0xFFFF5722, R.id.tab_mods,
                AodFragment::new, getString(R.string.nav_aod),
                "aod", "always-on display", "orologio", "clock", "meteo", "weather", "bordi", "edge lighting"));
        all.add(new SearchEntry(
                getString(R.string.nav_aod_clock), getString(R.string.nav_aod),
                R.drawable.ic_clock, 0xFFFF5722, R.id.tab_mods,
                AodClockFragment::new, getString(R.string.nav_aod_clock),
                "aod", "orologio", "clock", "stile", "style", "margine", "margin"));
        all.add(new SearchEntry(
                getString(R.string.nav_aod_weather), getString(R.string.nav_aod),
                R.drawable.ic_clock, 0xFFFF5722, R.id.tab_mods,
                AodWeatherFragment::new, getString(R.string.nav_aod_weather),
                "aod", "meteo", "weather", "temperatura", "posizione", "location", "margine"));
        all.add(new SearchEntry(
                getString(R.string.nav_aod_edge_lighting), getString(R.string.nav_aod),
                R.drawable.ic_clock, 0xFFFF5722, R.id.tab_mods,
                AodEdgeLightFragment::new, getString(R.string.nav_aod_edge_lighting),
                "aod", "bordi", "edge lighting", "illuminazione"));

        all.add(new SearchEntry(
                getString(R.string.nav_lock_screen), getString(R.string.nav_lock_screen_summary),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_mods,
                LockScreenFragment::new, getString(R.string.nav_lock_screen),
                "schermata di blocco", "lock screen", "sdb"));
        all.add(new SearchEntry(
                getString(R.string.nav_lock_clock_new), getString(R.string.nav_lock_screen),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_mods,
                LockscreenClockFragment::new, getString(R.string.nav_lock_clock_new),
                "sdb", "orologio", "clock", "stile", "style", "margine", "margin"));
        all.add(new SearchEntry(
                getString(R.string.nav_lock_weather), getString(R.string.nav_lock_screen),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_mods,
                LockscreenWeatherFragment::new, getString(R.string.nav_lock_weather),
                "sdb", "meteo", "weather", "temperatura", "posizione", "location", "margine"));
        all.add(new SearchEntry(
                getString(R.string.nav_lock_widgets), getString(R.string.nav_lock_screen),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_mods,
                LockscreenWidgetsFragment::new, getString(R.string.nav_lock_widgets),
                "sdb", "widget"));
        all.add(new SearchEntry(
                getString(R.string.nav_fingerprint_icon), getString(R.string.nav_lock_screen),
                R.drawable.ic_lock, 0xFF4CAF50, R.id.tab_mods,
                FingerprintIconFragment::new, getString(R.string.nav_fingerprint_icon),
                "sdb", "impronta", "fingerprint", "icona", "icon"));

        all.add(new SearchEntry(
                getString(R.string.dst_qs_solid_bg), getString(R.string.nav_quick_settings),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                QsSolidBgFragment::new, getString(R.string.dst_qs_solid_bg),
                "qs", "sfondo", "background", "solido", "solid"));
        all.add(new SearchEntry(
                getString(R.string.qs_header_section), getString(R.string.nav_quick_settings),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                QsHeaderImageFragment::new, getString(R.string.qs_header_section),
                "qs", "intestazione", "header", "immagine", "image", "sfondo", "background"));
        all.add(new SearchEntry(
                getString(R.string.qs_header_clock_section), getString(R.string.nav_quick_settings),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                QsHeaderClockFragment::new, getString(R.string.qs_header_clock_section),
                "qs", "intestazione", "header", "orologio", "clock", "data", "date", "red"));
        all.add(new SearchEntry(
                getString(R.string.qs_header_stock_clock_background_chip_style), getString(R.string.qs_header_clock_section),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                () -> ClockChipStyleFragment.newInstance("qs_header_clock_background_chip"),
                getString(R.string.qs_header_stock_clock_background_chip_style),
                "qs", "chip", "sfondo orologio"));
        all.add(new SearchEntry(
                getString(R.string.qs_header_stock_date_background_chip_style), getString(R.string.qs_header_clock_section),
                R.drawable.ic_qs, 0xFF00BCD4, R.id.tab_mods,
                () -> ClockChipStyleFragment.newInstance("qs_header_date_background_chip"),
                getString(R.string.qs_header_stock_date_background_chip_style),
                "qs", "chip", "sfondo data"));

        all.add(new SearchEntry(
                getString(R.string.nav_clock_style), getString(R.string.nav_statusbar),
                R.drawable.ic_notifications, 0xFF7C4DFF, R.id.tab_mods,
                ClockStyleFragment::new, getString(R.string.nav_clock_style),
                "orologio", "clock", "posizione", "position", "dimensione", "size", "padding"));
        all.add(new SearchEntry(
                getString(R.string.status_bar_clock_background_chip_style_title), getString(R.string.nav_statusbar),
                R.drawable.ic_notifications, 0xFF7C4DFF, R.id.tab_mods,
                () -> ClockChipStyleFragment.newInstance("status_bar_clock_background_chip"),
                getString(R.string.status_bar_clock_background_chip_style_title),
                "orologio", "clock", "chip", "sfondo"));
        all.add(new SearchEntry(
                getString(R.string.nav_clock_date), getString(R.string.nav_statusbar),
                R.drawable.ic_notifications, 0xFF7C4DFF, R.id.tab_mods,
                ClockOraDataFragment::new, getString(R.string.nav_clock_date),
                "orologio", "clock", "data", "date", "formato", "format", "ora"));
        all.add(new SearchEntry(
                getString(R.string.section_statusbar_notifs), getString(R.string.nav_statusbar),
                R.drawable.ic_notifications, 0xFF7C4DFF, R.id.tab_mods,
                StatusbarNotifsFragment::new, getString(R.string.section_statusbar_notifs),
                "notifiche", "notifications"));

        all.add(new SearchEntry(
                getString(R.string.dst_section_preset_sfondo), getString(R.string.nav_dst_colors),
                R.drawable.ic_drawing, 0xFF7C4DFF, R.id.tab_dst,
                DstBackgroundFragment::new, getString(R.string.dst_section_preset_sfondo),
                "dst", "sfondo", "background"));
        all.add(new SearchEntry(
                getString(R.string.dst_section_preset_accent), getString(R.string.nav_dst_colors),
                R.drawable.ic_drawing, 0xFF7C4DFF, R.id.tab_dst,
                DstAccentFragment::new, getString(R.string.dst_section_preset_accent),
                "dst", "accento", "accent", "colore", "color"));

        all.add(new SearchEntry(
                getString(R.string.vol_panel_section), getString(R.string.dark_shadow_preset_rvd),
                R.drawable.ic_sysui_volume, 0xFF3F51B5, R.id.tab_mods,
                VolumePanelFragment::new, getString(R.string.vol_panel_section),
                "volume", "pannello", "panel"));
        all.add(new SearchEntry(
                getString(R.string.vol_panel_colors), getString(R.string.dark_shadow_preset_rvd),
                R.drawable.ic_sysui_volume, 0xFF3F51B5, R.id.tab_mods,
                VolumePanelColorsFragment::new, getString(R.string.vol_panel_colors),
                "volume", "pannello", "panel", "colore", "color"));

        all.add(new SearchEntry(
                getString(R.string.nav_launcher), getString(R.string.nav_launcher_summary),
                R.drawable.ic_recents, 0xFF009688, R.id.tab_mods,
                LauncherFragment::new, getString(R.string.nav_launcher),
                "launcher", "recenti", "recents", "task switcher"));
        all.add(new SearchEntry(
                getString(R.string.dock_background), getString(R.string.nav_launcher),
                R.drawable.ic_recents, 0xFF009688, R.id.tab_mods,
                LauncherDockBackgroundFragment::new, getString(R.string.dock_background),
                "launcher", "dock", "sfondo", "background"));

        all.add(new SearchEntry(
                getString(R.string.nav_misc), getString(R.string.nav_misc_summary),
                R.drawable.ic_settings, 0xFF795548, R.id.tab_mods,
                MiscFragment::new, getString(R.string.nav_misc),
                "varie", "misc", "rotazione", "rotation", "usb"));
        all.add(new SearchEntry(
                getString(R.string.nav_misc_power_menu), getString(R.string.nav_misc),
                R.drawable.ic_settings, 0xFF795548, R.id.tab_mods,
                PowerMenuFragment::new, getString(R.string.nav_misc_power_menu),
                "power menu", "accensione", "spegnimento", "menù accensione"));

        return all;
    }

    private void hideGlobalSearch() {
        if (mHomeHeaderContainer   != null) mHomeHeaderContainer.setVisibility(View.GONE);
        if (mGlobalSearchContainer != null) {
            if (mGlobalSearchEt != null) mGlobalSearchEt.setText("");
            mGlobalSearchContainer.setVisibility(View.GONE);
        }
        if (mSearchResultsRv != null) mSearchResultsRv.setVisibility(View.GONE);
        if (mSearchEmptyTv   != null) mSearchEmptyTv.setVisibility(View.GONE);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void applyNavBarColor() {
        boolean bgOn = ObsidianPrefs.getBoolean("DST_BACKGROUND_on", false);
        if (!bgOn) return;
        int bgColor = ObsidianPrefs.getInt("DST_BACKGROUND", 0xFF1A1A2E) | 0xFF000000;
        mBottomNav.setBackgroundColor(bgColor);
        if (mNavDivider != null) {
            mNavDivider.setBackgroundColor(ColorUtils.adjustColor(bgColor, 25) | 0xFF000000);
        }
    }

    private int resolveDefaultTabId() {
        String value = ObsidianPrefs.getString(KEY_DEFAULT_TAB, "dst");
        return switch (value) {
            case "mods" -> R.id.tab_mods;
            case "settings" -> R.id.tab_settings;
            default -> R.id.tab_dst;
        };
    }

    private void switchTab(int tabId) {
        // Without this, any sub-screens pushed via navigateTo() on the PREVIOUS tab stayed on
        // the back stack — pressing Back after switching tabs could resurface a stale sub-screen
        // from a different tab entirely, with the bottom nav highlight left pointing at the
        // wrong tab too. Every real tab switch should start from that tab's own top level.
        popAllBackStack();

        Fragment fragment;
        if (tabId == R.id.tab_mods)          fragment = new GestioneModsTabFragment();
        else if (tabId == R.id.tab_settings) fragment = new ImpostazioniTabFragment();
        else                                 fragment = new DstTabFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void popAllBackStack() {
        while (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
        }
    }

    private void setSubFragmentActionBar(String title) {
        ActionBar ab = getSupportActionBar();
        if (ab == null) return;
        ab.setCustomView(null);
        ab.setDisplayShowCustomEnabled(false);
        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowTitleEnabled(true);
        ab.setTitle(title);
    }

    private void showHomeActionBar() {
        ActionBar ab = getSupportActionBar();
        if (ab == null) return;
        ab.setDisplayHomeAsUpEnabled(false);
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowCustomEnabled(true);
        ab.setCustomView(getLayoutInflater().inflate(R.layout.actionbar_header, null));
        // Show fixed home header (icon + OBSIDIAN + tagline)
        if (mHomeHeaderContainer != null) {
            mHomeHeaderContainer.removeAllViews();
            getLayoutInflater().inflate(R.layout.item_home_header, mHomeHeaderContainer, true);
            mHomeHeaderContainer.setVisibility(View.VISIBLE);
        }
        // Search now lives only on its own dedicated "Cerca" tab (see showSearchTab()) — the
        // 3 regular tabs no longer show an embedded search bar, so make sure it's off here.
        if (mGlobalSearchContainer != null) mGlobalSearchContainer.setVisibility(View.GONE);
    }

    /** Dedicated "Cerca" tab — the search bar with nothing competing for space above it (no
     *  home logo/tagline), so it never ends up half-covered by the keyboard the way it could
     *  when embedded in the 3 regular tabs. */
    private void showSearchTab() {
        popAllBackStack();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new Fragment())
                .commit();

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setCustomView(null);
            ab.setDisplayShowCustomEnabled(false);
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setDisplayShowTitleEnabled(true);
            ab.setTitle(getString(R.string.tab_search));
        }
        if (mHomeHeaderContainer != null) mHomeHeaderContainer.setVisibility(View.GONE);
        if (mGlobalSearchContainer != null) {
            mGlobalSearchContainer.setVisibility(View.VISIBLE);
            if (mGlobalSearchEt != null && mGlobalSearchEt.getText().length() > 0) {
                filterSearch(mGlobalSearchEt.getText().toString());
            }
        }
        if (mGlobalSearchEt != null) {
            mGlobalSearchEt.requestFocus();
            mGlobalSearchEt.post(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mGlobalSearchEt, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    // ── Color picker ───────────────────────────────────────────────────────────

    public void showColorPickerDialog(int dialogId, int color, boolean alpha,
                                      boolean shades, boolean presets) {
        showColorPickerDialog(dialogId, color, alpha, shades, presets, null);
    }

    /** Come sopra, ma con una palette di preset specifica al posto di quella Material
     *  di default della libreria (es. sfumature derivate dal colore sfondo attuale). */
    public void showColorPickerDialog(int dialogId, int color, boolean alpha,
                                      boolean shades, boolean presets, int[] presetColors) {
        ColorPickerDialog.Builder builder = ColorPickerDialog.newBuilder()
                .setDialogId(dialogId)
                .setColor(color)
                .setShowAlphaSlider(alpha)
                .setShowColorShades(shades);
        if (presetColors != null) builder.setPresets(presetColors);
        builder.show(this);
    }

    @Override public void onColorSelected(int dialogId, int color) {
        EventBus.getDefault().post(new ColorSelectedEvent(dialogId, color));
    }

    @Override public void onDialogDismissed(int dialogId) {}

    // ── Search data model ──────────────────────────────────────────────────────

    /** A single searchable destination across all tabs. */
    private static class SearchEntry {
        final String title;
        final String subtitle;
        final @DrawableRes int iconRes;
        final int accentColor;
        final int tabId;
        /** Null means "just switch to the tab, no sub-fragment push". */
        final Supplier<Fragment> pageSupplier;
        final String pageTitle;
        final String[] keywords;

        SearchEntry(String title, String subtitle, @DrawableRes int iconRes,
                    int accentColor, int tabId,
                    Supplier<Fragment> pageSupplier, String pageTitle,
                    String... keywords) {
            this.title        = title;
            this.subtitle     = subtitle;
            this.iconRes      = iconRes;
            this.accentColor  = accentColor;
            this.tabId        = tabId;
            this.pageSupplier = pageSupplier;
            this.pageTitle    = pageTitle;
            this.keywords     = keywords != null ? keywords : new String[0];
        }
    }

    // ── Search results adapter ─────────────────────────────────────────────────

    /** Reuses item_nav.xml for a consistent card look. */
    private static class SearchResultAdapter
            extends RecyclerView.Adapter<SearchResultAdapter.VH> {

        interface OnClick { void onClick(SearchEntry e); }

        private final List<SearchEntry> items;
        private final OnClick listener;

        SearchResultAdapter(List<SearchEntry> items, OnClick listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nav, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SearchEntry e  = items.get(pos);
            int accent     = e.accentColor;
            float dp       = h.itemView.getContext().getResources().getDisplayMetrics().density;

            int strokeColor = Color.argb(204,
                    Color.red(accent), Color.green(accent), Color.blue(accent));
            h.card.setStrokeColor(strokeColor);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(14 * dp);
            bg.setColor(Color.argb(46,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            bg.setStroke(Math.round(1.5f * dp), accent);
            h.iconContainer.setBackground(bg);

            h.icon.setImageResource(e.iconRes);
            h.icon.setImageTintList(ColorStateList.valueOf(accent));

            h.card.setCardBackgroundColor(ObsidianTheme.cardColor());
            h.title.setText(e.title);
            h.title.setTextColor(ObsidianTheme.textColor());
            if (e.subtitle != null && !e.subtitle.isEmpty()) {
                h.subtitle.setVisibility(View.VISIBLE);
                h.subtitle.setText(e.subtitle);
                h.subtitle.setTextColor(ObsidianTheme.textColor(0x99));
            } else {
                h.subtitle.setVisibility(View.GONE);
            }
            if (h.chevron != null) h.chevron.setTextColor(ObsidianTheme.textColor(0x66));

            h.itemView.setOnClickListener(v -> listener.onClick(e));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final FrameLayout iconContainer;
            final ImageView icon;
            final TextView title, subtitle, chevron;

            VH(View v) {
                super(v);
                card          = (MaterialCardView) v;
                iconContainer = v.findViewById(R.id.navIconContainer);
                icon          = v.findViewById(R.id.navIcon);
                title         = v.findViewById(R.id.navTitle);
                subtitle      = v.findViewById(R.id.navSubtitle);
                chevron       = v.findViewById(R.id.navChevron);
            }
        }
    }
}
