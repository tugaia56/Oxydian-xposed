# Oxydian

🌐 [English](README.md) · **Italiano**

**Oxydian** è un modulo Xposed/LSPosed per dispositivi OnePlus/OPPO (OxygenOS / ColorOS) con root. Trasforma il sistema in un tema scuro completo e coerente, non solo un filtro colore, e aggiunge molte mod di sistema.

Gran parte delle mod di sistema arriva dal lavoro di **Oxygen Customizer** di Luigi (@LC9889), qui integrato, adattato e ampliato.

## App compagna: Oxydian Theme

I veri **temi overlay** (temi per le app, Dark Shadow per Impostazioni / SystemUI / Launcher / app OnePlus, icone Wi-Fi / segnale / navigazione, tastierino PIN, barra di progresso, colori di accento e sfondo) stanno in un'app separata che **non** richiede Xposed:

➡️ **[Oxydian Theme](https://github.com/tugaia56/Oxydian-Theme/releases/latest)**

Oxydian legge accento e sfondo scelti in Oxydian Theme, quindi conviene installare le due app insieme.

## Caratteristiche

- **Barra di stato** — icone, orologio, opzioni notifiche, nascondi l'icona di una SIM, stili batteria
- **Schermata di blocco** — widget, orologio, meteo, copertina album, icona impronta
- **Always-On Display** — illuminazione bordi, orologio, meteo
- **Impostazioni Rapide** — sfondo riquadri, immagine e orologio dell'intestazione, widget, trasparenza e sfocatura
- **Pannello notifiche** — stili, texture, bordi, angoli regolabili
- **Pannello volume** — colori e stile
- **Menù Accensione** — colori, sfondi, riavvio avanzato con autenticazione
- **Launcher** — colonne, cartelle, dock, disposizione della home
- **Varie** — navigazione a gesture, abilita screenshot, CorePatch e altro
- **Controllo aggiornamenti** automatico

## Requisiti

- Dispositivo OnePlus / OPPO con **OxygenOS** (testato su OnePlus 12, OxygenOS 16.1), Android 12+
- **Root** (KernelSU / KernelSU Next o Magisk)
- **LSPosed** (o un altro framework Xposed) con Oxydian attivo negli ambiti di sistema

## Compatibilità con il tema

Oxydian è compatibile al 100% con il tema scuro. Il tema chiaro è supportato per l'interfaccia dell'app stessa, ma alcune mod che cambiano l'aspetto del sistema (stili notifiche, toast, dialogo) sono pensate e testate solo per il tema scuro: con il sistema in chiaro restano disattivate e l'aspetto stock di OxygenOS non viene toccato.

Per la migliore esperienza usa Oxydian con Android in tema scuro.

## Compilare

```
./gradlew assembleDebug
```

La release viene compilata dal flusso GitHub quando si invia un tag `vX.Y.Z` (invia anche changelog e APK su Telegram, se configurato).

## Crediti

- **Luigi (@LC9889)** — Oxygen Customizer, la base di gran parte delle mod
- **LuckyTool**, **CorePatch**, **DisableFlagSecure** — funzioni integrate da questi progetti
- **Claude** (Anthropic) — assistente IA usato durante tutto lo sviluppo
