package it.tugaia56.obsidian.utils.overlay.compiler;

import android.os.Build;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.utils.ModuleConstants;

/** Porting di OC's CompilerUtil — genera il contenuto di AndroidManifest.xml per un
 *  overlay e i suoi metadati/nome/categoria, semplificato: Obsidian ha un solo
 *  sottosistema overlay-compilato per ora (icon pack Impostazioni). */
public class CompilerUtil {

    private static final String TAG = CompilerUtil.class.getSimpleName();
    private static final String PREFIX = "ObsidianComponent";

    public static String createManifestContent(String overlayName, String targetPackage) {
        try {
            String category = getCategory(overlayName);
            if (!overlayName.startsWith(PREFIX)) overlayName = PREFIX + overlayName;
            if (!overlayName.endsWith(".overlay")) overlayName = overlayName + ".overlay";

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = documentBuilder.newDocument();
            Element rootElement = document.createElement("manifest");
            rootElement.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android");
            rootElement.setAttribute("package", overlayName);
            rootElement.setAttribute("android:versionName", "v" + BuildConfig.VERSION_NAME);
            // Senza versionCode, PackageManager considera due build successive con lo stesso
            // nome/versionName/firma "la stessa versione" e può riusare il PackageSetting
            // cache (incluso il targetPackage) invece di ri-parsare il manifest — osservato
            // 2026-09-27 dopo il rename applicationId: la SIP2 ricompilata con target nuovo
            // restava STATE_MISSING_TARGET (target vecchio in cache) anche dopo reboot.
            // Un valore che cresce sempre forza PMS a trattarla come un aggiornamento reale.
            rootElement.setAttribute("android:versionCode", String.valueOf(System.currentTimeMillis() / 1000));

            Element usesSdkElement = document.createElement("uses-sdk");
            usesSdkElement.setAttribute("android:minSdkVersion", String.valueOf(BuildConfig.MIN_SDK_VERSION));
            usesSdkElement.setAttribute("android:targetSdkVersion", String.valueOf(Build.VERSION.SDK_INT));
            rootElement.appendChild(usesSdkElement);

            Element overlayElement = document.createElement("overlay");
            overlayElement.setAttribute("android:category", category);
            overlayElement.setAttribute("android:priority", String.valueOf(1));
            overlayElement.setAttribute("android:targetPackage", targetPackage);
            overlayElement.setAttribute("android:isStatic", "false");
            rootElement.appendChild(overlayElement);

            Element applicationElement = document.createElement("application");
            applicationElement.setAttribute("android:label", overlayName.replace(".overlay", ""));
            applicationElement.setAttribute("allowBackup", "false");
            applicationElement.setAttribute("android:hasCode", "false");

            final HashMap<String, String> metadataNameToValueMap = new HashMap<>();
            metadataNameToValueMap.put(ModuleConstants.METADATA_OVERLAY_PARENT, BuildConfig.APPLICATION_ID);
            metadataNameToValueMap.put(ModuleConstants.METADATA_OVERLAY_TARGET, targetPackage);
            metadataNameToValueMap.put(ModuleConstants.METADATA_THEME_VERSION, String.valueOf(BuildConfig.VERSION_CODE));
            metadataNameToValueMap.put(ModuleConstants.METADATA_THEME_CATEGORY, category);

            metadataNameToValueMap.forEach((key, value) -> {
                Element element = document.createElement("meta-data");
                element.setAttribute("android:name", key);
                element.setAttribute("android:value", value);
                applicationElement.appendChild(element);
            });

            rootElement.appendChild(applicationElement);
            document.appendChild(rootElement);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            Source domSource = new DOMSource(document);
            StringWriter outWriter = new StringWriter();
            Result streamResult = new StreamResult(outWriter);
            transformer.transform(domSource, streamResult);

            return outWriter.getBuffer().toString();
        } catch (ParserConfigurationException | TransformerException e) {
            Log.i(TAG, "Failed to create manifest for " + overlayName, e);
        }
        return "";
    }

    public static String getOverlayName(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        return fileName.replaceAll(PREFIX + "|-unsigned|-unaligned|.apk", "");
    }

    private static String getCategory(String pkgName) {
        String category = ModuleConstants.OVERLAY_CATEGORY_PREFIX;
        pkgName = pkgName.replace(PREFIX, "").replace(".overlay", "");
        String base = pkgName.toLowerCase().replaceAll("\\d", "");
        if (base.equals("sst")) return category + "settings_theme";
        if (base.equals("sut")) return category + "systemui_theme";
        return category + "settings_icon_pack_" + base;
    }
}
