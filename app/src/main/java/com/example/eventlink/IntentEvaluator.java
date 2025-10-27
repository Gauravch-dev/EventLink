package com.example.eventlink;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Loads optional per-class thresholds and delegates to IntentLocalClassifier. */
public class IntentEvaluator {

    private static final String TAG = "IntentEvaluator";

    private final IntentLocalClassifier clf;
    private final Map<String, Double> perClassThresholds = new HashMap<>();
    private final double defaultThreshold;

    public IntentEvaluator(Context ctx, String modelAsset, String thresholdsAsset, double defaultThreshold) throws Exception {
        this.clf = new IntentLocalClassifier(ctx, modelAsset);
        this.defaultThreshold = defaultThreshold;

        if (thresholdsAsset != null) {
            try {
                String json = readAsset(ctx.getAssets(), thresholdsAsset);
                JSONObject obj = new JSONObject(json);
                for (String label : clf.classes()) {
                    if (obj.has(label)) {
                        perClassThresholds.put(label, obj.getDouble(label));
                    }
                }
                Log.i(TAG, "Loaded thresholds for " + perClassThresholds.size() + " classes.");
            } catch (Exception e) {
                Log.w(TAG, "Thresholds not loaded (" + e.getMessage() + ")");
            }
        }
    }

    public IntentLocalClassifier.Prediction evaluate(String text) {
        return clf.predict(text, defaultThreshold, perClassThresholds);
    }

    private static String readAsset(AssetManager am, String name) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(am.open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
