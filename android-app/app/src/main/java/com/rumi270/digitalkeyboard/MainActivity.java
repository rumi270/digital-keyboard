package com.rumi270.digitalkeyboard;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
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
        generateGridLayout(3, 4);
        canvas.post(this::renderKeys);
    }

    // Fills the keys list with a grid — but stores them as x/y/size, not grid cells
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

    // Draws every key in the list onto the screen at its stored position
    private void renderKeys() {
        canvas.removeAllViews();
        int canvasW = canvas.getWidth();
        int canvasH = canvas.getHeight();

        for (Key key : keys) {
            Button btn = new Button(this);
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

            final String action = key.action;
            btn.setOnClickListener(v -> webSocket.send(action));

            canvas.addView(btn);
        }
    }
}