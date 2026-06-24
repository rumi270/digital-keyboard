package com.rumi270.digitalkeyboard;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity {

    private WebSocket webSocket;
    private OkHttpClient client;

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

        Button btnA = findViewById(R.id.btnA);
        Button btnB = findViewById(R.id.btnB);
        Button btnC = findViewById(R.id.btnC);

        btnA.setOnClickListener(v -> webSocket.send("A"));
        btnB.setOnClickListener(v -> webSocket.send("B"));
        btnC.setOnClickListener(v -> webSocket.send("C"));
    }
}