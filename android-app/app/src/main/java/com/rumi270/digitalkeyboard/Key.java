package com.rumi270.digitalkeyboard;

public class Key {
    public float x;          // position from left (0.0 to 1.0, fraction of screen width)
    public float y;          // position from top (0.0 to 1.0, fraction of screen height)
    public float width;      // fraction of screen width
    public float height;     // fraction of screen height
    public String shape;     // "rect", "round", "circle"
    public String label;     // text shown on the key
    public String action;    // what it sends to the PC
    public String color;     // hex color, e.g. "#4A90D9"

    public Key(float x, float y, float width, float height, String label, String action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.action = action;
        this.shape = "rect";
        this.color = "#5B7FBF";
    }
}