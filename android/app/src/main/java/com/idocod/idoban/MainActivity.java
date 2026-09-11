package com.idocod.idoban;

import android.content.Intent;
import android.content.SharedPreferences;
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
    public void onPause() {
        super.onPause();
        // Refresca el widget con datos reales al salir de la app (no esperar
        // el ciclo de 30 min de updatePeriodMillis).
        refreshWidget();
    }

    private void refreshWidget() {
        try {
            Intent update = new Intent(this, IdobanWidgetProvider.class);
            update.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetIds(new android.content.ComponentName(this, IdobanWidgetProvider.class));
            update.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(update);
        } catch (Exception ignored) {}
    }

    private void handleWidgetIntent(Intent intent) {
        if (intent == null) return;
        String projectId = intent.getStringExtra("projectId");
        if (projectId != null && getBridge() != null && getBridge().getWebView() != null) {
            String js = "if(window.openProjectDirect) window.openProjectDirect('" + projectId.replace("'", "\\'") + "'); else location.hash='project=" + projectId.replace("'", "\\'") + "';";
            getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null));
        }
    }
}
