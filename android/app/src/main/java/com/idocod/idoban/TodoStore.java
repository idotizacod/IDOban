package com.idocod.idoban;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Almacén compartido del bucket list.
 *
 * El tablero vive en el mismo SharedPreferences que escribe la capa web vía
 * Capacitor Preferences ("CapacitorStorage", clave idoban_board_v1), así el
 * widget y la app comparten el mismo JSON de forma offline-first:
 *   app   -> escribe con saveBoard()
 *   widget -> lee/escribe con loadBoard()/saveBoard()
 * La pestaña activa del widget se guarda aparte en "idoban_todo_prefs".
 */
public final class TodoStore {

    public static final String PREFS_CAP = "CapacitorStorage";
    public static final String KEY_BOARD = "idoban_board_v1";

    public static final String PREFS_TODO = "idoban_todo_prefs";
    public static final String KEY_TAB = "todo_tab";

    private TodoStore() {}

    public static JSONObject loadBoard(Context c) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(PREFS_CAP, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_BOARD, null);
            if (raw != null) {
                JSONObject b = new JSONObject(raw);
                if (!b.has("bucket")) b.put("bucket", new JSONArray());
                return b;
            }
        } catch (Exception ignored) {}
        JSONObject b = new JSONObject();
        try {
            b.put("categories", new JSONArray());
            b.put("projects", new JSONArray());
            b.put("tasks", new JSONArray());
            b.put("bucket", new JSONArray());
        } catch (Exception ignored) {}
        return b;
    }

    public static void saveBoard(Context c, JSONObject b) {
        try {
            c.getSharedPreferences(PREFS_CAP, Context.MODE_PRIVATE)
             .edit().putString(KEY_BOARD, b.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONArray bucket(JSONObject b) {
        // La web (fuente de verdad) guarda las tareas en "tasks" con status.
        // "bucket" era un alias legacy que nadie escribe: se usa como fallback.
        JSONArray tasks = b.optJSONArray("tasks");
        if (tasks != null) return tasks;
        JSONArray a = b.optJSONArray("bucket");
        return a != null ? a : new JSONArray();
    }

    public static String tab(Context c) {
        return c.getSharedPreferences(PREFS_TODO, Context.MODE_PRIVATE)
                .getString(KEY_TAB, "todo");
    }

    public static void saveTab(Context c, String t) {
        c.getSharedPreferences(PREFS_TODO, Context.MODE_PRIVATE)
         .edit().putString(KEY_TAB, "doing".equals(t) ? "doing" : "todo").apply();
    }

    public static String uid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }
}