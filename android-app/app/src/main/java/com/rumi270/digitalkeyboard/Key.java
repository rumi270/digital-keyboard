package com.rumi270.digitalkeyboard;

public class Key {
    public float x, y, width, height;
    public String shape;     // "rect", "round", "circle"
    public String type;      // "button", "joystick", "slider" — for future use
    public String label;
    public String action;
    public String color;

    public Key(float x, float y, float width, float height, String label, String action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.action = action;
        this.shape = "rect";
        this.type = "button";
        this.color = "#5B7FBF";
    }
}