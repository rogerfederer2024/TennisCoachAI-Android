package com.tenniscoachai.app;

import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "https://tenniscoachai.it/";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        /*
         * SAFE AREA SUPERIORE
         *
         * Impedisce alla parte superiore di Tennis Coach AI
         * di finire sotto la status bar o sotto il foro/notch
         * della fotocamera.
         *
         * Non aggiungiamo padding inferiore, così la barra
         * inferiore dell'app rimane invariata.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            webView.setOnApplyWindowInsetsListener((View view, WindowInsets windowInsets) -> {

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
            });

            webView.requestApplyInsets();

        } else {

            webView.setOnApplyWindowInsetsListener((View view, WindowInsets windowInsets) -> {

                int topInset = windowInsets.getSystemWindowInsetTop();

                view.setPadding(
                        0,
                        topInset,
                        0,
                        0
                );

                return windowInsets;
            });

            webView.requestApplyInsets();
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Mantiene correttamente i cookie durante
        // autenticazione Google / Supabase nella WebView.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Salva i cookie dopo i passaggi di login/callback.
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();
                String host = uri.getHost();

                if (host == null) {
                    return false;
                }

                // Tennis Coach AI resta dentro l'app.
                if (host.equals("tenniscoachai.it")
                        || host.equals("www.tenniscoachai.it")
                        || host.equals("ai-tennis-coach.netlify.app")
                        || host.endsWith(".netlify.app")) {

                    return false;
                }

                // Google / Supabase restano nella WebView
                // per completare correttamente l'autenticazione.
                if (host.equals("accounts.google.com")
                        || host.endsWith(".google.com")
                        || host.endsWith(".googleapis.com")
                        || host.endsWith(".supabase.co")) {

                    return false;
                }

                // Gli altri link vengono aperti esternamente.
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return true;

                } catch (Exception e) {
                    return false;
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        }

        /*
         * Pulsante INDIETRO Android.
         * Se esiste una pagina precedente nella WebView torna indietro,
         * altrimenti chiude l'app.
         */
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }
}
