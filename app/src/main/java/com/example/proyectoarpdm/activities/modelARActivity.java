package com.example.proyectoarpdm.activities;

import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.common.helpers.CameraPermissionHelper;
import com.example.proyectoarpdm.common.helpers.DepthSettings;
import com.example.proyectoarpdm.common.helpers.DisplayRotationHelper;
import com.example.proyectoarpdm.common.helpers.InstantPlacementSettings;
import com.example.proyectoarpdm.common.helpers.SnackbarHelper;
import com.example.proyectoarpdm.common.helpers.TapHelper;
import com.example.proyectoarpdm.common.helpers.TrackingStateHelper;
import com.example.proyectoarpdm.common.samplerender.Framebuffer;
import com.example.proyectoarpdm.common.samplerender.GLError;
import com.example.proyectoarpdm.common.samplerender.Mesh;
import com.example.proyectoarpdm.common.samplerender.SampleRender;
import com.example.proyectoarpdm.common.samplerender.Shader;
import com.example.proyectoarpdm.common.samplerender.Texture;
import com.example.proyectoarpdm.common.samplerender.VertexBuffer;
import com.example.proyectoarpdm.common.samplerender.arcore.BackgroundRenderer;
import com.example.proyectoarpdm.common.samplerender.arcore.PlaneRenderer;
import com.example.proyectoarpdm.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class modelARActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = "modelARActivity";
    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
    private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";

    private static final float[] sphericalHarmonicFactors = {
            0.282095f, -0.325735f, 0.325735f, -0.325735f,
            0.273137f, -0.273137f, 0.078848f, -0.273137f, 0.136569f,
    };

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

    private GLSurfaceView surfaceView;
    private boolean installRequested;
    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private SampleRender render;
    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    private long lastPointCloudTimestamp = 0;

    private List<Mesh> virtualObjectMeshes = new ArrayList<>();
    private Shader virtualObjectShader;
    private Texture fallbackWhiteTexture;

    private final List<wrapped_Anchor> wrappedAnchors = new ArrayList<>();

    private Texture dfgTexture;
    private SpecularCubemapFilter cubemapFilter;

    private final float[] modelMatrix      = new float[16];
    private final float[] viewMatrix       = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix  = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix  = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection  = new float[4];

    private float scaleFactor  = 0.1f;
    private float rotationX    = 0f;
    private float rotationY    = 0f;
    private float rotationZ    = 0f;
    private float translateX   = 0f;
    private float translateY   = 0f;
    private float translateZ   = 0f;

    private static final float SCALE_STEP     = 0.05f;
    private static final float ROTATION_STEP  = 15f;
    private static final float TRANSLATE_STEP = 0.01f;

    private String modelPath;
    private String[] slidePages;

    // Slideshow UI
    private View slideshowOverlay;
    private TextView slideTitle;
    private TextView slideContent;
    private Button btnPrev;
    private Button btnNext;
    private int currentSlideIndex = 0;

    // ─────────────────────────────────────────────────────────────────────────
    //  onCreate
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.model_aractivity);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        modelPath = getIntent().getStringExtra("model_path");
        ArrayList<String> incomingSlides = getIntent().getStringArrayListExtra("slides");
        if (incomingSlides != null && !incomingSlides.isEmpty()) {
            slidePages = incomingSlides.toArray(new String[0]);
        } else {
            slidePages = new String[]{
                    "Bienvenido al visor de producto AR.",
                    "Puedes rotar el modelo usando los botones inferiores.",
                    "También puedes ajustar el tamaño para ver detalles.",
                    "Toca el modelo para ver esta información de nuevo.",
                    "¡Explora el producto en tu entorno real!"
            };
        }

        if (modelPath == null || modelPath.isEmpty()) {
            Toast.makeText(this, "Missing model path", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Botones de escala
        ImageButton btnIncrease = findViewById(R.id.btn_increase);
        ImageButton btnDecrease = findViewById(R.id.btn_decrease);
        btnIncrease.setOnClickListener(v -> scaleFactor = Math.min(scaleFactor + SCALE_STEP, 2.0f));
        btnDecrease.setOnClickListener(v -> scaleFactor = Math.max(scaleFactor - SCALE_STEP, 0.05f));

        // Botones de rotación
        ImageButton btnRotateYLeft  = findViewById(R.id.btn_rotate_y_left);
        ImageButton btnRotateYRight = findViewById(R.id.btn_rotate_y_right);
        ImageButton btnRotateXUp    = findViewById(R.id.btn_rotate_x_up);
        ImageButton btnRotateXDown  = findViewById(R.id.btn_rotate_x_down);
        ImageButton btnRotateZLeft  = findViewById(R.id.btn_rotate_z_left);
        ImageButton btnRotateZRight = findViewById(R.id.btn_rotate_z_right);

        btnRotateYLeft.setOnClickListener(v  -> rotationY = (rotationY - ROTATION_STEP) % 360f);
        btnRotateYRight.setOnClickListener(v -> rotationY = (rotationY + ROTATION_STEP) % 360f);
        btnRotateXUp.setOnClickListener(v    -> rotationX = (rotationX + ROTATION_STEP) % 360f);
        btnRotateXDown.setOnClickListener(v  -> rotationX = (rotationX - ROTATION_STEP) % 360f);
        btnRotateZLeft.setOnClickListener(v  -> rotationZ = (rotationZ - ROTATION_STEP) % 360f);
        btnRotateZRight.setOnClickListener(v -> rotationZ = (rotationZ + ROTATION_STEP) % 360f);

        // Botones de traslación
        ImageButton btnTransXPlus  = findViewById(R.id.btn_trans_x_plus);
        ImageButton btnTransXMinus = findViewById(R.id.btn_trans_x_minus);
        ImageButton btnTransYPlus  = findViewById(R.id.btn_trans_y_plus);
        ImageButton btnTransYMinus = findViewById(R.id.btn_trans_y_minus);
        ImageButton btnTransZPlus  = findViewById(R.id.btn_trans_z_plus);
        ImageButton btnTransZMinus = findViewById(R.id.btn_trans_z_minus);
        ImageButton btnResetCoords = findViewById(R.id.btn_reset_coords);

        btnTransXPlus.setOnClickListener(v  -> translateX += TRANSLATE_STEP);
        btnTransXMinus.setOnClickListener(v -> translateX -= TRANSLATE_STEP);
        btnTransYPlus.setOnClickListener(v  -> translateY += TRANSLATE_STEP);
        btnTransYMinus.setOnClickListener(v -> translateY -= TRANSLATE_STEP);
        btnTransZPlus.setOnClickListener(v  -> translateZ += TRANSLATE_STEP);
        btnTransZMinus.setOnClickListener(v -> translateZ -= TRANSLATE_STEP);
        btnResetCoords.setOnClickListener(v -> {
            translateX  = 0f;
            translateY  = 0f;
            translateZ  = 0f;
            rotationX   = 0f;
            rotationY   = 0f;
            rotationZ   = 0f;
            scaleFactor = 0.1f;
        });

        // GLSurfaceView y render
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);
        render = new SampleRender(surfaceView, this, getAssets());
        installRequested = false;
        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);
        instantPlacementSettings.setInstantPlacementEnabled(true);

        // Slideshow UI
        slideshowOverlay = findViewById(R.id.slideshow_overlay);
        slideTitle       = findViewById(R.id.slide_title);
        slideContent     = findViewById(R.id.slide_content);
        btnPrev          = findViewById(R.id.btn_prev);
        btnNext          = findViewById(R.id.btn_next);

        btnPrev.setOnClickListener(v -> {
            if (currentSlideIndex > 0) { currentSlideIndex--; updateSlideUI(); }
        });
        btnNext.setOnClickListener(v -> {
            if (currentSlideIndex < slidePages.length - 1) { currentSlideIndex++; updateSlideUI(); }
        });
        Button btnClose = findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> slideshowOverlay.setVisibility(View.GONE));

        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(modelARActivity.this, v);
            popup.setOnMenuItemClickListener(modelARActivity.this::settingsMenuClick);
            popup.inflate(R.menu.settings_menu);
            popup.show();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Settings menu
    // ─────────────────────────────────────────────────────────────────────────
    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        if (session != null) { session.close(); session = null; }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
                if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                    switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                        case INSTALL_REQUESTED: installRequested = true; return;
                        case INSTALLED: break;
                    }
                }
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }
                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore"; exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore"; exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app"; exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR"; exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session"; exception = e;
            }
            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }
        try {
            configureSession();
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SampleRender.Renderer — onSurfaceCreated
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onSurfaceCreated(SampleRender render) {
        try {
            planeRenderer           = new PlaneRenderer(render);
            backgroundRenderer      = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);

            cubemapFilter = new SpecularCubemapFilter(
                    render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

            // Textura DFG para el pipeline PBR
            dfgTexture = new Texture(
                    render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);
            final int dfgResolution = 64;
            final int dfgChannels   = 2;
            final int halfFloatSize = 2;
            ByteBuffer buffer = ByteBuffer.allocateDirect(
                    dfgResolution * dfgResolution * dfgChannels * halfFloatSize);

            // FIX: usar Channels.newChannel para garantizar lectura completa del stream
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                Channels.newChannel(is).read(buffer);
                buffer.rewind();
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F,
                    dfgResolution, dfgResolution, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

            // Point cloud
            pointCloudShader = Shader.createFromAssets(render,
                            "shaders/point_cloud.vert", "shaders/point_cloud.frag", null)
                    .setVec4("u_Color",
                            new float[]{31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                    .setFloat("u_PointSize", 5.0f);
            pointCloudVertexBuffer = new VertexBuffer(render, 4, null);
            pointCloudMesh = new Mesh(render, Mesh.PrimitiveMode.POINTS, null,
                    new VertexBuffer[]{pointCloudVertexBuffer});

            // Crear fallbackWhiteTexture ANTES del shader
            android.graphics.Bitmap whiteBitmap = android.graphics.Bitmap.createBitmap(
                    1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            whiteBitmap.setPixel(0, 0, android.graphics.Color.WHITE);
            fallbackWhiteTexture = Texture.createFromBitmap(render, whiteBitmap,
                    Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);

            // Cargar modelo .glb (texturas embebidas)
            try {
                if (modelPath.startsWith("/")) {
                    virtualObjectMeshes = Mesh.createMeshesFromFile(render, modelPath);
                } else {
                    virtualObjectMeshes = Mesh.createMeshesFromAsset(render, modelPath);
                }
                if (!virtualObjectMeshes.isEmpty()) {
                    Log.d(TAG, "Loaded " + virtualObjectMeshes.size() + " meshes");
                    for (Mesh m : virtualObjectMeshes) m.debugPrintInfo();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to load 3D model: " + modelPath, e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Error al cargar el modelo: " + modelPath,
                                Toast.LENGTH_LONG).show());
            }

            // Shader PBR
            virtualObjectShader = Shader.createFromAssets(render,
                            "shaders/environmental_hdr.vert",
                            "shaders/environmental_hdr.frag",
                            java.util.Collections.singletonMap("NUMBER_OF_MIPMAP_LEVELS",
                                    String.valueOf(cubemapFilter.getNumberOfMipmapLevels())))
                    .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                    .setTexture("u_DfgTexture", dfgTexture);

            // Asignar texturas por defecto al shader
            virtualObjectShader.setTexture("u_AlbedoTexture", fallbackWhiteTexture);
            virtualObjectShader.setTexture("u_RoughnessMetallicAmbientOcclusionTexture", fallbackWhiteTexture);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SampleRender.Renderer — onDrawFrame
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null || virtualObjectShader == null) return;

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            return;
        }
        Camera camera = frame.getCamera();

        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
            return;
        }
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion()
                || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage16Bits()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) { /* normal */ }
        }

        handleTap(frame, camera);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        String message = null;
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            message = camera.getTrackingFailureReason() == TrackingFailureReason.NONE
                    ? SEARCHING_PLANE_MESSAGE
                    : TrackingStateHelper.getTrackingFailureReasonString(camera);
        } else if (hasTrackingPlane()) {
            if (wrappedAnchors.isEmpty()) message = WAITING_FOR_TAP_MESSAGE;
        } else {
            message = SEARCHING_PLANE_MESSAGE;
        }
        if (message == null) messageSnackbarHelper.hide(this);
        else messageSnackbarHelper.showMessage(this, message);

        if (frame.getTimestamp() != 0) backgroundRenderer.drawBackground(render);
        if (camera.getTrackingState() == TrackingState.PAUSED) return;

        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.getPoints());
                lastPointCloudTimestamp = pointCloud.getTimestamp();
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            render.draw(pointCloudMesh, pointCloudShader);
        }

        planeRenderer.drawPlanes(render, session.getAllTrackables(Plane.class),
                camera.getDisplayOrientedPose(), projectionMatrix);

        // Valores PBR por defecto por si la estimación de luz no está lista aún
        virtualObjectShader.setBool("u_LightEstimateIsValid", false);
        float[] identityMatrix = new float[16];
        Matrix.setIdentityM(identityMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", identityMatrix);
        virtualObjectShader.setVec4("u_ViewLightDirection", new float[]{0f, 1f, 0f, 0f});
        virtualObjectShader.setVec3("u_LightIntensity", new float[]{1f, 1f, 1f});
        float[] zeroSH = new float[9 * 3];
        virtualObjectShader.setVec3Array("u_SphericalHarmonicsCoefficients", zeroSH);

        updateLightEstimation(frame.getLightEstimate(), viewMatrix);
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        for (wrapped_Anchor wrappedAnchor : wrappedAnchors) {
            Anchor anchor = wrappedAnchor.anchor();
            if (anchor.getTrackingState() != TrackingState.TRACKING) continue;

            float[] localModelMatrix = new float[16];
            anchor.getPose().toMatrix(localModelMatrix, 0);

            // 1. Traslación LOCAL
            float[] translationMatrix = new float[16];
            Matrix.setIdentityM(translationMatrix, 0);
            Matrix.translateM(translationMatrix, 0, translateX, translateY, translateZ);

            float[] tempMatrix = new float[16];
            Matrix.multiplyMM(tempMatrix, 0, localModelMatrix, 0, translationMatrix, 0);
            System.arraycopy(tempMatrix, 0, localModelMatrix, 0, 16);

            // 2. Rotación
            float[] rotationMatrix = new float[16];
            Matrix.setIdentityM(rotationMatrix, 0);
            Matrix.rotateM(rotationMatrix, 0, rotationX, 1f, 0f, 0f);
            Matrix.rotateM(rotationMatrix, 0, rotationY, 0f, 1f, 0f);
            Matrix.rotateM(rotationMatrix, 0, rotationZ, 0f, 0f, 1f);

            Matrix.multiplyMM(tempMatrix, 0, localModelMatrix, 0, rotationMatrix, 0);
            System.arraycopy(tempMatrix, 0, localModelMatrix, 0, 16);

            // 3. Escala
            float[] scaleMatrix = new float[16];
            Matrix.setIdentityM(scaleMatrix, 0);
            Matrix.scaleM(scaleMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

            Matrix.multiplyMM(tempMatrix, 0, localModelMatrix, 0, scaleMatrix, 0);
            System.arraycopy(tempMatrix, 0, localModelMatrix, 0, 16);

            // MVP
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, localModelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

            virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            // Renderizar todas las mallas del modelo (soporte multi-material)
            for (Mesh mesh : virtualObjectMeshes) {
                Texture embeddedTexture = mesh.getModelTexture();
                virtualObjectShader.setTexture("u_AlbedoTexture",
                        embeddedTexture != null ? embeddedTexture : fallbackWhiteTexture);
                render.draw(mesh, virtualObjectShader, virtualSceneFramebuffer);
            }
        }

        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  handleTap
    // ─────────────────────────────────────────────────────────────────────────
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) return;

        List<HitResult> hitResultList = instantPlacementSettings.isInstantPlacementEnabled()
                ? frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS)
                : frame.hitTest(tap);

        for (HitResult hit : hitResultList) {
            float[] hitPose = new float[3];
            hit.getHitPose().getTranslation(hitPose, 0);

            // ¿Tocó un modelo ya colocado? → mostrar slideshow
            for (wrapped_Anchor wrappedAnchor : wrappedAnchors) {
                float[] anchorPose = new float[3];
                wrappedAnchor.anchor().getPose().getTranslation(anchorPose, 0);
                double distance = Math.sqrt(
                        Math.pow(hitPose[0] - anchorPose[0], 2) +
                                Math.pow(hitPose[1] - anchorPose[1], 2) +
                                Math.pow(hitPose[2] - anchorPose[2], 2));
                if (distance < 0.2) {
                    runOnUiThread(() -> {
                        currentSlideIndex = 0;
                        updateSlideUI();
                        slideshowOverlay.setVisibility(View.VISIBLE);
                    });
                    return;
                }
            }

            // Colocar nuevo anchor en superficie detectada
            Trackable trackable = hit.getTrackable();
            if ((trackable instanceof Plane
                    && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                    && PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0)
                    || (trackable instanceof Point
                    && ((Point) trackable).getOrientationMode()
                    == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                    || (trackable instanceof InstantPlacementPoint)
                    || (trackable instanceof DepthPoint)) {

                if (wrappedAnchors.size() >= 20) {
                    wrappedAnchors.get(0).anchor().detach();
                    wrappedAnchors.remove(0);
                }
                wrappedAnchors.add(new wrapped_Anchor(hit.createAnchor(), trackable));
                runOnUiThread(this::showOcclusionDialogIfNeeded);
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Slideshow
    // ─────────────────────────────────────────────────────────────────────────
    private void updateSlideUI() {
        slideTitle.setText("Página " + (currentSlideIndex + 1));
        slideContent.setText(slidePages[currentSlideIndex]);
        btnPrev.setEnabled(currentSlideIndex > 0);
        btnNext.setEnabled(currentSlideIndex < slidePages.length - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dialogs / settings helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void showOcclusionDialogIfNeeded() {
        boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_with_depth)
                .setMessage(R.string.depth_use_explanation)
                .setPositiveButton(R.string.button_text_enable_depth,
                        (dialog, which) -> depthSettings.setUseDepthForOcclusion(true))
                .setNegativeButton(R.string.button_text_disable_depth,
                        (dialog, which) -> depthSettings.setUseDepthForOcclusion(false))
                .show();
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        getResources().getStringArray(R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (dialog, which, isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(R.string.done,
                        (dialog, which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }

    private void launchDepthSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            getResources().getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (dialog, which, isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(R.string.done,
                            (dialog, which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(R.string.done,
                            (dialog, which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Light estimation helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true);
        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);
        updateMainLight(
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(),
                viewMatrix);
        updateSphericalHarmonicsCoefficients(
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }

    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException("Coefficients array must be of length 27");
        }
        for (int i = 0; i < 9 * 3; ++i) {
            sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
        }
        virtualObjectShader.setVec3Array("u_SphericalHarmonicsCoefficients",
                sphericalHarmonicsCoefficients);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Session helpers
    // ─────────────────────────────────────────────────────────────────────────
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) return true;
        }
        return false;
    }

    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        config.setDepthMode(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                ? Config.DepthMode.AUTOMATIC : Config.DepthMode.DISABLED);
        config.setInstantPlacementMode(instantPlacementSettings.isInstantPlacementEnabled()
                ? Config.InstantPlacementMode.LOCAL_Y_UP : Config.InstantPlacementMode.DISABLED);
        session.configure(config);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  wrapped_Anchor (record-style helper)
// ─────────────────────────────────────────────────────────────────────────────
class wrapped_Anchor {
    private final Anchor anchor;
    private final Trackable trackable;

    public wrapped_Anchor(Anchor anchor, Trackable trackable) {
        this.anchor    = anchor;
        this.trackable = trackable;
    }

    public Anchor anchor()       { return anchor; }
    public Trackable trackable() { return trackable; }
}