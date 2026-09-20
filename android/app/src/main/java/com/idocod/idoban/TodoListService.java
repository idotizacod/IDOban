package com.idocod.idoban;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * IDOban · Servicio de lista del widget 2 (bucket/todo) con scroll.
 * Colección vía RemoteViewsService: las tareas se ordenan como en la web
 * (fijadas ★ primero, luego por createdAt más reciente) y cada fila tiene
 * sus propios controles (★ fijar, ◄ fase anterior, ✕ borrar, ► siguiente).
 */
public class TodoListService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoListFactory(getApplicationContext(), intent);
    }

    private static class TodoListFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private final List<JSONObject> items = new ArrayList<>();

        TodoListFactory(Context context, Intent intent) {
            this.context = context;
            // appWidgetId disponible vía EXTRA_APPWIDGET_ID si hiciera falta.
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDestroy() {}

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).optString("id", "").hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            String tab = TodoStore.tab(context);
            try {
                JSONArray bucket = TodoStore.bucket(TodoStore.loadBoard(context));
                for (int i = 0; i < bucket.length(); i++) {
                    JSONObject task = bucket.optJSONObject(i);
                    if (task != null && tab.equals(task.optString("status"))) {
                        items.add(task);
                    }
                }
            } catch (Exception ignored) {}
            Collections.sort(items, COMPARATOR);
        }

        /** Fijadas primero, después por createdAt más reciente (igual que la web). */
        private static final Comparator<JSONObject> COMPARATOR = (a, b) -> {
            int pa = a.optInt("priority", 0) == 1 ? 0 : 1;
            int pb = b.optInt("priority", 0) == 1 ? 0 : 1;
            int c = Integer.compare(pa, pb);
            if (c != 0) return c;
            return b.optString("createdAt", "").compareTo(a.optString("createdAt", ""));
        };

        @Override
        public RemoteViews getViewAt(int position) {
            JSONObject task = items.get(position);
            String taskId = task.optString("id", "");
            boolean pinned = task.optInt("priority", 0) == 1;

            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_idoban_todo_item);
            row.setTextViewText(R.id.todo_pin, pinned ? "★" : "☆");
            row.setTextViewText(R.id.todo_title, task.optString("title", ""));
            row.setTextViewText(R.id.todo_proj, projectName(task.optString("projectId", "")));
            row.setTextViewText(R.id.todo_next, "doing".equals(TodoStore.tab(context)) ? "✓" : "►");

            fillIn(row, R.id.todo_pin, TodoListWidgetProvider.OP_PIN, taskId);
            fillIn(row, R.id.todo_back, TodoListWidgetProvider.OP_BACK, taskId);
            fillIn(row, R.id.todo_edit, TodoListWidgetProvider.OP_DELETE, taskId);
            fillIn(row, R.id.todo_next, TodoListWidgetProvider.OP_NEXT, taskId);
            fillIn(row, R.id.todo_title, TodoListWidgetProvider.OP_EDIT, taskId);
            fillIn(row, R.id.todo_proj, TodoListWidgetProvider.OP_EDIT, taskId);
            return row;
        }

        /** Rellena el click de un control de la fila con la operación y el id de la tarea.
         *  op/taskId viajan en la URI (no en extras): en Android 12+ los launchers
         *  (incl. Motorola) pierden los extras de los fill-in intents; la URI se conserva. */
        private void fillIn(RemoteViews row, int viewId, String op, String taskId) {
            Uri uri = Uri.parse("idoban-todo://" + op + "/" + taskId);
            Intent fill = new Intent();
            fill.setData(uri);
            fill.putExtra(TodoListWidgetProvider.EXTRA_OP, op);
            if (taskId != null && !taskId.isEmpty()) {
                fill.putExtra(TodoListWidgetProvider.EXTRA_TASK, taskId);
            }
            row.setOnClickFillInIntent(viewId, fill);
        }

        /** Etiqueta <PROYECTO> de la tarea, o vacío si es libre. */
        private String projectName(String projectId) {
            if (projectId == null || projectId.isEmpty()) return "";
            try {
                JSONObject board = TodoStore.loadBoard(context);
                JSONArray projects = board.optJSONArray("projects");
                if (projects == null) return "";
                for (int i = 0; i < projects.length(); i++) {
                    JSONObject project = projects.optJSONObject(i);
                    if (project != null && projectId.equals(project.optString("id"))) {
                        return "<" + project.optString("name", "") + ">";
                    }
                }
            } catch (Exception ignored) {}
            return "";
        }
    }
}