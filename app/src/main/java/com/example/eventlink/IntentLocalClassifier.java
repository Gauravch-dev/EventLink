package com.example.eventlink;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TF-IDF + multinomial logistic regression (softmax) inference.
 * Tolerant loader for a variety of JSON schemas and vocab shapes.
 * Updated for integration with IntentEvaluator rule-based logic.
 */
public class IntentLocalClassifier {

    private static final String TAG = "IntentLocalClassifier";

    public static class Prediction {
        public final String top1;
        public final double top1Prob;
        public final String top2;
        public final double top2Prob;
        public final double[] probs;

        public Prediction(String t1, double p1, String t2, double p2, double[] all) {
            this.top1 = t1;
            this.top1Prob = p1;
            this.top2 = t2;
            this.top2Prob = p2;
            this.probs = all;
        }
    }

    private final String[] classes;           // [nClasses]
    private final Map<String,Integer> vocab;  // token -> index
    private final double[] idf;               // [nFeatures], default 1.0
    private final double[][] coef;            // [nClasses][nFeatures]
    private final double[] intercept;         // [nClasses], default 0.0
    private final String analyzer;            // "char_wb" or "word"
    private final int nMin, nMax;
    private final int nClasses, nFeatures;

    // ------------------- Constructor -------------------

    public IntentLocalClassifier(Context ctx) throws Exception {
        this(ctx, "intent_model_android.json");
    }

    public IntentLocalClassifier(Context ctx, String assetName) throws Exception {
        String raw = readAsset(ctx.getAssets(), assetName);
        JSONObject root = new JSONObject(raw);

        // Allow nested or flat
        JSONObject vecNode = firstNonNull(
                root.optJSONObject("vectorizer"),
                root.optJSONObject("tfidf"),
                root.optJSONObject("vec"));
        if (vecNode == null) vecNode = root;

        JSONObject clfNode = firstNonNull(
                root.optJSONObject("classifier"),
                root.optJSONObject("model"),
                root.optJSONObject("clf"));
        if (clfNode == null) clfNode = root;

        // --- classes ---
        JSONArray jClasses = firstNonNullArray(
                clfNode.optJSONArray("classes"),
                root.optJSONArray("classes"));
        if (jClasses == null || jClasses.length() == 0) {
            throw new IllegalArgumentException("Model JSON missing 'classes' array.");
        }
        classes = new String[jClasses.length()];
        for (int i = 0; i < jClasses.length(); i++) classes[i] = jClasses.getString(i);
        nClasses = classes.length;

        // --- vocabulary ---
        VocabResult vr = loadVocabulary(vecNode, root);
        this.vocab = vr.vocab;
        this.nFeatures = vr.nFeatures;
        Log.i(TAG, "Vocab loaded: size=" + nFeatures + " (shape=" + vr.shape + ", source=" + vr.source + ")");

        // --- idf (optional) ---
        double[] idfTmp = new double[nFeatures];
        Arrays.fill(idfTmp, 1.0);
        JSONArray jIdf = firstNonNullArray(
                vecNode.optJSONArray("idf"),
                root.optJSONArray("idf"));
        if (jIdf != null && jIdf.length() == nFeatures) {
            for (int i = 0; i < nFeatures; i++) idfTmp[i] = jIdf.getDouble(i);
        }
        idf = idfTmp;

        // --- analyzer & ngram ---
        String ana = firstNonNullString(
                vecNode.optString("analyzer", null),
                root.optString("analyzer", null));
        analyzer = (ana == null || ana.isEmpty()) ? "char_wb" : ana;

        nMin = vecNode.has("ngram_min") ? vecNode.optInt("ngram_min", 3) : root.optInt("ngram_min", 3);
        nMax = vecNode.has("ngram_max") ? vecNode.optInt("ngram_max", 5) : root.optInt("ngram_max", 5);

        // --- coef ---
        JSONArray jCoef = firstNonNullArray(
                clfNode.optJSONArray("coef"),
                root.optJSONArray("coef"),
                clfNode.optJSONArray("coefficients"));
        if (jCoef == null || jCoef.length() != nClasses) {
            throw new IllegalArgumentException("Model JSON 'coef' must be [nClasses][nFeatures].");
        }
        coef = new double[nClasses][nFeatures];
        for (int r = 0; r < nClasses; r++) {
            JSONArray row = jCoef.getJSONArray(r);
            if (row.length() != nFeatures) {
                throw new IllegalArgumentException("coef row " + r + " length " + row.length() + " != nFeatures " + nFeatures);
            }
            for (int c = 0; c < nFeatures; c++) coef[r][c] = row.getDouble(c);
        }

        // --- intercept (optional)---
        JSONArray jInt = firstNonNullArray(
                clfNode.optJSONArray("intercept"),
                root.optJSONArray("intercept"),
                clfNode.optJSONArray("bias"));
        intercept = new double[nClasses];
        if (jInt != null && jInt.length() == nClasses) {
            for (int i = 0; i < nClasses; i++) intercept[i] = jInt.getDouble(i);
        } else {
            Arrays.fill(intercept, 0.0);
        }

        Log.i(TAG, "Loaded model: classes=" + nClasses + ", features=" + nFeatures +
                ", analyzer=" + analyzer + ", ngram=(" + nMin + "," + nMax + ")");
    }

