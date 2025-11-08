package com.example.eventlink;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DbProvider
 * -----------------------------------------------------
 * Provides contextual data from Firestore for intents:
 *  - user_name, user_email, user_interests
 *  - registered_events
 *  - event_location, event_date, event_time, event_about, event_category
 */
public class DbProvider {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static void fetchContextForIntent(Context ctx, String userEmail, String intentName, @NonNull Consumer<Map<String, String>> callback) {
        Map<String, String> result = new HashMap<>();

        if (intentName == null) {
            callback.accept(result);
            return;
        }

        switch (intentName.toLowerCase()) {
            case "user_name":
            case "user_email":
            case "user_interests":
            case "registered_events":
                fetchUserContext(userEmail, intentName, callback);
                break;

            case "event_location":
            case "event_date":
            case "event_time":
            case "event_details":
            case "event_about":
            case "event_category":
                fetchEventContext(intentName, callback);
                break;

            default:
                callback.accept(result);
        }
    }

    private static void fetchUserContext(String email, String intent, Consumer<Map<String, String>> callback) {
        if (email == null || email.isEmpty()) {
            callback.accept(new HashMap<>());
            return;
        }

        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    Map<String, String> map = new HashMap<>();
                    if (!q.isEmpty()) {
                        DocumentSnapshot doc = q.getDocuments().get(0);

                        switch (intent) {
                            case "user_name":
                                String name = doc.getString("name");
                                map.put("user_name", name != null ? name : "(no name)");
                                break;

                            case "user_email":
                                map.put("user_email", email);
                                break;

                            case "user_interests":
                                List<String> interests = (List<String>) doc.get("interests");
                                if (interests != null && !interests.isEmpty()) {
                                    map.put("user_interests", String.join(", ", interests));
                                } else {
                                    map.put("user_interests", "(no interests found)");
                                }
                                break;

                            case "registered_events":
                                List<String> events = (List<String>) doc.get("registeredEvents");
                                if (events != null && !events.isEmpty()) {
                                    map.put("registered_events", String.join(", ", events));
                                } else {
                                    map.put("registered_events", "You have no registered events.");
                                }
                                break;
                        }
                    }
                    callback.accept(map);
                })
                .addOnFailureListener(e -> {
                    Log.e("DbProvider", "Error fetching user: ", e);
                    callback.accept(new HashMap<>());
                });
    }

    private static void fetchEventContext(String intent, Consumer<Map<String, String>> callback) {
        // Example: fetch global "createdEvents" collection
        db.collection("createdEvents")
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    Map<String, String> map = new HashMap<>();
                    if (!q.isEmpty()) {
                        DocumentSnapshot doc = q.getDocuments().get(0);
                        switch (intent) {
                            case "event_location":
                                map.put("event_location", doc.getString("location"));
                                break;
                            case "event_date":
                                map.put("event_date", doc.getString("date"));
                                break;
                            case "event_time":
                                map.put("event_time", doc.getString("time"));
                                break;
                            case "event_details":
                                map.put("event_details", doc.getString("description"));
                                break;
                            case "event_about":
                                map.put("event_about", doc.getString("about"));
                                break;
                            case "event_category":
                                map.put("event_category", doc.getString("category"));
                                break;
                        }
                    }
                    callback.accept(map);
                })
                .addOnFailureListener(e -> {
                    Log.e("DbProvider", "Error fetching event: ", e);
                    callback.accept(new HashMap<>());
                });
    }

    public static String buildPrompt(String intent, Map<String, String> ctx) {
        StringBuilder sb = new StringBuilder("You are EventLink assistant. Answer clearly and accurately.\n\n");

        if (ctx != null && !ctx.isEmpty()) {
            sb.append("CONTEXT:\n");
            for (Map.Entry<String, String> e : ctx.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\n");
        }

        switch (intent.toLowerCase()) {
            case "user_name":
                sb.append("User asked for their name. Respond with their stored name.");
                break;
            case "user_email":
                sb.append("User asked for their email. Respond with the registered email.");
                break;
            case "user_interests":
                sb.append("User asked about their interests. Respond with the list of interests.");
                break;
            case "registered_events":
                sb.append("User asked what events they are registered for.");
                break;
            case "event_about":
                sb.append("User asked what an event is about. Summarize using 'about' or 'description' fields.");
                break;
            case "event_category":
                sb.append("User asked the category of the event. Respond with the event category.");
                break;
            default:
                sb.append("Provide the best possible contextual response.");
        }

        return sb.toString();
    }
}
