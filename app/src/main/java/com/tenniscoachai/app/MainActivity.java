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

    // FASE 200M: NON usare WebView.goBack() sull'app: puo' tornare
    // al login Google o a una vecchia pagina pur restando autenticati.
    // I tasti HTML, il gesto Android e il tasto del browser useranno tcaiBack().
    private void handleTennisCoachBack() {
        if (webView == null) return;

        Uri current = null;
        try {
            String url = webView.getUrl();
            if (url != null) current = Uri.parse(url);
        } catch (Exception ignored) { }

        String host = current == null ? null : current.getHost();
        String path = current == null ? null : current.getPath();
        boolean official = host != null && (
                "tenniscoachai.it".equalsIgnoreCase(host) ||
                "www.tenniscoachai.it".equalsIgnoreCase(host) ||
                "ai-tennis-coach.netlify.app".equalsIgnoreCase(host) ||
                host.toLowerCase(java.util.Locale.ROOT).endsWith(".netlify.app")
        );
        boolean appPath = path == null || path.isEmpty() ||
                "/".equals(path) || "/app".equals(path) ||
                path.startsWith("/app/");

        if (official && appPath) {
            // Il risultato viene elaborato nell'attuale WebView.
            // Se JS non e' pronto, non chiudere comunque l'Activity.
            webView.evaluateJavascript(
                    "(function(){" +
                    "try{" +
                    "if(typeof window.tcaiBack==='function'){window.tcaiBack();return true;}" +
                    "if(typeof window.home==='function'){window.home();return true;}" +
                    "}catch(e){console.warn('TCAI Android Back',e);}" +
                    "return false;" +
                    "})()",
                    null
            );
            return;
        }

        // Fuori dall'app ufficiale (es. autorizzazione Google):
        // torna indietro se possibile, altrimenti rientra nell'app.
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            webView.loadUrl("https://tenniscoachai.it/app/");
        }
    }
}
