package com.tenniscoachai.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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

                                    // Nel caso Android restituisca più file
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

                                        // Singolo video
                                        results = new Uri[]{
                                                data.getData()
                                        };
                                    }
                                }
                            }

                            // Restituisce il video direttamente
                            // alla pagina Analisi Video AI
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
        // SELETTORE VIDEO
        // =========================================================
        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> newFilePathCallback,
                            FileChooserParams fileChooserParams) {

                        // Chiude una eventuale richiesta precedente
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

                        // Mostra solamente video
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
        //
        // Non utilizziamo restoreState() durante il ritorno
        // dal selettore video.
        // =========================================================
        webView.loadUrl(
                "https://tenniscoachai.it/"
        );
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
