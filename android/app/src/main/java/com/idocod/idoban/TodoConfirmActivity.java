package com.idocod.idoban;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * IDOban · Confirmación de borrado (Ultra-Studio: canvas, tinta, rojo = acción).
 * El widget inyecta el id de la tarea; SÍ la elimina y refresca los widgets.
 */
public class TodoConfirmActivity extends Activity {

    public static final String EXTRA_TASK = "taskId";

    private String taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskId = getIntent().getStringExtra(EXTRA_TASK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.widget_canvas));
        setContentView(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(color(R.color.widget_item));
        cardBg.setCornerRadius(dp(6));
        cardBg.setStroke(dp(1), color(R.color.widget_grid));
        card.setBackground(cardBg);
        root.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.CENTER;
        }});

        TextView bar = new TextView(this);
        bar.setBackgroundColor(color(R.color.todo_accent));
        card.addView(bar, new LinearLayout.LayoutParams(
                dp(34), dp(4)) {{
            setMargins(0, 0, 0, dp(14));
        }});

        TextView title = new TextView(this);
        title.setText("¿ELIMINAR TAREA?");
        title.setTextSize(16);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setLetterSpacing(0.06f);
        title.setTextColor(color(R.color.widget_ink));
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("LA ACCIÓN NO SE PUEDE DESHACER");
        body.setTextSize(10);
        body.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        body.setLetterSpacing(0.12f);
        body.setTextColor(color(R.color.widget_ink));
        body.setAlpha(0.5f);
        body.setGravity(Gravity.CENTER);
        card.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            setMargins(0, dp(6), 0, dp(18));
        }});

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button no = new Button(this);
        no.setText("NO");
        no.setTextSize(14);
        no.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        no.setAllCaps(false);
        no.setTextColor(color(R.color.widget_ink));
        GradientDrawable noBg = new GradientDrawable();
        noBg.setColor(color(R.color.widget_item));
        noBg.setCornerRadius(dp(6));
        noBg.setStroke(dp(1), color(R.color.widget_grid));
        no.setBackground(noBg);
        no.setOnClickListener(v -> finish());
        buttons.addView(no, new LinearLayout.LayoutParams(0, dp(50), 1f) {{
            setMargins(0, 0, dp(6), 0);
        }});

        Button yes = new Button(this);
        yes.setText("SÍ");
        yes.setTextSize(14);
        yes.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        yes.setAllCaps(false);
        yes.setTextColor(color(R.color.widget_canvas));
        GradientDrawable yesBg = new GradientDrawable();
        yesBg.setColor(color(R.color.todo_accent));
        yesBg.setCornerRadius(dp(6));
        yes.setBackground(yesBg);
        yes.setOnClickListener(v -> deleteAndFinish());
        buttons.addView(yes, new LinearLayout.LayoutParams(0, dp(50), 1f) {{
            setMargins(dp(6), 0, 0, 0);
        }});
    }

    /** Elimina la tarea del board, refresca los widgets y cierra. */
    private void deleteAndFinish() {
        try {
            JSONObject board = TodoStore.loadBoard(this);
            JSONArray bucket = TodoStore.bucket(board);
            for (int i = bucket.length() - 1; i >= 0; i--) {
                JSONObject task = bucket.optJSONObject(i);
                if (task != null && taskId != null && taskId.equals(task.optString("id"))) {
                    bucket.remove(i);
                    TodoStore.saveBoard(this, board);
                    break;
                }
            }
            TodoListWidgetProvider.refreshAll(this);
            Toast.makeText(this, "✔ TAREA ELIMINADA", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
        finish();
    }

    @SuppressWarnings("deprecation")
    private int color(int res) {
        return getResources().getColor(res);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}