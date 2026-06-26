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
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private WebSocket webSocket;
    private OkHttpClient client;
    private List<Key> keys = new ArrayList<>();
    private FrameLayout canvas;
    private boolean editMode = false;
    private Gson gson = new Gson();
    private Button addKeyBtn;
    private Button profilesBtn;
    private String currentProfile = "Default";
    private java.util.Map<String, List<Key>> profiles = new java.util.HashMap<>();
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private android.os.Handler reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new OkHttpClient();
        connectWebSocket();
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

        profilesBtn = findViewById(R.id.profilesBtn);

        editToggle.setOnClickListener(v -> {
            editMode = !editMode;
            editToggle.setText(editMode ? "Done" : "Edit");
            addKeyBtn.setVisibility(editMode ? android.view.View.VISIBLE : android.view.View.GONE);
            profilesBtn.setVisibility(editMode ? android.view.View.VISIBLE : android.view.View.GONE);
            android.widget.Toast.makeText(this, editMode ? "Edit mode ON" : "Edit mode OFF", android.widget.Toast.LENGTH_SHORT).show();
            canvas.post(this::renderKeys);
        });

        profilesBtn.setOnClickListener(v -> showProfilesDialog());

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
        // Put the current keys into the current profile, then save everything.
        profiles.put(currentProfile, keys);
        SharedPreferences prefs = getSharedPreferences("layouts", MODE_PRIVATE);
        String profilesJson = gson.toJson(profiles);
        prefs.edit()
                .putString("allProfiles", profilesJson)
                .putString("currentProfile", currentProfile)
                .apply();
    }

    private void loadKeys() {
        SharedPreferences prefs = getSharedPreferences("layouts", MODE_PRIVATE);
        String profilesJson = prefs.getString("allProfiles", null);
        currentProfile = prefs.getString("currentProfile", "Default");

        if (profilesJson == null) {
            // First ever launch — make a Default profile with a starter grid.
            generateGridLayout(3, 4);
            profiles = new java.util.HashMap<>();
            profiles.put("Default", keys);
            saveKeys();
        } else {
            Type profilesType = new TypeToken<java.util.HashMap<String, ArrayList<Key>>>(){}.getType();
            profiles = gson.fromJson(profilesJson, profilesType);
            keys = profiles.get(currentProfile);
            if (keys == null) {
                // Safety: current profile missing — fall back to any profile or a new grid.
                if (!profiles.isEmpty()) {
                    currentProfile = profiles.keySet().iterator().next();
                    keys = profiles.get(currentProfile);
                } else {
                    generateGridLayout(3, 4);
                    profiles.put(currentProfile, keys);
                }
            }
        }
    }

    private void renderKeys() {
        canvas.removeAllViews();
        int canvasW = canvas.getWidth();
        int canvasH = canvas.getHeight();
        int gap = 8;

        for (Key key : keys) {
            int left = Math.round(key.x * canvasW);
            int top = Math.round(key.y * canvasH);
            int right = Math.round((key.x + key.width) * canvasW);
            int bottom = Math.round((key.y + key.height) * canvasH);

            int keyW = (right - left) - gap;
            int keyH = (bottom - top) - gap;

            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setText(key.label);
            try {
                btn.setBackgroundColor(android.graphics.Color.parseColor(key.color));
            } catch (Exception e) {
                btn.setBackgroundColor(android.graphics.Color.parseColor("#5B7FBF"));
            }
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(keyW, keyH);
            btnParams.leftMargin = left + gap / 2;
            btnParams.topMargin = top + gap / 2;
            btn.setLayoutParams(btnParams);

            final float[] downXY = new float[2];
            final int[] startMargins = new int[2];
            final int[] startSize = new int[2];
            final boolean[] didDrag = {false};
            final boolean[] isResizing = {false};

            btn.setOnTouchListener((v, event) -> {
                if (!editMode) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        webSocket.send(key.action);
                    }
                    return true;
                }

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downXY[0] = event.getRawX();
                        downXY[1] = event.getRawY();
                        startMargins[0] = btnParams.leftMargin;
                        startMargins[1] = btnParams.topMargin;
                        startSize[0] = btnParams.width;
                        startSize[1] = btnParams.height;
                        didDrag[0] = false;

                        // Fixed, finger-friendly resize corner. Resize is allowed
                        // as long as the touch lands in the bottom-right corner zone.
                        int zoneW = Math.min(80, v.getWidth() / 2);
                        int zoneH = Math.min(80, v.getHeight() / 2);
                        isResizing[0] = (event.getX() > v.getWidth() - zoneW)
                                && (event.getY() > v.getHeight() - zoneH);
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downXY[0];
                        float dy = event.getRawY() - downXY[1];
                        if (Math.abs(dx) > 15 || Math.abs(dy) > 15) didDrag[0] = true;

                        if (isResizing[0]) {
                            // Work out the new size in pixels, then clamp in the SAME
                            // fraction units we store, live, so there's no snap on release.
                            int rawW = startSize[0] + (int) dx;
                            int rawH = startSize[1] + (int) dy;

                            float minPxW = 0.05f * canvas.getWidth();
                            float minPxH = 0.05f * canvas.getHeight();
                            float maxPxW = canvas.getWidth() - btnParams.leftMargin - gap;
                            float maxPxH = canvas.getHeight() - btnParams.topMargin - gap;

                            btnParams.width = (int) Math.max(minPxW, Math.min(rawW, maxPxW));
                            btnParams.height = (int) Math.max(minPxH, Math.min(rawH, maxPxH));
                        } else {
                            btnParams.leftMargin = startMargins[0] + (int) dx;
                            btnParams.topMargin = startMargins[1] + (int) dy;
                        }
                        btn.setLayoutParams(btnParams);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        if (didDrag[0]) {
                            if (isResizing[0]) {
                                key.width = (float) (btnParams.width + gap) / canvas.getWidth();
                                key.height = (float) (btnParams.height + gap) / canvas.getHeight();
                            } else {
                                key.x = (float) (btnParams.leftMargin - gap / 2) / canvas.getWidth();
                                key.y = (float) (btnParams.topMargin - gap / 2) / canvas.getHeight();
                            }
                            clampKeyToBounds(key);
                            saveKeys();
                            canvas.post(this::renderKeys);
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

        // Color picker row
        final String[] chosenColor = { key.color };
        String[] palette = {"#5B7FBF", "#D9534F", "#5CB85C", "#F0AD4E", "#9B59B6", "#34495E", "#E91E63", "#16A085"};

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, 24, 0, 0);

        for (String colorHex : palette) {
            View swatch = new View(this);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(70, 70);
            swatchParams.setMargins(8, 0, 8, 0);
            swatch.setLayoutParams(swatchParams);
            swatch.setBackgroundColor(android.graphics.Color.parseColor(colorHex));
            swatch.setOnClickListener(sv -> {
                chosenColor[0] = colorHex;
                for (int i = 0; i < colorRow.getChildCount(); i++) {
                    colorRow.getChildAt(i).setAlpha(0.4f);
                }
                sv.setAlpha(1f);
            });
            swatch.setAlpha(colorHex.equals(key.color) ? 1f : 0.4f);
            colorRow.addView(swatch);
        }
        layout.addView(colorRow);

        new AlertDialog.Builder(this)
                .setTitle("Edit key")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    key.label = labelInput.getText().toString();
                    key.action = actionInput.getText().toString();
                    key.width = parseFraction(widthInput.getText().toString(), key.width);
                    key.height = parseFraction(heightInput.getText().toString(), key.height);
                    key.color = chosenColor[0];
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

    private void showProfilesDialog() {
        java.util.List<String> names = new ArrayList<>(profiles.keySet());
        String[] nameArray = names.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Profiles (current: " + currentProfile + ")")
                .setItems(nameArray, (dialog, which) -> {
                    switchProfile(nameArray[which]);
                })
                .setPositiveButton("New profile", (dialog, which) -> showNewProfileDialog())
                .setNeutralButton("Delete current", (dialog, which) -> deleteCurrentProfile())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void switchProfile(String name) {
        saveKeys(); // save the current profile before leaving it
        currentProfile = name;
        keys = profiles.get(name);
        saveKeys();
        canvas.post(this::renderKeys);
        android.widget.Toast.makeText(this, "Switched to " + name, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showNewProfileDialog() {
        EditText input = new EditText(this);
        input.setHint("Profile name");

        new AlertDialog.Builder(this)
                .setTitle("New profile")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(this, "Name can't be empty", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (profiles.containsKey(name)) {
                        android.widget.Toast.makeText(this, "That name already exists", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveKeys();
                    currentProfile = name;
                    keys = new ArrayList<>();
                    generateGridLayout(3, 4);
                    profiles.put(name, keys);
                    saveKeys();
                    canvas.post(this::renderKeys);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrentProfile() {
        if (profiles.size() <= 1) {
            android.widget.Toast.makeText(this, "Can't delete your only profile", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        profiles.remove(currentProfile);
        currentProfile = profiles.keySet().iterator().next();
        keys = profiles.get(currentProfile);
        saveKeys();
        canvas.post(this::renderKeys);
        android.widget.Toast.makeText(this, "Now on " + currentProfile, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void connectWebSocket() {
        if (isConnecting || isConnected) return;
        isConnecting = true;

        Request request = new Request.Builder()
                .url("ws://10.0.0.1:8080")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, okhttp3.Response response) {
                isConnected = true;
                isConnecting = false;
                runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Connected!", android.widget.Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                isConnecting = false;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, okhttp3.Response response) {
                isConnected = false;
                isConnecting = false;
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectHandler.postDelayed(() -> {
            if (!isConnected) {
                connectWebSocket();
            }
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isConnected) {
            connectWebSocket();
        }
    }

}