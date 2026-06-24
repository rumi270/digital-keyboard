package com.rumi270.digitalkeyboard;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private WebSocket webSocket;
    private OkHttpClient client;
    private List<Key> keys = new ArrayList<>();
    private FrameLayout canvas;
    private boolean editMode = false;
    private Gson gson = new Gson();
    private Button addKeyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("ws://10.0.0.1:8080")
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, okhttp3.Response response) {
                runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Connected!", android.widget.Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onFailure(WebSocket ws, Throwable t, okhttp3.Response response) {
                runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Failed: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        });

        canvas = findViewById(R.id.canvas);

        canvas.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newW = right - left;
            int newH = bottom - top;
            int oldW = oldRight - oldLeft;
            int oldH = oldBottom - oldTop;
            if (newW != oldW || newH != oldH) {
                canvas.post(this::renderKeys);
            }
        });
        
        Button editToggle = findViewById(R.id.editToggle);
        addKeyBtn = findViewById(R.id.addKey);

        editToggle.setOnClickListener(v -> {
            editMode = !editMode;
            editToggle.setText(editMode ? "Done" : "Edit");
            addKeyBtn.setVisibility(editMode ? android.view.View.VISIBLE : android.view.View.GONE);
            android.widget.Toast.makeText(this, editMode ? "Edit mode ON" : "Edit mode OFF", android.widget.Toast.LENGTH_SHORT).show();
        });

        addKeyBtn.setOnClickListener(v -> addNewKey());
        loadKeys();
        canvas.post(this::renderKeys);
    }

    private void generateGridLayout(int cols, int rows) {
        keys.clear();
        float w = 1f / cols;
        float h = 1f / rows;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float x = col * w;
                float y = row * h;
                String label = "Key " + row + "," + col;
                keys.add(new Key(x, y, w, h, label, label));
            }
        }
    }

    private void saveKeys() {
        SharedPreferences prefs = getSharedPreferences("layouts", MODE_PRIVATE);
        String json = gson.toJson(keys);
        prefs.edit().putString("currentLayout", json).apply();
    }

    private void loadKeys() {
        SharedPreferences prefs = getSharedPreferences("layouts", MODE_PRIVATE);
        String json = prefs.getString("currentLayout", null);

        if (json == null) {
            generateGridLayout(3, 4);
            saveKeys();
        } else {
            Type listType = new TypeToken<ArrayList<Key>>(){}.getType();
            keys = gson.fromJson(json, listType);
        }
    }

    private void renderKeys() {
        canvas.removeAllViews();
        int canvasW = canvas.getWidth();
        int canvasH = canvas.getHeight();

        for (Key key : keys) {
            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setText(key.label);

            int left = Math.round(key.x * canvasW);
            int top = Math.round(key.y * canvasH);
            int right = Math.round((key.x + key.width) * canvasW);
            int bottom = Math.round((key.y + key.height) * canvasH);

            int gap = 8;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (right - left) - gap,
                    (bottom - top) - gap
            );
            params.leftMargin = left + gap / 2;
            params.topMargin = top + gap / 2;
            btn.setLayoutParams(params);

            final float[] downXY = new float[2];
            final int[] startMargins = new int[2];
            final boolean[] didDrag = {false};

            btn.setOnTouchListener((v, event) -> {
                if (!editMode) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        webSocket.send(key.action);
                    }
                    return true;
                }

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downXY[0] = event.getRawX();
                        downXY[1] = event.getRawY();
                        startMargins[0] = lp.leftMargin;
                        startMargins[1] = lp.topMargin;
                        didDrag[0] = false;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downXY[0];
                        float dy = event.getRawY() - downXY[1];
                        if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                            didDrag[0] = true;
                        }
                        lp.leftMargin = startMargins[0] + (int) dx;
                        lp.topMargin = startMargins[1] + (int) dy;
                        v.setLayoutParams(lp);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        if (didDrag[0]) {
                            key.x = (float) (lp.leftMargin - gap / 2) / canvas.getWidth();
                            key.y = (float) (lp.topMargin - gap / 2) / canvas.getHeight();
                            clampKeyToBounds(key);
                            saveKeys();
                            renderKeys();
                        } else {
                            showEditDialog(key);
                        }
                        return true;
                }
                return false;
            });

            canvas.addView(btn);
        }
    }
    private void addNewKey() {
        Key newKey = new Key(0f, 0f, 0.25f, 0.15f, "New", "A");
        keys.add(newKey);
        saveKeys();
        renderKeys();
    }

    private void showEditDialog(Key key) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = 48;
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText labelInput = new EditText(this);
        labelInput.setInputType(InputType.TYPE_CLASS_TEXT);
        labelInput.setText(key.label);
        labelInput.setHint("Label shown on the key, e.g. Copy");
        layout.addView(labelInput);

        EditText actionInput = new EditText(this);
        actionInput.setInputType(InputType.TYPE_CLASS_TEXT);
        actionInput.setText(key.action);
        actionInput.setHint("What it sends, e.g. A");
        layout.addView(actionInput);

        EditText widthInput = new EditText(this);
        widthInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        widthInput.setText(String.valueOf(key.width));
        widthInput.setHint("Width (0.1 to 1.0)");
        layout.addView(widthInput);

        EditText heightInput = new EditText(this);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        heightInput.setText(String.valueOf(key.height));
        heightInput.setHint("Height (0.1 to 1.0)");
        layout.addView(heightInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit key")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    key.label = labelInput.getText().toString();
                    key.action = actionInput.getText().toString();
                    key.width = parseFraction(widthInput.getText().toString(), key.width);
                    key.height = parseFraction(heightInput.getText().toString(), key.height);
                    clampKeyToBounds(key);
                    saveKeys();
                    canvas.post(this::renderKeys);
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (dialog, which) -> {
                    keys.remove(key);
                    saveKeys();
                    canvas.post(this::renderKeys);
                })
                .show();
    }

    private float parseFraction(String text, float fallback) {
        try {
            float value = Float.parseFloat(text);
            if (value < 0.05f) value = 0.05f;
            if (value > 1.0f) value = 1.0f;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    private void clampKeyToBounds(Key key) {
        if (key.width > 1f) key.width = 1f;
        if (key.height > 1f) key.height = 1f;
        if (key.width < 0.05f) key.width = 0.05f;
        if (key.height < 0.05f) key.height = 0.05f;

        if (key.x < 0f) key.x = 0f;
        if (key.y < 0f) key.y = 0f;

        if (key.x + key.width > 1f) key.x = 1f - key.width;
        if (key.y + key.height > 1f) key.y = 1f - key.height;
    }

}