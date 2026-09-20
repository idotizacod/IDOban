package com.idocod.idoban;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * IDOban · Widget 2 (bucket list con pestañas POR COMENZAR / EN PROCESO).
 * Colección con scroll vía TodoListService (RemoteViewsFactory). Los controles
 * de cada fila se entregan con fill-in intents sobre una plantilla común.
 * Operaciones: ★ fijar/desfijar · ◄ fase anterior · ✕ borrar · ► siguiente fase · títulos editar.
 */
public class TodoListWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TODO_TAB = "com.idocod.idoban.TODO_TAB";
    public static final String EXTRA_TAB = "tab";
    public static final String ACTION_TODO_ADD = "com.idocod.idoban.TODO_ADD";
    public static final String ACTION_OPEN_BUCKET = "com.idocod.idoban.OPEN_BUCKET";

    /** Acción única que reciben los clicks de las filas (plantilla + fill-in). */
    public static final String ACTION_ITEM = "com.idocod.idoban.TODO_ITEM";
    public static final String EXTRA_OP = "op";
    public static final String EXTRA_TASK = "taskId";

    public static final String OP_PIN = "pin";
    public static final String OP_NEXT = "next";
    public static final String OP_BACK = "back";
    public static final String OP_DELETE = "delete";
    public static final String OP_EDIT = "edit";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context, id));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TODO_ADD.equals(action)) {
            openEditor(context, null);
        } else if (ACTION_TODO_TAB.equals(action)) {
            TodoStore.saveTab(context, intent.getStringExtra(EXTRA_TAB));
            refreshAll(context);
        } else if (ACTION_ITEM.equals(action)) {
            String op = resolveOp(intent);
            String taskId = resolveTaskId(intent);
            if (OP_PIN.equals(op)) {
                togglePin(context, taskId);
            } else if (OP_NEXT.equals(op)) {
                advance(context, taskId);
            } else if (OP_BACK.equals(op)) {
                back(context, taskId);
            } else if (OP_DELETE.equals(op)) {
                openDelete(context, taskId);
            } else if (OP_EDIT.equals(op)) {
                openEditor(context, taskId);
            }
        }
    }

    /** op desde la URI (idoban-todo://<op>/<taskId>) o extra como fallback. */
    private static String resolveOp(Intent intent) {
        if (intent.getData() != null) {
            String host = intent.getData().getHost();
            if (host != null) return host;
        }
        return intent.getStringExtra(EXTRA_OP);
    }

    /** taskId desde la URI o extra como fallback. */
    private static String resolveTaskId(Intent intent) {
        if (intent.getData() != null) {
            java.util.List<String> segs = intent.getData().getPathSegments();
            if (!segs.isEmpty()) return segs.get(segs.size() - 1);
        }
        return intent.getStringExtra(EXTRA_TASK);
    }

    /** Abre TodoInputActivity en modo edición (o alta si taskId es null). */
    private void openEditor(Context context, String taskId) {
        Intent intent = new Intent(context, TodoInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (taskId != null) intent.putExtra(EXTRA_TASK, taskId);
        context.startActivity(intent);
    }

    /** Abre la confirmación de borrado. */
    private void openDelete(Context context, String taskId) {
        Intent intent = new Intent(context, TodoConfirmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (taskId != null) intent.putExtra(EXTRA_TASK, taskId);
        context.startActivity(intent);
    }

    /** Fija/desfija (priority 1/0) la tarea y refresca. */
    private void togglePin(Context context, String taskId) {
        try {
            JSONObject board = TodoStore.loadBoard(context);
            JSONArray bucket = TodoStore.bucket(board);
            for (int i = 0; i < bucket.length(); i++) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && taskId != null && taskId.equals(task.optString("id"))) {
                    int priority = task.optInt("priority", 0) == 1 ? 0 : 1;
                    task.put("priority", priority);
                    TodoStore.saveBoard(context, board);
                    break;
                }
            }
        } catch (Exception ignored) {}
        refreshAll(context);
    }

    /** Avanza la tarea a la siguiente fase y refresca los widgets. */
    private void advance(Context context, String taskId) {
        try {
            JSONObject board = TodoStore.loadBoard(context);
            JSONArray bucket = TodoStore.bucket(board);
            for (int i = 0; i < bucket.length(); i++) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && taskId != null && taskId.equals(task.optString("id"))) {
                    String status = task.optString("status", "todo");
                    task.put("status", "todo".equals(status) ? "doing" : "done");
                    TodoStore.saveBoard(context, board);
                    break;
                }
            }
        } catch (Exception ignored) {}
        refreshAll(context);
    }

    /** Retrocede la tarea a la fase anterior y refresca los widgets. */
    private void back(Context context, String taskId) {
        try {
            JSONObject board = TodoStore.loadBoard(context);
            JSONArray bucket = TodoStore.bucket(board);
            for (int i = 0; i < bucket.length(); i++) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && taskId != null && taskId.equals(task.optString("id"))) {
                    String status = task.optString("status", "todo");
                    task.put("status", "done".equals(status) ? "doing" : "todo");
                    TodoStore.saveBoard(context, board);
                    break;
                }
            }
        } catch (Exception ignored) {}
        refreshAll(context);
    }

    private RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_idoban_todo);
        String tab = TodoStore.tab(context);
        boolean doing = "doing".equals(tab);
        int countTodo = countStatus(context, "todo");
        int countDoing = countStatus(context, "doing");

        views.setTextViewText(R.id.todo_tab_todo, doing
                ? "POR COMENZAR (" + String.format("%02d", countTodo) + ")"
                : "» POR COMENZAR (" + String.format("%02d", countTodo) + ")");
        views.setTextViewText(R.id.todo_tab_doing, doing
                ? "» EN PROCESO (" + String.format("%02d", countDoing) + ")"
                : "EN PROCESO (" + String.format("%02d", countDoing) + ")");
        setupTabClick(context, views, R.id.todo_tab_todo, "todo");
        setupTabClick(context, views, R.id.todo_tab_doing, "doing");

        views.setTextViewText(R.id.todo_badge, String.format("%02d", doing ? countDoing : countTodo));

        Intent service = new Intent(context, TodoListService.class);
        service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setRemoteAdapter(R.id.todo_list, service);
        views.setEmptyView(R.id.todo_list, R.id.todo_empty);

        setupItemTemplate(context, views);
        setupAddClick(context, views, R.id.todo_add);
        setupAddClick(context, views, R.id.todo_widget_root);
        return views;
    }

    /** Plantilla de click común para las filas de la lista (los extras llegan por fill-in).
     *  OBLIGATORIO FLAG_MUTABLE: en Android 12+ el fill-in intent de cada fila solo
     *  se fusiona en una plantilla mutable (inmutable bloquea extras/URI). */
    private void setupItemTemplate(Context context, RemoteViews views) {
        Intent template = new Intent(context, TodoListWidgetProvider.class);
        template.setAction(ACTION_ITEM);
        PendingIntent pi = PendingIntent.getBroadcast(context, R.id.todo_list, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.todo_list, pi);
    }

    private int countStatus(Context context, String status) {
        try {
            JSONArray bucket = TodoStore.bucket(TodoStore.loadBoard(context));
            int count = 0;
            for (int i = 0; i < bucket.length(); i++) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && status.equals(task.optString("status"))) count++;
            }
            return count;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Click en pestaña: broadcast para cambiar la pestaña persistida. */
    private void setupTabClick(Context context, RemoteViews views, int viewId, String tab) {
        Intent intent = new Intent(context, TodoListWidgetProvider.class);
        intent.setAction(ACTION_TODO_TAB).putExtra(EXTRA_TAB, tab);
        PendingIntent pi = PendingIntent.getBroadcast(context, viewId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    /** Botón "+": abre la Activity de entrada de tareas. */
    private void setupAddClick(Context context, RemoteViews views, int viewId) {
        PendingIntent pi = PendingIntent.getActivity(context, viewId,
                new Intent(context, TodoInputActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    /** Click: abre MainActivity en el bucket. */
    private void setupBucketClick(Context context, RemoteViews views, int viewId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_OPEN_BUCKET);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, viewId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    /** Refresca el widget 2 (datos + vistas) y propaga al widget 1. */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, TodoListWidgetProvider.class));
        if (ids.length > 0) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.todo_list);
        }
        for (int id : ids) {
            RemoteViews views = new TodoListWidgetProvider().buildViews(context, id);
            manager.updateAppWidget(id, views);
        }
        IdobanWidgetProvider.refreshAll(context);
    }
}