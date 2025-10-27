package com.example.eventlink;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GeminiResponse {
    @SerializedName("candidates") public List<Candidate> candidates;

    public String firstText() {
        if (candidates == null || candidates.isEmpty()) return null;
        Candidate c = candidates.get(0);
        if (c == null || c.content == null || c.content.parts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (GeminiRequest.Part p : c.content.parts) {
            if (p != null && p.text != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(p.text);
            }
        }
        return sb.toString();
    }

    public static class Candidate {
        @SerializedName("content") public GeminiRequest.Content content;
        @SerializedName("finishReason") public String finishReason;
    }
}
