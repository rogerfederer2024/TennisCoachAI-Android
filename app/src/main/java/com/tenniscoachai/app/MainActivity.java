
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
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TASTIERA ANDROID
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        // WEBVIEW E CONTENITORE
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // GESTIONE TASTIERA SAMSUNG / ANDROID 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            getWindow().setDecorFitsSystemWindows(false);

            root.setOnApplyWindowInsetsListener((view, insets) -> {

                Insets system = insets.getInsets(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.displayCutout()
                );

                Insets nav = insets.getInsets(
                        WindowInsets.Type.navigationBars()
                );

                Insets ime = insets.getInsets(
                        WindowInsets.Type.ime()
                );

                int bottom = Math.max(nav.bottom, ime.bottom);

                if (view.getPaddingTop() != system.top
                        || view.getPaddingBottom() != bottom) {

                    view.setPadding(
                            0,
                            system.top,
                            0,
                            bottom
                    );
                }

                return insets;
            });

            root.requestApplyInsets();

        } else {

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        // SELETTORE VIDEO
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

                                        int count = data.getClipData()
                                                .getItemCount();

                                        results = new Uri[count];

                                        for (int i = 0; i < count; i++) {

                                            results[i] = data.getClipData()
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

        // IMPOSTAZIONI WEBVIEW
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        // COOKIE GOOGLE LOGIN / SUPABASE
        CookieManager cookieManager = CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            cookieManager.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }

        // SALVATAGGIO FOTO ANDROID
        webView.addJavascriptInterface(
                new AndroidImageSaver(),
                "AndroidImageSaver"
        );

        // SELETTORE VIDEO
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

                        filePathCallback = newFilePathCallback;

                        Intent intent = new Intent(
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

                            filePathCallback.onReceiveValue(null);
                            filePathCallback = null;

                            return true;
                        }
                    }
                }
        );

        // NAVIGAZIONE WEBVIEW
        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url) {

                        super.onPageFinished(view, url);

                        // Download immagini data:image
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

                        view.evaluateJavascript(javascript, null);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        return handleUrl(request.getUrl());
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        return handleUrl(Uri.parse(url));
                    }

                    private boolean handleUrl(Uri uri) {

                        String host = uri.getHost();

                        if (host == null) {
                            return false;
                        }

                        host = host.toLowerCase();

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

                        try {

                            Intent intent = new Intent(
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

        // TENNIS COACH AI - ACCESSO DIRETTO ALL'APP
        webView.loadUrl("https://tenniscoachai.it/app/");
    }

    // SALVATAGGIO FOTO
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

                String header = dataUrl.substring(0, comma);
                String payload = dataUrl.substring(comma + 1);

                byte[] imageBytes = Base64.decode(
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

                String fileName = sanitizeFileName(
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

    // SALVA IMMAGINI NEL DISPOSITIVO
    private void saveImageToDevice(
            byte[] imageBytes,
            String fileName,
            String mimeType) throws Exception {

        ContentValues values = new ContentValues();

        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                fileName
        );

        values.put(
                MediaStore.Images.Media.MIME_TYPE,
                mimeType
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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

        Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            throw new Exception(
                    "Impossibile creare il file immagine"
            );
        }

        try (
                OutputStream outputStream =
                        getContentResolver().openOutputStream(uri)
        ) {

            if (outputStream == null) {
                throw new Exception(
                        "Impossibile aprire il file"
                );
            }

            outputStream.write(imageBytes);
            outputStream.flush();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            ContentValues completed = new ContentValues();

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

    // NOME FILE SICURO
    private String sanitizeFileName(
            String requestedName,
            String extension) {

        String name = requestedName;

        if (name == null || name.trim().isEmpty()) {

            name = "TennisCoachAI_"
                    + System.currentTimeMillis()
                    + extension;
        }

        name = name.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_"
        );

        String lower = name.toLowerCase();

        if (
                !lower.endsWith(".jpg")
                        && !lower.endsWith(".jpeg")
                        && !lower.endsWith(".png")
        ) {
            name += extension;
        }

        return name;
    }

    // TASTO INDIETRO ANDROID
    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
