package com.example.proyectoarpdm.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.common.helpers.EmbeddingHelper;
import com.example.proyectoarpdm.common.helpers.RecognitionOverlayView;
import com.example.proyectoarpdm.models.ARModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

public class TestActivity extends AppCompatActivity {

    private static final String TAG = "TestActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // -----------------------------------------------------------------------
    // Umbral del score COMBINADO. Solo se reconoce si TAMBIÉN supera MIN_COSINE.
    // -----------------------------------------------------------------------
    private static final double THRESHOLD   = 0.50;
    private static final double MIN_COSINE  = 0.05;  // filtra falsos positivos por histograma

    // Tolerancia de zona central (30% del tamaño de la imagen en cada eje)
    private static final float CENTER_TOLERANCE = 0.30f;

    // Número de frames consecutivos con el mismo ganador para confirmar reconocimiento
    private static final int STABILITY_FRAMES = 2;

    // -----------------------------------------------------------------------
    // UI / cámara
    // -----------------------------------------------------------------------
    private PreviewView previewView;
    private RecognitionOverlayView overlayView;
    private TextView txtStatus, txtResult;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;

    // -----------------------------------------------------------------------
    // Reconocimiento
    // -----------------------------------------------------------------------
    private final List<ARModel> cachedModels = new ArrayList<>();
    private final Map<String, float[]> modelEmbeddings = new HashMap<>();
    private final Map<String, float[]> modelHistograms = new HashMap<>();

    private EmbeddingHelper embeddingHelper;
    private volatile boolean isReady = false;
    private final AtomicBoolean isDialogShowing = new AtomicBoolean(false);

    // Estabilización temporal (volatile: accedidos desde hilos ML Kit y cameraExecutor)
    private volatile String pendingName  = "";
    private volatile int    pendingCount = 0;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        txtStatus   = findViewById(R.id.txtStatus);
        txtResult   = findViewById(R.id.txtResult);

        cameraExecutor = Executors.newSingleThreadExecutor();

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(options);

        try {
            embeddingHelper = new EmbeddingHelper(this);
            Log.d(TAG, "EmbeddingHelper listo. size=" + embeddingHelper.getEmbeddingSize());
        } catch (IOException e) {
            Log.e(TAG, "Error cargando modelo TFLite", e);
            Toast.makeText(this, "Error al cargar modelo de reconocimiento",
                    Toast.LENGTH_LONG).show();
        }

        showNoticeAndDownload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (embeddingHelper != null) embeddingHelper.close();
        if (objectDetector  != null) objectDetector.close();
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == CAMERA_PERMISSION_CODE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Permiso de cámara denegado",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // -----------------------------------------------------------------------
    // Preparación: Firebase → descarga → embeddings + histogramas
    // -----------------------------------------------------------------------

    private void showNoticeAndDownload() {
        new AlertDialog.Builder(this)
                .setTitle("Modo Reconocimiento de Logos")
                .setMessage("Se descargarán las imágenes de referencia y se calcularán sus perfiles de color.")
                .setPositiveButton("Comenzar", (d, w) -> fetchModels())
                .setCancelable(false)
                .show();
    }

