package com.example.eventlink;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * DbIntents
 * -------------------------
 * Declares all supported context-based intents
 * that are fulfilled by Firestore (through DbProvider).
 */
public class DbIntents {

    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "user_name",
            "user_email",
            "user_interests",
            "registered_events",
            "event_location",
            "event_date",
            "event_time",
            "event_details",
            "event_about",
            "event_category"
    ));

    public static boolean isSupportedIntent(String intent) {
        if (intent == null) return false;
        return SUPPORTED.contains(intent.toLowerCase());
    }

    public static Set<String> all() {
        return SUPPORTED;
    }
}
