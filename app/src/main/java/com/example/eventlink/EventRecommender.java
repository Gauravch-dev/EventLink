package com.example.eventlink;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight TF-IDF + cosine similarity recommender for events.
 * Loads pre-trained weights_events.json from assets/.
 */
public class EventRecommender {

    private final Map<String, Integer> vocab = new HashMap<>();
    private final double[] idf;
    private final Map<String, Map<String, Double>> eventVectors;
    private final Map<String, EventMeta> events;
    private final int nFeatures;

    public static class EventMeta {
        public final String id, name, category, imageUrl, date;
        public EventMeta(String id, String name, String category, String imageUrl, String date) {
            this.id = id; this.name = name; this.category = category;
            this.imageUrl = imageUrl; this.date = date;
        }
    }

    public static class RankedEvent {
        public final EventMeta meta;
        public final double score;
        public RankedEvent(EventMeta m, double s) { this.meta = m; this.score = s; }
    }

    public EventRecommender(Context ctx) throws Exception {
        JSONObject root = new JSONObject(readAsset(ctx.getAssets(), "weights_events.json"));

        JSONObject vec = root.getJSONObject("vectorizer");
        JSONObject jVocab = vec.getJSONObject("vocab");

        int maxIdx = -1;
        Iterator<String> it = jVocab.keys();
        while (it.hasNext()) {
            String term = it.next();
            int idx = jVocab.getInt(term);
            vocab.put(term, idx);
            if (idx > maxIdx) maxIdx = idx;
        }
        nFeatures = maxIdx + 1;

        double[] idfTmp = new double[nFeatures];
        JSONObject jIdf = vec.getJSONObject("idf");
        for (String term : vocab.keySet()) {
            int idx = vocab.get(term);
            idfTmp[idx] = jIdf.optDouble(term, 1.0);
        }
        this.idf = idfTmp;

        JSONObject ev = root.getJSONObject("event_vectors");
        Map<String, Map<String, Double>> evMap = new HashMap<>();
        Iterator<String> evIds = ev.keys();
        while (evIds.hasNext()) {
            String eid = evIds.next();
            JSONObject w = ev.getJSONObject(eid);
            Map<String, Double> sparse = new HashMap<>();
            Iterator<String> terms = w.keys();
            while (terms.hasNext()) {
                String t = terms.next();
                sparse.put(t, w.getDouble(t));
            }
            evMap.put(eid, sparse);
        }
        this.eventVectors = evMap;

        JSONObject meta = root.getJSONObject("events");
        Map<String, EventMeta> m = new HashMap<>();
        Iterator<String> ids = meta.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject o = meta.getJSONObject(id);
            m.put(id, new EventMeta(
                    id,
                    o.optString("name", ""),
                    o.optString("category", ""),
                    o.optString("image_url", ""),
                    o.optString("date", "")
            ));
        }
        this.events = m;
    }

    /** Recommend events for a given interest (category name). */
    public List<RankedEvent> recommend(String interest, int topK, boolean strictCategory) {
        Map<String, Double> q = vectorize(interest);

        List<RankedEvent> out = new ArrayList<>();
        for (String eid : eventVectors.keySet()) {
            EventMeta meta = events.get(eid);
            if (meta == null) continue;

            if (strictCategory) {
                if (!meta.category.equalsIgnoreCase(interest)) continue;
            } else {
                if (!(meta.category.equalsIgnoreCase(interest) ||
                        meta.category.toLowerCase().contains(interest.toLowerCase())))
                    continue;
            }

            double score = cosine(q, eventVectors.get(eid));
            out.add(new RankedEvent(meta, score));
        }

        out.sort((a, b) -> Double.compare(b.score, a.score));
        if (out.size() > topK) return out.subList(0, topK);
        return out;
    }

    // ----- Helpers -----
    private Map<String, Double> vectorize(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String tok : tokenize(text)) {
            tf.put(tok, tf.getOrDefault(tok, 0) + 1);
        }
        if (tf.isEmpty()) return Collections.emptyMap();

        Map<String, Double> weights = new HashMap<>();
        double sumsq = 0.0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            Integer idx = vocab.get(e.getKey());
            if (idx == null) continue;
            double w = e.getValue() * idf[idx];
            weights.put(e.getKey(), w);
            sumsq += w * w;
        }
        double norm = Math.sqrt(Math.max(1e-12, sumsq));
        for (String t : new ArrayList<>(weights.keySet())) {
            weights.put(t, weights.get(t) / norm);
        }
        return weights;
    }

    private static List<String> tokenize(String s) {
        if (s == null) return Collections.emptyList();
        s = s.toLowerCase(Locale.US);
        String[] parts = s.split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        double dot = 0.0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double wb = b.get(e.getKey());
            if (wb != null) dot += e.getValue() * wb;
        }
        return dot;
    }

    private static String readAsset(AssetManager am, String name) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(am.open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
