package com.example.eventlink;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class IntentEvaluator {

    private static final String TAG = "IntentEvaluator";
    private final IntentLocalClassifier clf;
    private final Map<String, Double> perClassThresholds = new HashMap<>();
    private final double defaultThreshold;

    // Only keep rule-based detection for date/time questions
    private static final Pattern DATE_TIME = Pattern.compile(
            "\\b(when|what\\s*time|date|schedule|start\\s*time|begin|happen|time\\s*of)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public IntentEvaluator(Context ctx, String modelAsset, String thresholdsAsset, double defaultThreshold) throws Exception {
        this.clf = new IntentLocalClassifier(ctx, modelAsset);
        this.defaultThreshold = defaultThreshold;

        if (thresholdsAsset != null) {
            try {
                String json = readAsset(ctx.getAssets(), thresholdsAsset);
                JSONObject obj = new JSONObject(json);
                for (String label : clf.classes()) {
                    if (obj.has(label)) perClassThresholds.put(label, obj.getDouble(label));
                }
                Log.i(TAG, "Loaded thresholds for " + perClassThresholds.size() + " classes.");
            } catch (Exception e) {
                Log.w(TAG, "Thresholds not loaded (" + e.getMessage() + ")");
            }
        }
    }

    public IntentLocalClassifier.Prediction evaluate(String text) {
        if (TextUtils.isEmpty(text))
            return new IntentLocalClassifier.Prediction("out_of_scope", 1.0, "", 0.0, new double[0]);

        // --- Date/Time override only ---
        if (DATE_TIME.matcher(text).find())
            return new IntentLocalClassifier.Prediction("event_date_time", 0.98, "out_of_scope", 0.02, new double[0]);

        // --- Everything else handled by the trained model ---
        return clf.predict(text, defaultThreshold, perClassThresholds);
    }

    private static String readAsset(AssetManager am, String name) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(am.open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
