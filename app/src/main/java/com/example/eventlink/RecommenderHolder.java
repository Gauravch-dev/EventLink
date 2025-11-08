package com.example.eventlink;

import android.content.Context;

/**
 * Thread-safe singleton holder for EventRecommender.
 * Prevents reloading the weights JSON each time a screen opens.
 */
public final class RecommenderHolder {

    private static EventRecommender instance = null;

    private RecommenderHolder() {
        // Private constructor to prevent instantiation
    }

    public static synchronized EventRecommender get(Context ctx) throws Exception {
        if (instance == null) {
            instance = new EventRecommender(ctx.getApplicationContext());
        }
        return instance;
    }

    /** Optional reset method if you ever need to reload weights manually */
    public static synchronized void reset() {
        instance = null;
    }
}
