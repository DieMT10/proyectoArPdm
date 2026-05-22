package com.example.proyectoarpdm.common.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * EmbeddingHelper — optimizado para reconocimiento de logos (<10 referencias).
 *
 * Estrategia combinada:
 *   score final = α * cosine_similarity + (1-α) * histogram_similarity
 *
 * Con α = 0.75 (75% embedding, 25% histograma).
 * Se usa junto con MIN_COSINE en TestActivity para filtrar falsos positivos
 * donde el histograma coincide por colores genéricos pero el embedding no.
 */
public class EmbeddingHelper {

    private static final String TAG = "EmbeddingHelper";
    private static final String MODEL_FILE = "mobilenet_v2_embeddings.tflite";
    private static final int INPUT_SIZE = 224;

    // Peso del embedding coseno en el score combinado (0.0 – 1.0)
    // 0.75 = 75% embedding + 25% histograma
    public static final float EMBEDDING_WEIGHT = 0.75f;

    // Bins por canal para el histograma de color (8 = 8x8x8 = 512 bins total)
    private static final int HIST_BINS = 8;

    private final Interpreter interpreter;
    private final boolean isInputQuantized;
    private final boolean isOutputQuantized;
    private final int embeddingSize;
    private final float outputScale;
    private final int outputZeroPoint;

    public EmbeddingHelper(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        MappedByteBuffer modelBuffer = loadModelFile(context);
        interpreter = new Interpreter(modelBuffer, options);

        Tensor inputTensor = interpreter.getInputTensor(0);
        isInputQuantized = inputTensor.dataType() == org.tensorflow.lite.DataType.UINT8;

        Tensor outputTensor = interpreter.getOutputTensor(0);
        isOutputQuantized = outputTensor.dataType() == org.tensorflow.lite.DataType.UINT8;
        int[] outputShape = outputTensor.shape();
        embeddingSize = outputShape[outputShape.length - 1];
        outputScale = outputTensor.quantizationParams().getScale();
        outputZeroPoint = outputTensor.quantizationParams().getZeroPoint();

        Log.d(TAG, "Modelo listo. UINT8in=" + isInputQuantized
                + " UINT8out=" + isOutputQuantized
                + " size=" + embeddingSize);
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (android.content.res.AssetFileDescriptor fd =
                     context.getAssets().openFd(MODEL_FILE);
             FileInputStream is = new FileInputStream(fd.getFileDescriptor())) {
            return is.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(),
                    fd.getDeclaredLength());
        }
    }

    // -------------------------------------------------------------------------
    // Embedding
    // -------------------------------------------------------------------------

    public float[] getEmbedding(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        Object input = isInputQuantized
                ? preprocessUint8(resized)
                : preprocessFloat(resized);

        if (isOutputQuantized) {
            byte[][] out = new byte[1][embeddingSize];
            interpreter.run(input, out);
            return dequantize(out[0]);
        } else {
            float[][] out = new float[1][embeddingSize];
            interpreter.run(input, out);
            return l2normalize(out[0]);
        }
    }

    private ByteBuffer preprocessFloat(Bitmap bmp) {
        ByteBuffer buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buf.order(ByteOrder.nativeOrder());
        int[] px = new int[INPUT_SIZE * INPUT_SIZE];
        bmp.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int p : px) {
            buf.putFloat(((p >> 16 & 0xFF) / 127.5f) - 1f);
            buf.putFloat(((p >>  8 & 0xFF) / 127.5f) - 1f);
            buf.putFloat(((p       & 0xFF) / 127.5f) - 1f);
        }
        return buf;
    }

    private ByteBuffer preprocessUint8(Bitmap bmp) {
        ByteBuffer buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3);
        buf.order(ByteOrder.nativeOrder());
        int[] px = new int[INPUT_SIZE * INPUT_SIZE];
        bmp.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int p : px) {
            buf.put((byte) (p >> 16 & 0xFF));
            buf.put((byte) (p >>  8 & 0xFF));
            buf.put((byte) (p       & 0xFF));
        }
        return buf;
    }

    private float[] dequantize(byte[] bytes) {
        float[] r = new float[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            r[i] = ((bytes[i] & 0xFF) - outputZeroPoint) * outputScale;
        return l2normalize(r);
    }

    private float[] l2normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-10) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    // -------------------------------------------------------------------------
    // Histograma de color 3D (R, G, B cuantizados a HIST_BINS niveles)
    // -------------------------------------------------------------------------

    public float[] getColorHistogram(Bitmap bitmap) {
        Bitmap small = Bitmap.createScaledBitmap(bitmap, 64, 64, true);
        int total = 64 * 64;
        int[] px = new int[total];
        small.getPixels(px, 0, 64, 0, 0, 64, 64);

        float[] hist = new float[HIST_BINS * HIST_BINS * HIST_BINS];
        float binSize = 256f / HIST_BINS;

        for (int p : px) {
            int r = (int) ((p >> 16 & 0xFF) / binSize);
            int g = (int) ((p >>  8 & 0xFF) / binSize);
            int b = (int) ((p       & 0xFF) / binSize);
            r = Math.min(r, HIST_BINS - 1);
            g = Math.min(g, HIST_BINS - 1);
            b = Math.min(b, HIST_BINS - 1);
            hist[r * HIST_BINS * HIST_BINS + g * HIST_BINS + b]++;
        }

        for (int i = 0; i < hist.length; i++) hist[i] /= total;

        if (small != bitmap) small.recycle();
        return hist;
    }

    // -------------------------------------------------------------------------
    // Métricas de similitud
    // -------------------------------------------------------------------------

    /** Similitud coseno entre vectores L2-normalizados. Rango [-1, 1]. */
    public double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0;
        double dot = 0;
        for (int i = 0; i < v1.length; i++) dot += v1[i] * v2[i];
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * Similitud de histogramas mediante intersección normalizada.
     * Rango [0, 1]; 1 = histogramas idénticos.
     */
    public double histogramSimilarity(float[] h1, float[] h2) {
        if (h1 == null || h2 == null || h1.length != h2.length) return 0;
        double intersection = 0;
        for (int i = 0; i < h1.length; i++)
            intersection += Math.min(h1[i], h2[i]);
        return Math.max(0.0, Math.min(1.0, intersection));
    }

    /**
     * Score combinado:
     *   EMBEDDING_WEIGHT * cosine + (1 - EMBEDDING_WEIGHT) * histogram
     *
     * cosineSim se mapea de [-1,1] → [0,1] antes de combinar.
     */
    public double combinedScore(float[] emb1, float[] emb2,
                                float[] hist1, float[] hist2) {
        double cos  = (cosineSimilarity(emb1, emb2) + 1.0) / 2.0;
        double hist = histogramSimilarity(hist1, hist2);
        return EMBEDDING_WEIGHT * cos + (1f - EMBEDDING_WEIGHT) * hist;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    public int getEmbeddingSize() { return embeddingSize; }

    public void close() {
        if (interpreter != null) interpreter.close();
    }
}