package com.example.eventlink;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

/** Stub DB provider: replace with Firebase queries later. */
public final class DbProvider {
    private DbProvider() {}

    public static Map<String, String> fetchContextForIntent(Context ctx, String userId, String intent) {
        Map<String, String> m = new HashMap<>();

        // Demo user
        m.put("user_name", "Demo User");
        m.put("user_email", "demo@example.com");
        m.put("user_location", "Bengaluru");

        // Demo event
        m.put("event_name", "AI Summit 2025");
        m.put("event_description", "A workshop on modern ML systems.");
        m.put("event_category", "Workshop");
        m.put("event_location", "Mumbai Convention Center");

        return m;
    }

    public static String buildPrompt(String intent, Map<String, String> ctx) {
        if (intent == null) intent = "out_of_scope";
        switch (intent) {
            case "user_name":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state the user's name in one short sentence.\n" +
                        "Context:\n- user_name: " + safe(ctx.get("user_name"));
            case "user_email":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state the registered email in one short sentence.\n" +
                        "Context:\n- user_email: " + safe(ctx.get("user_email"));
            case "user_location":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state the user's location in one short sentence.\n" +
                        "Context:\n- user_location: " + safe(ctx.get("user_location"));
            case "event_name":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state the event name.\n" +
                        "Context:\n- event_name: " + safe(ctx.get("event_name"));
            case "event_description":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: summarize the event in 1–2 friendly sentences.\n" +
                        "Context:\n- event_description: " + safe(ctx.get("event_description"));
            case "event_category":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state the event category/type in one sentence.\n" +
                        "Context:\n- event_category: " + safe(ctx.get("event_category"));
            case "event_location":
                return "You are EventLink Assistant. Answer ONLY from context.\n" +
                        "Task: state where the event will be held in one sentence.\n" +
                        "Context:\n- event_location: " + safe(ctx.get("event_location"));
            default:
                return "You are EventLink Assistant. Be brief and helpful. " +
                        "If the question is not about the EventLink user or event context, politely say you only answer event-related queries.";
        }
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
