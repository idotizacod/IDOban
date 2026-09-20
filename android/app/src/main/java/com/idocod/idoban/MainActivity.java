package com.idocod.idoban;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de cualquier widget, la web recarga su estado (jack/bucket)
        // desde el JSON compartido por si el widget lo avanzó/modificó fuera.
        reloadFromNative("openBucketDirect()");
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshAllWidgets();
    }

    private void refreshAllWidgets() {
        IdobanWidgetProvider.refreshAll(this);
        TodoListWidgetProvider.refreshAll(this);
    }

    /** Maneja clics de los widgets (proyecto del widget 1, bucket del widget 2). */
    private void handleWidgetIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if ("com.idocod.idoban.OPEN_BUCKET".equals(action)) {
            // Widget 2 · ir al bucket (tabla todo) dentro de la app
            reloadFromNative("openBucketDirect()");
            return;
        }
        String projectId = intent.getStringExtra("projectId");
        if (projectId != null) {
            reloadFromNative("openProjectDirect('" + projectId.replace("'", "\\'") + "')");
        }
    }

    private void reloadFromNative(String js) {
        if (getBridge() == null || getBridge().getWebView() == null) return;
        getBridge().getWebView().post(() -> {
            try {
                getBridge().getWebView().evaluateJavascript(js, null);
            } catch (Exception ignored) {}
        });
    }
}