    // ---------- robust vocabulary loader ----------

    private static final class VocabResult {
        final Map<String,Integer> vocab;
        final int nFeatures;
        final String shape;
        final String source;
        VocabResult(Map<String,Integer> v, int n, String shape, String source) {
            this.vocab = v; this.nFeatures = n; this.shape = shape; this.source = source;
        }
    }

    private VocabResult loadVocabulary(JSONObject vecNode, JSONObject root) throws Exception {
        // Try multiple nodes/keys
        Object voc = firstNonNullObject(
                vecNode.opt("vocab"),
                vecNode.opt("vocabulary"),
                vecNode.opt("token2id"),
                // nested vectorizer
                firstNonNullObject(
                        vecNode.optJSONObject("vectorizer") != null ? vecNode.getJSONObject("vectorizer").opt("vocabulary") : null,
                        vecNode.optJSONObject("vectorizer") != null ? vecNode.getJSONObject("vectorizer").opt("vocab") : null,
                        vecNode.optJSONObject("vectorizer") != null ? vecNode.getJSONObject("vectorizer").opt("token2id") : null
                ),
                root.opt("vocab"),
                root.opt("vocabulary"),
                root.opt("token2id")
        );

        if (voc == null) {
            Log.w(TAG, "Available keys in vectorizer: " + listKeys(vecNode));
            Log.w(TAG, "Available keys in root: " + listKeys(root));
            throw new IllegalArgumentException("Model JSON missing vocabulary (vocab/vocabulary/token2id).");
        }

        Map<String,Integer> map = new HashMap<>();
        int maxIdx = -1;
        String shape = "";
        String source = "";

        if (voc instanceof JSONObject) {
            JSONObject o = (JSONObject) voc;
            // check whether token->idx or idx->token
            int intLike = 0, total = 0;
            for (Iterator<String> it = o.keys(); it.hasNext();) {
                String k = it.next(); total++;
                if (k.matches("\\d+")) intLike++;
            }
            if (intLike > total / 2) {
                for (Iterator<String> it = o.keys(); it.hasNext();) {
                    String k = it.next();
                    int idx = Integer.parseInt(k);
                    String token = o.getString(k);
                    map.put(token, idx);
                    if (idx > maxIdx) maxIdx = idx;
                }
                shape = "map(idx->token,inverted)";
            } else {
                for (Iterator<String> it = o.keys(); it.hasNext();) {
                    String token = it.next();
                    int idx = o.getInt(token);
                    map.put(token, idx);
                    if (idx > maxIdx) maxIdx = idx;
                }
                shape = "map(token->idx)";
            }
            source = "JSONObject";
        } else if (voc instanceof JSONArray) {
            JSONArray arr = (JSONArray) voc;
            if (arr.length() > 0 && arr.get(0) instanceof JSONArray) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray pair = arr.getJSONArray(i);
                    String token = pair.getString(0);
                    int idx = pair.getInt(1);
                    map.put(token, idx);
                    if (idx > maxIdx) maxIdx = idx;
                }
                shape = "array-of-pairs";
            } else if (arr.length() > 0 && arr.get(0) instanceof String) {
                for (int i = 0; i < arr.length(); i++) {
                    String token = arr.getString(i);
                    map.put(token, i);
                }
                maxIdx = arr.length() - 1;
                shape = "array-of-tokens";
            } else {
                throw new IllegalArgumentException("Vocabulary array shape not recognized.");
            }
            source = "JSONArray";
        } else {
            throw new IllegalArgumentException("Vocabulary type not recognized: " + voc.getClass());
        }

        return new VocabResult(map, maxIdx + 1, shape, source);
    }

    private static String listKeys(JSONObject o) {
        if (o == null) return "(null)";
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = o.keys(); it.hasNext();) keys.add(it.next());
        Collections.sort(keys);
        return keys.toString();
    }

    private static JSONObject firstNonNull(JSONObject... arr) {
        for (JSONObject o : arr) if (o != null) return o;
        return null;
    }
    private static JSONArray firstNonNullArray(JSONArray... arr) {
        for (JSONArray a : arr) if (a != null) return a;
        return null;
    }
    private static String firstNonNullString(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return null;
    }
    private static Object firstNonNullObject(Object... arr) {
        for (Object o : arr) if (o != null) return o;
        return null;
    }

    private static String readAsset(AssetManager am, String name) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(am.open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // --------- Public API ---------

    public Prediction predict(String text) {
        double[] x = vectorize(text == null ? "" : text);
        double[] logits = matVec(coef, x, intercept);
        double[] probs = softmax(logits);

        int i1 = 0, i2 = (probs.length > 1 ? 1 : 0);
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > probs[i1]) { i2 = i1; i1 = i; }
            else if (i != i1 && probs[i] > probs[i2]) { i2 = i; }
        }
        return new Prediction(classes[i1], probs[i1], classes[i2], probs[i2], probs);
    }

    public Prediction predict(String text, double defaultThreshold, Map<String, Double> perClassThr) {
        Prediction p = predict(text);
        Double thr = perClassThr == null ? null : perClassThr.get(p.top1);
        double useThr = (thr != null) ? thr : defaultThreshold;

        int oosIdx = indexOfClass("out_of_scope");
        if (oosIdx >= 0 && p.top1Prob < useThr) {
            double[] forced = new double[p.probs.length];
            Arrays.fill(forced, 0.0);
            forced[oosIdx] = 1.0;
            int alt = bestAlt(oosIdx, p.probs);
            return new Prediction("out_of_scope", 1.0, classes[alt], p.probs[alt], forced);
        }
        return p;
    }

    public String[] classes() { return Arrays.copyOf(classes, classes.length); }

    // --------- Vectorizer ---------

    private double[] vectorize(String text) {
        Map<Integer, Double> counts = new HashMap<>();
        if ("word".equalsIgnoreCase(analyzer)) {
            List<String> words = simpleWordTokenize(text);
            for (String w : words) {
                List<String> ngrams = charNgrams(w, nMin, nMax);
                for (String ng : ngrams) bump(counts, ng);
            }
        } else { // char_wb default
            List<String> words = simpleWordTokenize(text);
            for (String w : words) {
                String padded = " " + w + " ";
                List<String> ngrams = charNgrams(padded, nMin, nMax);
                for (String ng : ngrams) bump(counts, ng);
            }
        }

        double[] x = new double[nFeatures];
        for (Map.Entry<Integer, Double> e : counts.entrySet()) {
            int idx = e.getKey();
            if (idx >= 0 && idx < nFeatures) {
                double tf = e.getValue();
                x[idx] = tf * idf[idx];
            }
        }

        // l2 normalize
        double norm = 0.0;
        for (double v : x) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < x.length; i++) x[i] /= norm;

        return x;
    }

    private void bump(Map<Integer, Double> counts, String token) {
        Integer idx = vocab.get(token);
        if (idx == null) return;
        counts.put(idx, counts.getOrDefault(idx, 0.0) + 1.0);
    }

    private static List<String> simpleWordTokenize(String s) {
        String cleaned = (s == null ? "" : s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        String[] parts = cleaned.trim().split("\\s+");
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static List<String> charNgrams(String s, int nMin, int nMax) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        int L = s.length();
        for (int n = nMin; n <= nMax; n++) {
            if (n > L) continue;
            for (int i = 0; i + n <= L; i++) out.add(s.substring(i, i + n));
        }
        return out;
    }

    // --------- Math ---------

    private static double[] matVec(double[][] A, double[] x, double[] b) {
        int R = A.length;
        double[] y = new double[R];
        for (int r = 0; r < R; r++) {
            double s = 0.0;
            double[] row = A[r];
            int len = Math.min(row.length, x.length);
            for (int c = 0; c < len; c++) s += row[c] * x[c];
            y[r] = s + (b != null && r < b.length ? b[r] : 0.0);
        }
        return y;
    }

    private static double[] softmax(double[] z) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : z) if (v > max) max = v;
        double sum = 0.0;
        double[] e = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            e[i] = Math.exp(z[i] - max);
            sum += e[i];
        }
        if (sum == 0.0) {
            Arrays.fill(e, 1.0 / z.length);
            return e;
        }
        for (int i = 0; i < e.length; i++) e[i] /= sum;
        return e;
    }

    private int indexOfClass(String name) {
        for (int i = 0; i < classes.length; i++) if (classes[i].equals(name)) return i;
        return -1;
    }

    private int bestAlt(int excludeIdx, double[] probs) {
        int best = -1; double bestVal = -1;
        for (int i = 0; i < probs.length; i++) {
            if (i == excludeIdx) continue;
            if (probs[i] > bestVal) { bestVal = probs[i]; best = i; }
        }
        return best < 0 ? 0 : best;
    }
}
