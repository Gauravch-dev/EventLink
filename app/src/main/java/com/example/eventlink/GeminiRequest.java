package com.example.eventlink;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal body for v1beta generateContent with systemInstruction.
 */
public class GeminiRequest {
    @SerializedName("systemInstruction") public Content systemInstruction;
    @SerializedName("contents")          public List<Content> contents = new ArrayList<>();

    public static GeminiRequest of(String userText, String systemPrompt) {
        GeminiRequest req = new GeminiRequest();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            req.systemInstruction = Content.ofText(systemPrompt, "system");
        }
        req.contents.add(Content.ofText(userText, "user"));
        return req;
    }

    // --- nested DTOs ---

    public static class Content {
        @SerializedName("role")  public String role;
        @SerializedName("parts") public List<Part> parts = new ArrayList<>();

        public static Content ofText(String text, String role) {
            Content c = new Content();
            c.role = role;
            Part p = new Part();
            p.text = text;
            c.parts.add(p);
            return c;
        }
    }

    public static class Part {
        @SerializedName("text") public String text;
    }
}
