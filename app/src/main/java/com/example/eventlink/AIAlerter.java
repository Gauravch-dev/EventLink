package com.example.eventlink;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/** Simple AI alert generator reacting to chatbot intent classification. */
public class AIAlerter {

    public static void trigger(Context ctx, String intentLabel) {
        String message = makeMessage(intentLabel);
        NotificationUtils.notify(ctx, new Random().nextInt(9999),
                "AI Assistant Insight", message);
    }

    private static String makeMessage(String intent) {
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Calendar.getInstance().getTime());
        String base;
        intent = intent.toLowerCase(Locale.ROOT);

        switch (intent) {
            case "event_location":
                base = "AI noticed you're checking event locations.";
                break;
            case "event_description":
                base = "AI thinks you're exploring event details.";
                break;
            case "event_category":
                base = "AI sees you’re browsing through event categories.";
                break;
            case "user_name":
            case "user_email":
            case "user_location":
                base = "AI detected you're managing user profile data.";
                break;
            case "general_greeting":
                base = "AI appreciates your greeting — nice to see you active!";
                break;
            case "out_of_scope":
                base = "AI sensed a random query — let’s stay focused on events.";
                break;
            default:
                base = "AI Insight (" + time + "): You're engaging with event information.";
                break;
        }

        String[] extras = {
                "That’s a smart query — stay organized!",
                "AI is learning your preferences for better tips.",
                "Keep exploring — consistency builds clarity.",
                "You’re getting the most out of EventLink today!",
                "AI tip: check event summaries for quick context."
        };
        String add = extras[new Random().nextInt(extras.length)];
        return base + "\n" + add;
    }
}
