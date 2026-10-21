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
import androidx.browser.customtabs.CustomTabsIntent;

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

        root.addView(
            webView,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );

        setContentView(root);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // GESTIONE TASTIERA SAMSUNG / ANDROID 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            getWindow().setDecorFitsSystemWindows(false);

            root.setOnApplyWindowInsetsListener(
                (view, insets) -> {

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

                    int bottom = Math.max(
                        nav.bottom,
                        ime.bottom
                    );

                    if (
                        view.getPaddingTop() != system.top
                        || view.getPaddingBottom() != bottom
                    ) {
                        view.setPadding(
                            0,
                            system.top,
                            0,
                            bottom
                        );
                    }

                    return insets;
                }
            );

            root.requestApplyInsets();

        } else {

            getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        // SELETTORE VIDEO
        fileChooserLauncher = registerForActivityResult(
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
