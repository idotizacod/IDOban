package com.idocod.idoban;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

public class IdobanWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_BOARD = "idoban_board_v1";
    private static final String PREFS_RECENT = "idoban_recent_v1";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_idoban);
            updateWidget(context, views);
            appWidgetManager.updateAppWidget(widgetId, views);
        }
    }

    private void updateWidget(Context context, RemoteViews views) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            // Intenta leer board y recientes desde CapacitorStorage (si se sincronizó) o fallback demo
            String boardJson = prefs.getString(PREFS_BOARD, null);
            String recentJson = prefs.getString(PREFS_RECENT, null);
            // Fallback: intenta WebView localStorage vía mismo prefs sin prefijo (algunas versiones)
            if (boardJson == null) boardJson = prefs.getString("idoban_board_v1", null);
            if (recentJson == null) recentJson = prefs.getString("idoban_recent_v1", null);

            if (boardJson == null || recentJson == null) {
                // Sin datos aún: estado vacío real (nunca se fabrican datos demo).
                views.setTextViewText(R.id.widget_badge, "00");
                views.setViewVisibility(R.id.widget_item_0, android.view.View.GONE);
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE);
                setupClick(context, views, R.id.widget_empty, null);
                return;
            }

            JSONObject board = new JSONObject(boardJson);
            JSONArray recent = new JSONArray(recentJson);
            JSONArray projects = board.optJSONArray("projects");
            JSONArray tasks = board.optJSONArray("tasks");
            if (projects == null || tasks == null || recent.length() == 0) {
                views.setTextViewText(R.id.widget_badge, "00");
                views.setViewVisibility(R.id.widget_item_0, android.view.View.GONE);
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE);
                return;
            }

            int count = Math.min(recent.length(), 1); // por ahora solo primer item en layout estático (extensible a 4)
            views.setTextViewText(R.id.widget_badge, String.format("%02d", recent.length()));
            if (count > 0) {
                String projId = recent.getString(0);
                JSONObject proj = findById(projects, projId);
                if (proj != null) {
                    String name = proj.optString("name", "—");
                    views.setTextViewText(R.id.widget_title_0, name);
                    // contar tareas
                    int todo=0, doing=0, done=0;
                    for (int i=0;i<tasks.length();i++) {
                        JSONObject t = tasks.getJSONObject(i);
                        if (projId.equals(t.optString("projectId"))) {
                            String s = t.optString("status");
                            if ("todo".equals(s)) todo++;
                            else if ("doing".equals(s)) doing++;
                            else if ("done".equals(s)) done++;
                        }
                    }
                    int total = todo+doing+done;
                    views.setTextViewText(R.id.widget_count_0, total + " T");
                    if (total>0) {
                        int pctTodo = Math.round(todo*100f/total);
                        int pctDoing = Math.round(doing*100f/total);
                        int pctDone = 100 - pctTodo - pctDoing;
                        views.setTextViewText(R.id.widget_pct_todo_0, pctTodo+"%");
                        views.setTextViewText(R.id.widget_pct_doing_0, pctDoing+"%");
                        views.setTextViewText(R.id.widget_pct_done_0, pctDone+"%");
                        // Nota: pesos de barra son fijos en XML (33/33/34) para RemoteViews simple.
                        // Para dinámico real se usaría setViewLayoutWeight si API lo permite, aquí se mantiene demo visual.
                    }
                    views.setViewVisibility(R.id.widget_item_0, android.view.View.VISIBLE);
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE);
                    setupClick(context, views, R.id.widget_item_0, projId);
                }
            }
        } catch (Exception e) {
            // fallback silencioso
            views.setViewVisibility(R.id.widget_item_0, android.view.View.VISIBLE);
        }
        // click en widget vacío abre app
        setupClick(context, views, R.id.widget_empty, null);
    }

    private void setupClick(Context context, RemoteViews views, int viewId, String projectId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (projectId != null) intent.putExtra("projectId", projectId);
        // también intent con hash para web
        intent.setAction(projectId != null ? "OPEN_PROJECT_" + projectId : "OPEN_APP");
        PendingIntent pi = PendingIntent.getActivity(context, viewId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    private JSONObject findById(JSONArray arr, String id) {
        for (int i=0;i<arr.length();i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o!=null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
}