    private void fetchModels() {
        txtStatus.setText("Obteniendo datos de Firebase...");
        FirebaseDatabase.getInstance()
                .getReference("models")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> urls = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            ARModel model = ds.getValue(ARModel.class);
                            if (model != null && model.getImagen() != null) {
                                model.setId(ds.getKey());
                                if (model.getName() == null || model.getName().isEmpty())
                                    model.setName(ds.getKey());
                                cachedModels.add(model);
                                urls.add(model.getImagen());
                            }
                        }
                        Log.d(TAG, "Modelos Firebase: " + cachedModels.size());
                        downloadImages(urls);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Firebase error: " + error.getMessage());
                        txtStatus.setText("Error al conectar con Firebase");
                    }
                });
    }

    private void downloadImages(List<String> urls) {
        if (urls.isEmpty()) {
            txtStatus.setText("No hay imágenes para descargar");
            startCameraIfPermissionGranted();
            return;
        }
        txtStatus.setText("Descargando " + urls.size() + " imágenes...");
        final int[] count = {0};
        final int total = urls.size();

        for (String url : urls) {
            Glide.with(this)
                    .downloadOnly()
                    .load(url)
                    .listener(new RequestListener<File>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e,
                                                    Object model, Target<File> target,
                                                    boolean isFirstResource) {
                            Log.e(TAG, "Fallo descarga: " + url, e);
                            checkFinished(total, count);
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(File resource,
                                                       Object model, Target<File> target,
                                                       DataSource ds, boolean isFirstResource) {
                            processReference(resource, url, total, count);
                            return false;
                        }
                    })
                    .submit();
        }
    }

    private void processReference(File file, String url, int total, int[] count) {
        if (embeddingHelper == null) { checkFinished(total, count); return; }

        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bmp == null) {
            Log.w(TAG, "No decodificable: " + url);
            checkFinished(total, count);
            return;
        }

        cameraExecutor.execute(() -> {
            try {
                for (ARModel m : cachedModels) {
                    if (!url.equals(m.getImagen())) continue;

                    float[] emb  = embeddingHelper.getEmbedding(bmp);
                    float[] hist = embeddingHelper.getColorHistogram(bmp);

                    modelEmbeddings.put(m.getName(), emb);
                    modelHistograms.put(m.getName(), hist);

                    Log.d(TAG, "Referencia lista: " + m.getName()
                            + "  embSize=" + emb.length
                            + "  histSize=" + hist.length);
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando referencia: " + url, e);
            } finally {
                bmp.recycle();
                checkFinished(total, count);
            }
        });
    }

    private void checkFinished(int total, int[] count) {
        int n;
        synchronized (count) { n = ++count[0]; }
        runOnUiThread(() -> {
            txtStatus.setText("Descargando: " + n + "/" + total);
            if (n >= total) {
                Log.d(TAG, "Referencias listas: " + modelEmbeddings.size()
                        + " embeddings, " + modelHistograms.size() + " histogramas");
                Toast.makeText(this,
                        "¡Listo! " + modelEmbeddings.size() + " logos cargados",
                        Toast.LENGTH_SHORT).show();
                txtStatus.setText("Recursos listos. Iniciando cámara...");
                isReady = true;
                startCameraIfPermissionGranted();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Cámara
    // -----------------------------------------------------------------------

    private void startCameraIfPermissionGranted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cp = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cp.unbindAll();
                cp.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                runOnUiThread(() -> txtStatus.setVisibility(View.GONE));

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error cámara", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // -----------------------------------------------------------------------
    // Análisis de fotograma
    // -----------------------------------------------------------------------

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        android.media.Image mediaImage = imageProxy.getImage();

        if (mediaImage == null || !isReady || isDialogShowing.get()) {
            imageProxy.close();
            return;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);

        objectDetector.process(inputImage)
                .addOnSuccessListener(objects -> {
                    if (isDialogShowing.get()) return;

                    boolean found = false;
                    for (com.google.mlkit.vision.objects.DetectedObject obj : objects) {
                        if (!isObjectInCenter(obj.getBoundingBox(),
                                inputImage.getWidth(), inputImage.getHeight())) continue;

                        found = true;
                        if (modelEmbeddings.isEmpty()) break;

                        Bitmap frame = yuvToBitmap(imageProxy);
                        if (frame == null) break;

                        Bitmap rotated = rotateBitmap(frame, rotation);
                        Rect b = obj.getBoundingBox();
                        int l   = Math.max(0, b.left);
                        int t   = Math.max(0, b.top);
                        int r   = Math.min(rotated.getWidth(),  b.right);
                        int bot = Math.min(rotated.getHeight(), b.bottom);

                        if (r > l && bot > t) {
                            Bitmap crop = Bitmap.createBitmap(rotated, l, t, r - l, bot - t);
                            compareCombined(crop);
                        }

                        if (rotated != frame) rotated.recycle();
                        frame.recycle();
                        break;
                    }
                    if (!found) {
                        pendingName  = "";
                        pendingCount = 0;
                        updateUI(false, "");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "ML Kit error", e))
                .addOnCompleteListener(t -> imageProxy.close());
    }

    // -----------------------------------------------------------------------
    // Comparación combinada embedding + histograma
    // -----------------------------------------------------------------------

    private void compareCombined(Bitmap crop) {
        if (embeddingHelper == null || modelEmbeddings.isEmpty()) {
            crop.recycle();
            return;
        }

        cameraExecutor.execute(() -> {
            if (isDialogShowing.get()) {
                crop.recycle();
                return;
            }
            try {
                float[] frameEmb  = embeddingHelper.getEmbedding(crop);
                float[] frameHist = embeddingHelper.getColorHistogram(crop);

                String bestName  = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                double bestCos   = 0, bestHist = 0;

                for (String name : modelEmbeddings.keySet()) {
                    float[] refEmb  = modelEmbeddings.get(name);
                    float[] refHist = modelHistograms.get(name);
                    if (refEmb == null || refHist == null) continue;

                    double score = embeddingHelper.combinedScore(
                            frameEmb, refEmb, frameHist, refHist);
                    double cos  = embeddingHelper.cosineSimilarity(frameEmb, refEmb);
                    double hist = embeddingHelper.histogramSimilarity(frameHist, refHist);

                    Log.d(TAG, String.format(Locale.US,
                            "[%s]  combined=%.4f  cos=%.4f  hist=%.4f",
                            name, score, cos, hist));

                    if (score > bestScore) {
                        bestScore = score;
                        bestName  = name;
                        bestCos   = cos;
                        bestHist  = hist;
                    }
                }

                final String fn = bestName;
                final double fs = bestScore;
                final double fc = bestCos;
                final double fh = bestHist;

                // Requiere superar umbral combinado Y umbral mínimo de coseno
                if (fn != null && fs >= THRESHOLD && fc >= MIN_COSINE) {
                    Log.d(TAG, String.format(Locale.US,
                            "✓ CANDIDATO: %s  combined=%.4f  cos=%.4f  hist=%.4f  (frame %d/%d)",
                            fn, fs, fc, fh, pendingCount + 1, STABILITY_FRAMES));

                    if (fn.equals(pendingName)) {
                        pendingCount++;
                    } else {
                        pendingName  = fn;
                        pendingCount = 1;
                    }

                    if (pendingCount >= STABILITY_FRAMES) {
                        Log.d(TAG, "✓✓ RECONOCIDO ESTABLE: " + fn);
                        isDialogShowing.set(true); // PAUSA INMEDIATA
                        runOnUiThread(() -> {
                            updateUI(true, fn);
                            showRecognitionModal(fn);
                        });
                    }

                } else {
                    Log.d(TAG, String.format(Locale.US,
                            "✗ Sin match. Mejor: %s=%.4f  cos=%.4f (umbral=%.2f, min_cos=%.2f)",
                            fn, fs, fc, THRESHOLD, MIN_COSINE));
                    pendingName  = "";
                    pendingCount = 0;
                    runOnUiThread(() -> updateUI(false, ""));
                }

            } finally {
                crop.recycle();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Utilidades de imagen
    // -----------------------------------------------------------------------

    private Bitmap yuvToBitmap(@NonNull ImageProxy proxy) {
        try {
            ImageProxy.PlaneProxy[] planes = proxy.getPlanes();
            int W = proxy.getWidth(), H = proxy.getHeight();

            ByteBuffer yBuf = planes[0].getBuffer();
            ByteBuffer uBuf = planes[1].getBuffer();
            ByteBuffer vBuf = planes[2].getBuffer();

            int yStride  = planes[0].getRowStride();
            int uvStride = planes[1].getRowStride();
            int uvPixel  = planes[1].getPixelStride();

            byte[] nv21 = new byte[W * H + 2 * (W / 2) * (H / 2)];

            int idx = 0;
            yBuf.rewind();
            for (int row = 0; row < H; row++) {
                yBuf.position(row * yStride);
                yBuf.get(nv21, idx, W);
                idx += W;
            }

            vBuf.rewind(); uBuf.rewind();
            for (int row = 0; row < H / 2; row++) {
                for (int col = 0; col < W / 2; col++) {
                    int pos = row * uvStride + col * uvPixel;
                    nv21[idx++] = vBuf.get(pos);
                    nv21[idx++] = uBuf.get(pos);
                }
            }

            android.graphics.YuvImage yuv = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21, W, H, null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, W, H), 90, out);
            byte[] bytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        } catch (Exception e) {
            Log.e(TAG, "yuvToBitmap error", e);
            return null;
        }
    }

    private Bitmap rotateBitmap(Bitmap bmp, int degrees) {
        if (degrees == 0) return bmp;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private boolean isObjectInCenter(Rect b, int w, int h) {
        return Math.abs(b.centerX() - w / 2f) < w * CENTER_TOLERANCE
                && Math.abs(b.centerY() - h / 2f) < h * CENTER_TOLERANCE;
    }

    // -----------------------------------------------------------------------
    // UI y Navegación AR
    // -----------------------------------------------------------------------

    private void showRecognitionModal(String modelName) {
        ARModel match = null;
        for (ARModel m : cachedModels) {
            if (modelName.equals(m.getName())) {
                match = m;
                break;
            }
        }

        if (match == null) {
            isDialogShowing.set(false);
            return;
        }

        final ARModel finalMatch = match;
        String location = "Lat: " + match.getLatitud() + ", Lon: " + match.getLongitud();

        new AlertDialog.Builder(this)
                .setTitle("Objeto Reconocido")
                .setMessage("Nombre: " + match.getName() + "\nUbicación: " + location)
                .setPositiveButton("Ver en AR", (dialog, which) -> {
                    downloadAndLaunchAR(finalMatch);
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    isDialogShowing.set(false);
                    pendingName = "";
                    pendingCount = 0;
                    updateUI(false, "");
                })
                .setCancelable(false)
                .show();
    }

    private void downloadAndLaunchAR(ARModel model) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("Descargando recursos...")
                .setCancelable(false)
                .show();

        File localModelFile = new File(getCacheDir(), model.getId() + ".glb");
        String modelUrl = model.getModelUrl();

        if (localModelFile.exists() && localModelFile.length() > 1024) {
            downloadSlidesAndLaunch(model, localModelFile.getAbsolutePath(), progressDialog);
            return;
        }

        if (modelUrl == null || modelUrl.isEmpty()) {
            progressDialog.dismiss();
            Toast.makeText(this, "Error: URL del modelo no válida", Toast.LENGTH_SHORT).show();
            isDialogShowing.set(false);
            return;
        }

        if (modelUrl.startsWith("gs://") || modelUrl.contains("firebasestorage.googleapis.com")) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(modelUrl);
            storageRef.getFile(localModelFile).addOnSuccessListener(taskSnapshot -> {
                downloadSlidesAndLaunch(model, localModelFile.getAbsolutePath(), progressDialog);
            }).addOnFailureListener(exception -> {
                progressDialog.dismiss();
                Toast.makeText(this, "Error al descargar modelo", Toast.LENGTH_SHORT).show();
                isDialogShowing.set(false);
            });
        } else {
            downloadFileFromUrl(modelUrl, localModelFile, () ->
                    downloadSlidesAndLaunch(model, localModelFile.getAbsolutePath(), progressDialog), progressDialog);
        }
    }

    private void downloadSlidesAndLaunch(ARModel model, String localPath, AlertDialog progressDialog) {
        String slidesUrl = model.getSlidesUrl();
        
        // Fallback: Si slidesUrl está vacío, buscar una URL de PDF en la lista de slides
        if (slidesUrl == null || slidesUrl.isEmpty()) {
            List<String> list = model.getSlides();
            if (list != null) {
                for (String s : list) {
                    if (s != null && (s.toLowerCase().endsWith(".pdf") || s.contains(".pdf?"))) {
                        slidesUrl = s;
                        break;
                    }
                }
            }
        }

        if (slidesUrl == null || slidesUrl.isEmpty()) {
            progressDialog.dismiss();
            launchHelloAR(localPath, null, model.getSlides());
            return;
        }

        File localSlidesFile = new File(getCacheDir(), model.getId() + "_slides.pdf");
        if (localSlidesFile.exists() && localSlidesFile.length() > 5000) {
            progressDialog.dismiss();
            launchHelloAR(localPath, localSlidesFile.getAbsolutePath(), model.getSlides());
            return;
        }

        if (slidesUrl.startsWith("gs://") || slidesUrl.contains("firebasestorage.googleapis.com")) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(slidesUrl);
            storageRef.getFile(localSlidesFile).addOnSuccessListener(taskSnapshot -> {
                progressDialog.dismiss();
                launchHelloAR(localPath, localSlidesFile.getAbsolutePath(), model.getSlides());
            }).addOnFailureListener(exception -> {
                progressDialog.dismiss();
                launchHelloAR(localPath, null, model.getSlides());
            });
        } else {
            downloadFileFromUrl(slidesUrl, localSlidesFile, () -> {
                progressDialog.dismiss();
                launchHelloAR(localPath, localSlidesFile.getAbsolutePath(), model.getSlides());
            }, progressDialog);
        }
    }

    private void downloadFileFromUrl(String urlString, File destination, Runnable onSuccess, AlertDialog progressDialog) {
        new Thread(() -> {
            try {
                String encodedUrl = urlString.replace(" ", "%20");
                java.net.URL url = new java.net.URL(encodedUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() != java.net.HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + connection.getResponseCode());
                }

                try (java.io.InputStream input = connection.getInputStream();
                     java.io.OutputStream output = new java.io.FileOutputStream(destination)) {
                    byte[] data = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = input.read(data)) != -1) {
                        output.write(data, 0, bytesRead);
                    }
                }
                runOnUiThread(onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "Error descarga: " + urlString, e);
                runOnUiThread(() -> {
                    if (destination.getName().endsWith(".pdf")) {
                        onSuccess.run();
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Fallo de red", Toast.LENGTH_SHORT).show();
                        isDialogShowing.set(false);
                    }
                });
            }
        }).start();
    }

    private void launchHelloAR(String modelPath, String slidesPath, List<String> slides) {
        android.content.Intent intent = new android.content.Intent(this, com.example.proyectoarpdm.helloar.HelloArActivity.class);
        intent.putExtra("model_path", modelPath);
        if (slidesPath != null) intent.putExtra("slides_url", slidesPath);
        
        if (slides != null && !slides.isEmpty()) {
            ArrayList<String> filteredSlides = new ArrayList<>();
            for (String s : slides) {
                // Si ya tenemos un PDF descargado, no mostramos la URL del PDF como texto
                if (slidesPath != null && s != null && (s.toLowerCase().endsWith(".pdf") || s.contains(".pdf?"))) {
                    continue;
                }
                filteredSlides.add(s);
            }
            if (!filteredSlides.isEmpty()) {
                intent.putStringArrayListExtra("slides", filteredSlides);
            }
        }
        startActivity(intent);
        isDialogShowing.set(false);
        pendingName = "";
        pendingCount = 0;
    }

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------

    private void updateUI(boolean recognized, String name) {
        if (isDialogShowing.get() && !recognized) return;
        runOnUiThread(() -> {
            overlayView.setRecognized(recognized);
            if (recognized && !name.isEmpty()) {
                txtResult.setVisibility(View.VISIBLE);
                txtResult.setText("Reconocido: " + name);
            } else {
                txtResult.setVisibility(View.GONE);
            }
        });
    }
}