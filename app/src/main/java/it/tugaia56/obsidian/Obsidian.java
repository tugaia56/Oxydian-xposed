package it.tugaia56.obsidian;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.UserManager;
import com.topjohnwu.superuser.Shell;
import it.tugaia56.obsidian.utils.UpdateScheduler;
public class Obsidian extends Application {
    @SuppressLint("StaticFieldLeak") private static Context appContext;
    private static Obsidian instance;
    static {
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(20));
    }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        appContext = createDeviceProtectedStorageContext();
        // Prima dello sblocco (BFU, es. subito dopo un riavvio) WorkManager non è ancora
        // inizializzato — il suo androidx.startup.InitializationProvider non parte finché lo
        // storage credential-encrypted non è disponibile, e WorkManager.getInstance() lancia
        // un'eccezione reale (non un semplice ritardo). Confermato dal vivo: crash-loop ad ogni
        // avvio finché l'utente non sblocca il telefono. Non critico rimandare: il lavoro
        // periodico persiste già da solo tra i riavvii, questa chiamata è solo un allineamento
        // difensivo ai pref correnti — il prossimo avvio normale (a sblocco avvenuto) lo rifà.
        if (isUserUnlocked()) {
            try {
                UpdateScheduler.ensureNotificationChannel(this);
                UpdateScheduler.reschedule(this);
            } catch (Throwable ignored) {
                // Belt-and-suspenders: qualunque altro motivo imprevisto di fallimento qui non
                // deve mai far crashare l'intera app all'avvio.
            }
        }
    }
    private boolean isUserUnlocked() {
        try {
            UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
            return um == null || um.isUserUnlocked();
        } catch (Throwable t) {
            return true; // in dubbio, prova comunque (il try/catch sopra copre un fallimento reale)
        }
    }
    public static Obsidian get() { return instance; }
    public static Context getAppContext() { return appContext; }
}
