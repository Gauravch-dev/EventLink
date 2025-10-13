package com.example.eventlink;

public class GeminiIn {
    public String message;
    public String system; // optional

    public GeminiIn(String message) { this.message = message; }
    public GeminiIn(String message, String system) {
        this.message = message;
        this.system = system;
    }
}
