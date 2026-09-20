package com.idocod.idoban;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * IDOban · Alta/edición de tarea (minimal, 3 boxes).
 * CATEGORÍA + PROYECTO son AutoComplete: al escribir filtra las existentes y,
 * si un nombre no existe, se crea al guardar. TÍTULO es el campo de texto libre.
 * Respeta el modelo web: categories/projects/tasks en CapacitorStorage.
 */
public class TodoInputActivity extends Activity {

    /** El provider inyecta aquí el id de la tarea a editar (o ausente = nueva). */
    public static final String EXTRA_TASK = "taskId";

    private AutoCompleteTextView categoryInput;
    private AutoCompleteTextView projectInput;
    private EditText titleInput;
    private boolean editMode;
    private String editingTaskId;

    private List<JSONObject> categories = new ArrayList<>();
    private List<JSONObject> projects = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editMode = getIntent().hasExtra(EXTRA_TASK);
        editingTaskId = getIntent().getStringExtra(EXTRA_TASK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.widget_canvas));
        setContentView(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(32), dp(16), dp(32), dp(16));
        root.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // TÍTULO (primero)
        card.addView(fieldLabel("TÍTULO"));
        titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("TÍTULO DE LA TAREA");
        titleInput.setHintTextColor(Color.parseColor("#9aa5b1"));
        titleInput.setTextColor(color(R.color.widget_ink));
        titleInput.setTextSize(18);
        titleInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        titleInput.setBackground(boxBg(true));
        titleInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        titleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                categoryInput.requestFocus();
                return true;
            }
            return false;
        });
        card.addView(titleInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            setMargins(0, dp(6), 0, 0);
        }});

        // CATEGORÍA (crear/elegir)
        card.addView(fieldLabel("CATEGORÍA"));
        categoryInput = makeComplete(card, "ELIGE O CREA CATEGORÍA");

        // PROYECTO (depende de la categoría elegida)
        card.addView(fieldLabel("PROYECTO"));
        projectInput = makeComplete(card, "ELIGE O CREA PROYECTO");

        Button save = new Button(this);
        save.setText(editMode ? "GUARDAR" : "CREAR");
        save.setTextSize(14);
        save.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        save.setAllCaps(false);
        save.setTextColor(color(R.color.widget_canvas));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(color(R.color.todo_accent));
        saveBg.setCornerRadius(dp(6));
        save.setBackground(saveBg);
        save.setOnClickListener(v -> save());
        card.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)) {{
            setMargins(0, dp(6), 0, 0);
        }});

        loadData();
        if (editMode) {
            loadTask();
        }
        titleInput.requestFocus();
        titleInput.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(titleInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    /** Al ganar foco de ventana abre el dropdown del campo enfocado (si la vista ya está adjunta). */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        if (categoryInput != null && categoryInput.isFocused() && categoryInput.isAttachedToWindow()) {
            categoryInput.showDropDown();
        }
        if (projectInput != null && projectInput.isFocused() && projectInput.isAttachedToWindow()) {
            filterProjectsByCategory();
            projectInput.showDropDown();
        }
    }

    private TextView fieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setLetterSpacing(0.14f);
        tv.setTextColor(color(R.color.widget_ink));
        tv.setAlpha(0.55f);
        return tv;
    }

    private AutoCompleteTextView makeComplete(LinearLayout parent, String hint) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#9aa5b1"));
        input.setTextColor(color(R.color.widget_ink));
        input.setTextSize(15);
        input.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(boxBg(false));
        input.setThreshold(0);
        input.setOnFocusChangeListener((v, has) -> {
            input.setBackground(has ? boxFocused() : boxBg(false));
            if (has && input.isAttachedToWindow()) input.showDropDown();
            else input.dismissDropDown();
        });
        parent.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            setMargins(0, dp(6), 0, 0);
        }});
        return input;
    }

    private GradientDrawable boxBg(boolean focusable) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(R.color.widget_item));
        g.setStroke(dp(focusable ? 1 : 1), color(R.color.widget_grid));
        return g;
    }

    private GradientDrawable boxFocused() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(R.color.widget_item));
        g.setStroke(dp(2), color(R.color.todo_accent));
        return g;
    }

    /** Carga categorías y proyectos del board compartido. */
    private void loadData() {
        try {
            JSONObject board = TodoStore.loadBoard(this);
            JSONArray cat = board.optJSONArray("categories");
            if (cat != null) {
                for (int i = 0; i < cat.length(); i++) {
                    JSONObject c = cat.optJSONObject(i);
                    if (c != null) categories.add(c);
                }
            }
            JSONArray proj = board.optJSONArray("projects");
            if (proj != null) {
                for (int i = 0; i < proj.length(); i++) {
                    JSONObject p = proj.optJSONObject(i);
                    if (p != null) projects.add(p);
                }
            }
        } catch (Exception ignored) {}
        categoryInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names(categories)));
        projectInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>()));

        categoryInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterProjectsByCategory();
                clearProjectIfNotInCategory();
            }
        });
        categoryInput.setOnItemClickListener((parent, view, position, id) -> {
            filterProjectsByCategory();
            projectInput.post(projectInput::showDropDown);
        });
    }

    /** Solo muestra proyectos de la categoría elegida (nunca otras categorías). */
    private void filterProjectsByCategory() {
        String catId = currentCategoryId();
        List<String> list = new ArrayList<>();
        if (catId != null) {
            for (JSONObject p : projects) {
                if (catId.equals(p.optString("categoryId", ""))) {
                    list.add(p.optString("name", ""));
                }
            }
        }
        projectInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, list));
        if (projectInput.isPopupShowing()) {
            projectInput.showDropDown();
        }
    }

    private List<String> names(List<JSONObject> list) {
        List<String> out = new ArrayList<>();
        for (JSONObject o : list) out.add(o.optString("name", ""));
        return out;
    }

    /** Si la categoría cambió y el proyecto existente ya no le pertenece, lo limpia. */
    private void clearProjectIfNotInCategory() {
        String catId = currentCategoryId();
        String projName = projectInput.getText().toString().trim();
        if (projName.isEmpty()) return;
        boolean known = false;
        boolean belongs = false;
        for (JSONObject p : projects) {
            if (projName.equalsIgnoreCase(p.optString("name", ""))) {
                known = true;
                belongs = catId != null && catId.equals(p.optString("categoryId", ""));
                break;
            }
        }
        if (known && !belongs) projectInput.setText("");
    }

    private String currentCategoryId() {
        String catName = categoryInput.getText().toString().trim();
        for (JSONObject c : categories) {
            if (catName.equalsIgnoreCase(c.optString("name", ""))) {
                return c.optString("id", "");
            }
        }
        return null;
    }

    /** Si el texto no coincide con una existente (sin importar mayúsculas), la crea. */
    private JSONObject ensure(List<JSONObject> list, String name) {
        if (name == null || name.isEmpty()) return null;
        for (JSONObject o : list) {
            if (name.equalsIgnoreCase(o.optString("name", ""))) return o;
        }
        JSONObject created = new JSONObject();
        try {
            created.put("id", TodoStore.uid());
            created.put("name", name.toUpperCase());
            created.put("createdAt", java.time.Instant.now().toString());
        } catch (Exception ignored) {}
        list.add(created);
        return created;
    }

    private String findId(List<JSONObject> list, String field, String value) {
        if (value == null) return "";
        for (JSONObject o : list) {
            if (value.equals(o.optString(field, ""))) return value;
        }
        return "";
    }

    /** Precarga una tarea existente (modo edición). */
    private void loadTask() {
        try {
            JSONObject board = TodoStore.loadBoard(this);
            JSONArray bucket = TodoStore.bucket(board);
            for (int i = 0; i < bucket.length(); i++) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && editingTaskId.equals(task.optString("id"))) {
                    titleInput.setText(task.optString("title", ""));
                    String pid = task.optString("projectId", "");
                    for (JSONObject p : projects) {
                        if (pid.equals(p.optString("id"))) {
                            projectInput.setText(p.optString("name", ""));
                            for (JSONObject c : categories) {
                                if (p.optString("categoryId", "").equals(c.optString("id"))) {
                                    categoryInput.setText(c.optString("name", ""));
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    /** Guarda: crea tarea (o categoría/proyecto si hace falta) y refresca widgets. */
    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) return;
        try {
            JSONObject board = TodoStore.loadBoard(this);
            JSONArray bucket = TodoStore.bucket(board);

            JSONObject cat = ensure(categories, categoryInput.getText().toString().trim());
            JSONObject proj = ensure(projects, projectInput.getText().toString().trim());
            if (proj != null && cat != null) {
                try { proj.put("categoryId", cat.optString("id", "")); } catch (Exception ignored) {}
            }

            JSONObject task = null;
            if (editMode) {
                for (int i = 0; i < bucket.length(); i++) {
                    JSONObject t = bucket.optJSONObject(i);
                    if (t != null && editingTaskId.equals(t.optString("id"))) {
                        task = t;
                        break;
                    }
                }
            }
            if (task == null) {
                task = new JSONObject();
                task.put("id", TodoStore.uid());
                task.put("status", "todo");
                task.put("createdAt", java.time.Instant.now().toString());
                task.put("createdFrom", "input");
                bucket.put(task);
            }

            task.put("title", title);
            if (proj != null) {
                task.put("projectId", proj.optString("id", ""));
            } else {
                task.remove("projectId");
            }

            board.put("categories", new JSONArray(categories.toString()));
            board.put("projects", new JSONArray(projects.toString()));
            TodoStore.saveBoard(this, board);
            refreshWidgets();
            Toast.makeText(this, editMode ? "✔ TAREA ACTUALIZADA" : "✔ TAREA CREADA",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
        finish();
    }

    /** Refresca ambos widgets (2 · bucket y 1 · proyectos). */
    private void refreshWidgets() {
        TodoListWidgetProvider.refreshAll(this);
        IdobanWidgetProvider.refreshAll(this);
    }

    @SuppressWarnings("deprecation")
    private int color(int res) {
        return getResources().getColor(res);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}