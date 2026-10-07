package com.tenniscoachai.app;

import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // Callback usato dalla pagina web per ricevere il video selezionato
    private ValueCallback<Uri[]> filePathCallback;

    // Selettore file Android
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =========================================================
        // CREA WEBVIEW
        // =========================================================
        webView = new WebView(this);
        setContentView(webView);

        // =========================================================
        // SAFE AREA ANDROID
        // Evita sovrapposizione con status bar / fotocamera
        // =========================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            webView.setOnApplyWindowInsetsListener(
                    (View view, WindowInsets windowInsets) -> {

                        Insets safeInsets = windowInsets.getInsets(
                                WindowInsets.Type.statusBars()
                                        | WindowInsets.Type.displayCutout()
                        );

                        view.setPadding(
                                0,
                                safeInsets.top,
                                0,
                                0
                        );

                        return windowInsets;
                    }
            );

            webView.requestApplyInsets();

        } else {

            webView.setOnApplyWindowInsetsListener(
                    (View view, WindowInsets windowInsets) -> {

                        int topInset =
                                windowInsets.getSystemWindowInsetTop();

                        view.setPadding(
                                0,
                                topInset,
                                0,
                                0
                        );

                        return windowInsets;
                    }
            );

            webView.requestApplyInsets();
        }

        // =========================================================
        // RISULTATO DEL SELETTORE VIDEO
        // =========================================================
        fileChooserLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (filePathCallback == null) {
                                return;
                            }

                            Uri[] results = null;

                            if (result.getResultCode() == RESULT_OK) {

                                Intent data = result.getData();

                                if (data != null) {

                                    if (data.getClipData() != null) {

                                        int count =
                                                data.getClipData()
                                                        .getItemCount();

                                        results = new Uri[count];

                                        for (int i = 0; i < count; i++) {

                                            results[i] =
                                                    data.getClipData()
                                                            .getItemAt(i)
                                                            .getUri();
                                        }

                                    } else if (data.getData() != null) {

                                        results = new Uri[]{
                                                data.getData()
                                        };
                                    }
                                }
                            }

                            filePathCallback.onReceiveValue(results);
                            filePathCallback = null;
                        }
                );

        // =========================================================
        // IMPOSTAZIONI WEBVIEW
        // =========================================================
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        // =========================================================
        // COOKIE
        // Necessari per Google Login e Supabase
        // =========================================================
        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            cookieManager.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }

        // =========================================================
        // BRIDGE ANDROID PER SALVARE LE FOTO
        // =========================================================
        webView.addJavascriptInterface(
                new AndroidImageSaver(),
                "AndroidImageSaver"
        );

        // =========================================================
        // SELETTORE VIDEO
        // =========================================================
        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> newFilePathCallback,
                            FileChooserParams fileChooserParams) {

                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                        }

                        filePathCallback =
                                newFilePathCallback;

                        Intent intent =
                                new Intent(
                                        Intent.ACTION_OPEN_DOCUMENT
                                );

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        intent.setType("video/*");

                        intent.putExtra(
                                Intent.EXTRA_ALLOW_MULTIPLE,
                                false
                        );

                        try {

                            fileChooserLauncher.launch(intent);
                            return true;

                        } catch (ActivityNotFoundException e) {

                            filePathCallback = null;
                            return false;
                        }
                    }
                }
        );

        // =========================================================
        // NAVIGAZIONE WEBVIEW
        // =========================================================
        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url) {

                        super.onPageFinished(view, url);

                        /*
                         * Tennis Coach AI genera le immagini della
                         * Video Analisi come data:image/jpeg...
                         *
                         * Android WebView non gestisce correttamente
                         * il normale <a download>.
                         *
                         * Qui intercettiamo il click e passiamo
                         * l'immagine al bridge Android.
                         */

                        String javascript =
                                "(function(){" +
                                "if(window.__tcaiAndroidImageDownload)return;" +
                                "window.__tcaiAndroidImageDownload=true;" +

                                "var originalClick=" +
                                "HTMLAnchorElement.prototype.click;" +

                                "HTMLAnchorElement.prototype.click=function(){" +

                                "try{" +

                                "var href=this.href||'';" +

                                "if(this.download && " +
                                "href.indexOf('data:image/')===0 && " +
                                "window.AndroidImageSaver){" +

                                "window.AndroidImageSaver.saveImage(" +
                                "href," +
                                "this.download||'TennisCoachAI.jpg'" +
                                ");" +

                                "return;" +
                                "}" +

                                "}catch(e){}" +

                                "return originalClick.call(this);" +

                                "};" +
                                "})();";

                        view.evaluateJavascript(
                                javascript,
                                null
                        );
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        return handleUrl(
                                request.getUrl()
                        );
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        return handleUrl(
                                Uri.parse(url)
                        );
                    }

                    private boolean handleUrl(Uri uri) {

                        String host = uri.getHost();

                        if (host == null) {
                            return false;
                        }

                        host = host.toLowerCase();

                        // Questi domini rimangono dentro l'app
                        if (
                                host.equals("tenniscoachai.it")
                                        || host.endsWith(".tenniscoachai.it")
                                        || host.equals("ai-tennis-coach.netlify.app")
                                        || host.endsWith(".netlify.app")
                                        || host.equals("accounts.google.com")
                                        || host.endsWith(".google.com")
                                        || host.endsWith(".googleusercontent.com")
                                        || host.endsWith(".supabase.co")
                        ) {

                            return false;
                        }

                        // Altri link: browser/app esterna
                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            uri
                                    );

                            startActivity(intent);

                        } catch (Exception ignored) {
                        }

                        return true;
                    }
                }
        );

        // =========================================================
        // APRE TENNIS COACH AI
        // =========================================================
        webView.loadUrl(
                "https://tenniscoachai.it/"
        );
    }

    // =============================================================
    // SALVATAGGIO FOTO NATIVO ANDROID
    // =============================================================
    private class AndroidImageSaver {

        @JavascriptInterface
        public void saveImage(
                String dataUrl,
                String requestedName) {

            try {

                if (dataUrl == null ||
                        !dataUrl.startsWith("data:image/")) {
                    return;
                }

                int comma = dataUrl.indexOf(',');

                if (comma < 0) {
                    return;
                }

                String header =
                        dataUrl.substring(0, comma);

                String payload =
                        dataUrl.substring(comma + 1);

                byte[] imageBytes =
                        Base64.decode(
                                payload,
                                Base64.DEFAULT
                        );

                String mimeType =
                        header.contains("image/png")
                                ? "image/png"
                                : "image/jpeg";

                String extension =
                        mimeType.equals("image/png")
                                ? ".png"
                                : ".jpg";

                String fileName =
                        sanitizeFileName(
                                requestedName,
                                extension
                        );

                saveImageToDevice(
                        imageBytes,
                        fileName,
                        mimeType
                );

                runOnUiThread(
                        () -> Toast.makeText(
                                MainActivity.this,
                                "Foto salvata",
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(
                        () -> Toast.makeText(
                                MainActivity.this,
                                "Impossibile salvare la foto",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }
    }

    // =============================================================
    // SALVA NELLA GALLERIA / IMMAGINI
    // =============================================================
    private void saveImageToDevice(
            byte[] imageBytes,
            String fileName,
            String mimeType) throws Exception {

        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                fileName
        );

        values.put(
                MediaStore.Images.Media.MIME_TYPE,
                mimeType
        );

        /*
         * L'app ha minSdk 24.
         *
         * Su Android 10+ possiamo usare RELATIVE_PATH
         * senza richiedere permessi di archiviazione.
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES
                            + "/Tennis Coach AI"
            );

            values.put(
                    MediaStore.Images.Media.IS_PENDING,
                    1
            );
        }

        Uri uri =
                getContentResolver().insert(
                        MediaStore.Images.Media
                                .EXTERNAL_CONTENT_URI,
                        values
                );

        if (uri == null) {
            throw new Exception(
                    "Impossibile creare il file immagine"
            );
        }

        try (
                OutputStream outputStream =
                        getContentResolver()
                                .openOutputStream(uri)
        ) {

            if (outputStream == null) {
                throw new Exception(
                        "Impossibile aprire il file"
                );
            }

            outputStream.write(imageBytes);
            outputStream.flush();
        }

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            ContentValues completed =
                    new ContentValues();

            completed.put(
                    MediaStore.Images.Media.IS_PENDING,
                    0
            );

            getContentResolver().update(
                    uri,
                    completed,
                    null,
                    null
            );
        }
    }

    // =============================================================
    // NOME FILE SICURO
    // =============================================================
    private String sanitizeFileName(
            String requestedName,
            String extension) {

        String name = requestedName;

        if (name == null ||
                name.trim().isEmpty()) {

            name =
                    "TennisCoachAI_"
                            + System.currentTimeMillis()
                            + extension;
        }

        name = name.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_"
        );

        String lower =
                name.toLowerCase();

        if (
                !lower.endsWith(".jpg")
                        && !lower.endsWith(".jpeg")
                        && !lower.endsWith(".png")
        ) {

            name += extension;
        }

        return name;
    }

    // =============================================================
    // TASTO INDIETRO ANDROID
    // =============================================================
    @Override
    public void onBackPressed() {

        if (
                webView != null
                        && webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
