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

public class MainActivity extends AppCompatActivity {

    private WebSocket webSocket;
    private OkHttpClient client;
    private List<Key> keys = new ArrayList<>();
    private FrameLayout canvas;
    private boolean editMode = false;
    private Gson gson = new Gson();

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

        Button editToggle = findViewById(R.id.editToggle);
        editToggle.setOnClickListener(v -> {
            editMode = !editMode;
            editToggle.setText(editMode ? "Done" : "Edit");
            android.widget.Toast.makeText(this, editMode ? "Edit mode ON" : "Edit mode OFF", android.widget.Toast.LENGTH_SHORT).show();
        });

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

            btn.setOnClickListener(v -> {
                if (editMode) {
                    showEditDialog(key);
                } else {
                    webSocket.send(key.action);
                }
            });

            canvas.addView(btn);
        }
    }

    private void showEditDialog(Key key) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(key.action);
        input.setHint("What this key sends, e.g. A");

        new AlertDialog.Builder(this)
                .setTitle("Edit key")
                .setMessage("Set the label and action for this key")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newValue = input.getText().toString();
                    key.label = newValue;
                    key.action = newValue;
                    saveKeys();
                    renderKeys();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}