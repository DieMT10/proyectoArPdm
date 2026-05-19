package com.example.proyectoarpdm.helloar;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.ArCoreApk.Availability;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.example.proyectoarpdm.common.helpers.CameraPermissionHelper;
import com.example.proyectoarpdm.common.helpers.DepthSettings;
import com.example.proyectoarpdm.common.helpers.DisplayRotationHelper;
import com.example.proyectoarpdm.common.helpers.FullScreenHelper;
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
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

  private static final String TAG = HelloArActivity.class.getSimpleName();
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

  private Mesh virtualObjectMesh;
  private Shader virtualObjectShader;
  private Texture fallbackWhiteTexture;
  // ── ELIMINADO: virtualObjectAlbedoTexture y virtualObjectAlbedoInstantPlacementTexture
  //    El .glb trae sus texturas embebidas; se acceden con virtualObjectMesh.getModelTexture()

  private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

  private Texture dfgTexture;
  private SpecularCubemapFilter cubemapFilter;

  // Slideshow UI
  private View slideshowOverlay;
  private TextView slideTitle;
  private TextView slideContent;
  private ImageView slideImage;
  private Button btnPrev;
  private Button btnNext;
  private Button btnClose;
  private int currentSlideIndex = 0;
  private PdfRenderer pdfRenderer;
  private PdfRenderer.Page currentPage;
  private ParcelFileDescriptor parcelFileDescriptor;
  private int totalPages = 0;

  private float scaleFactor = 0.1f;
  private float rotationAngle = 0f;
  private static final float SCALE_STEP = 0.05f;
  private static final float ROTATION_STEP = 15f;

  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];
  private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
  private final float[] viewInverseMatrix = new float[16];
  private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
  private final float[] viewLightDirection = new float[4];
  private float offsetX = 0f;
  private float offsetY = 0f;
  private float offsetZ = 0f;
  private static final float TRANSLATION_STEP = 0.05f; // metros

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(com.example.proyectoarpdm.R.layout.activity_main);
    surfaceView = findViewById(com.example.proyectoarpdm.R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(this);
    tapHelper = new TapHelper(this);
    surfaceView.setOnTouchListener(tapHelper);
    render = new SampleRender(surfaceView, this, getAssets());
    installRequested = false;
    depthSettings.onCreate(this);
    instantPlacementSettings.onCreate(this);
    // Activar instant placement por defecto
    instantPlacementSettings.setInstantPlacementEnabled(true);

    slideshowOverlay = findViewById(com.example.proyectoarpdm.R.id.slideshow_overlay);
    slideTitle       = findViewById(com.example.proyectoarpdm.R.id.slide_title);
    slideContent     = findViewById(com.example.proyectoarpdm.R.id.slide_content);
    slideImage       = findViewById(com.example.proyectoarpdm.R.id.slide_image);
    btnPrev          = findViewById(com.example.proyectoarpdm.R.id.btn_prev);
    btnNext          = findViewById(com.example.proyectoarpdm.R.id.btn_next);
    btnClose         = findViewById(com.example.proyectoarpdm.R.id.btn_close);

    ImageButton btnMoveXPos = findViewById(com.example.proyectoarpdm.R.id.btn_move_x_pos);
    ImageButton btnMoveXNeg = findViewById(com.example.proyectoarpdm.R.id.btn_move_x_neg);
    ImageButton btnMoveYPos = findViewById(com.example.proyectoarpdm.R.id.btn_move_y_pos);
    ImageButton btnMoveYNeg = findViewById(com.example.proyectoarpdm.R.id.btn_move_y_neg);
    ImageButton btnMoveZPos = findViewById(com.example.proyectoarpdm.R.id.btn_move_z_pos);
    ImageButton btnMoveZNeg = findViewById(com.example.proyectoarpdm.R.id.btn_move_z_neg);

    btnMoveXPos.setOnClickListener(v -> offsetX += TRANSLATION_STEP);
    btnMoveXNeg.setOnClickListener(v -> offsetX -= TRANSLATION_STEP);
    btnMoveYPos.setOnClickListener(v -> offsetY += TRANSLATION_STEP);
    btnMoveYNeg.setOnClickListener(v -> offsetY -= TRANSLATION_STEP);
    btnMoveZPos.setOnClickListener(v -> offsetZ += TRANSLATION_STEP);
    btnMoveZNeg.setOnClickListener(v -> offsetZ -= TRANSLATION_STEP);

    initPdfRenderer();

    btnPrev.setOnClickListener(v -> {
      if (currentSlideIndex > 0) { currentSlideIndex--; updateSlideUI(); }
    });
    btnNext.setOnClickListener(v -> {
      if (currentSlideIndex < totalPages - 1) { currentSlideIndex++; updateSlideUI(); }
    });
    btnClose.setOnClickListener(v -> slideshowOverlay.setVisibility(View.GONE));

    ImageButton btnScaleUp = findViewById(com.example.proyectoarpdm.R.id.btn_scale_up);
    ImageButton btnScaleDown = findViewById(com.example.proyectoarpdm.R.id.btn_scale_down);
    ImageButton btnRotateLeft = findViewById(com.example.proyectoarpdm.R.id.btn_rotate_left);
    ImageButton btnRotateRight = findViewById(com.example.proyectoarpdm.R.id.btn_rotate_right);

    btnScaleUp.setOnClickListener(v -> scaleFactor = Math.min(scaleFactor + SCALE_STEP, 2.0f));
    btnScaleDown.setOnClickListener(v -> scaleFactor = Math.max(scaleFactor - SCALE_STEP, 0.05f));
    btnRotateLeft.setOnClickListener(v -> rotationAngle = (rotationAngle - ROTATION_STEP) % 360f);
    btnRotateRight.setOnClickListener(v -> rotationAngle = (rotationAngle + ROTATION_STEP) % 360f);

    ImageButton settingsButton = findViewById(com.example.proyectoarpdm.R.id.settings_button);
    settingsButton.setOnClickListener(v -> {
      PopupMenu popup = new PopupMenu(HelloArActivity.this, v);
      popup.setOnMenuItemClickListener(HelloArActivity.this::settingsMenuClick);
      popup.inflate(com.example.proyectoarpdm.R.menu.settings_menu);
      popup.show();
    });
  }

  protected boolean settingsMenuClick(MenuItem item) {
    if (item.getItemId() == com.example.proyectoarpdm.R.id.depth_settings) {
      launchDepthSettingsMenuDialog(); return true;
    } else if (item.getItemId() == com.example.proyectoarpdm.R.id.instant_placement_settings) {
      launchInstantPlacementSettingsMenuDialog(); return true;
    }
    return false;
  }

  @Override
  protected void onDestroy() {
    if (session != null) { session.close(); session = null; }
    closePdfRenderer();
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability != Availability.SUPPORTED_INSTALLED) {
          switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
            case INSTALL_REQUESTED: installRequested = true; return;
            case INSTALLED: break;
          }
        }
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this); return;
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
      session = null; return;
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

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    try {
      planeRenderer           = new PlaneRenderer(render);
      backgroundRenderer      = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, 1, 1);

      cubemapFilter = new SpecularCubemapFilter(render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

      // Textura DFG — parte del pipeline PBR, no es del modelo
      dfgTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);
      final int dfgResolution = 64;
      final int dfgChannels   = 2;
      final int halfFloatSize = 2;
      ByteBuffer buffer = ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
      try (InputStream is = getAssets().open("models/dfg.raw")) {
        is.read(buffer.array());
      }
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
      GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
      GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F,
              dfgResolution, dfgResolution, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buffer);
      GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

      // Point cloud
      pointCloudShader = Shader.createFromAssets(render,
                      "shaders/point_cloud.vert", "shaders/point_cloud.frag", null)
              .setVec4("u_Color", new float[]{31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
              .setFloat("u_PointSize", 5.0f);
      pointCloudVertexBuffer = new VertexBuffer(render, 4, null);
      pointCloudMesh = new Mesh(render, Mesh.PrimitiveMode.POINTS, null,
              new VertexBuffer[]{pointCloudVertexBuffer});

      // Textura blanca 1x1 como fallback
      android.graphics.Bitmap whiteBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
      whiteBitmap.setPixel(0, 0, android.graphics.Color.WHITE);
      fallbackWhiteTexture = Texture.createFromBitmap(render, whiteBitmap,
              Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);

      // ── CARGA DE MODELO Y SHADER DE PRUEBA ─────────────────────────────
      virtualObjectMesh = Mesh.createFromAsset(render, "react_logo.glb");
      virtualObjectMesh.debugPrintInfo();
      Log.d(TAG, "Embedded texture: " + (virtualObjectMesh.getModelTexture() != null ? "OK" : "NULL"));

      // Usar shader simple (rojo) para diagnosticar si la geometría carga
      virtualObjectShader = Shader.createFromAssets(render,
                      "shaders/point_cloud.vert",
                      "shaders/point_cloud.frag",
                      null)
              .setVec4("u_Color", new float[]{1.0f, 0.0f, 0.0f, 1.0f}) // Rojo
              .setFloat("u_PointSize", 10.0f);
      // ────────────────────────────────────────────────────────────────────

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

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null) return;

    if (!hasSetTextureNames) {
      session.setCameraTextureNames(new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
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
      backgroundRenderer.setUseDepthVisualization(render, depthSettings.depthColorVisualizationEnabled());
      backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
      return;
    }
    backgroundRenderer.updateDisplayGeometry(frame);

    if (camera.getTrackingState() == TrackingState.TRACKING
            && (depthSettings.useDepthForOcclusion() || depthSettings.depthColorVisualizationEnabled())) {
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

    // Default PBR uniforms in case light estimation is not valid yet
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

    for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
      Anchor anchor = wrappedAnchor.getAnchor();
      if (anchor.getTrackingState() != TrackingState.TRACKING) continue;

      anchor.getPose().toMatrix(modelMatrix, 0);

// Traslación XYZ manual
      float[] translationMatrix = new float[16];
      Matrix.setIdentityM(translationMatrix, 0);
      translationMatrix[12] = offsetX;
      translationMatrix[13] = offsetY;
      translationMatrix[14] = offsetZ;

// Rotación
      float[] rotationMatrix = new float[16];
      Matrix.setRotateM(rotationMatrix, 0, rotationAngle, 0f, 1f, 0f);

// Escala
      float[] scaleMatrix = new float[16];
      Matrix.setIdentityM(scaleMatrix, 0);
      Matrix.scaleM(scaleMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

// Orden: pose × traslación × rotación × escala
      Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, translationMatrix, 0);
      Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, rotationMatrix, 0);
      Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);

      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

      if (virtualObjectMesh != null) {
        render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
      }
    }

    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
  }

  private void handleTap(Frame frame, Camera camera) {
    MotionEvent tap = tapHelper.poll();
    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      List<HitResult> hitResultList = instantPlacementSettings.isInstantPlacementEnabled()
              ? frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS)
              : frame.hitTest(tap);

      for (HitResult hit : hitResultList) {
        float[] hitPose = new float[3];
        hit.getHitPose().getTranslation(hitPose, 0);

        for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
          float[] anchorPose = new float[3];
          wrappedAnchor.getAnchor().getPose().getTranslation(anchorPose, 0);
          double distance = Math.sqrt(
                  Math.pow(hitPose[0] - anchorPose[0], 2) +
                          Math.pow(hitPose[1] - anchorPose[1], 2) +
                          Math.pow(hitPose[2] - anchorPose[2], 2));
          if (distance < 0.15) {
            runOnUiThread(() -> {
              currentSlideIndex = 0;
              updateSlideUI();
              slideshowOverlay.setVisibility(View.VISIBLE);
            });
            return;
          }
        }

        Trackable trackable = hit.getTrackable();
        if ((trackable instanceof Plane
                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                && PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0)
                || (trackable instanceof Point
                && ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL)
                || (trackable instanceof InstantPlacementPoint)
                || (trackable instanceof DepthPoint)) {
          if (wrappedAnchors.size() >= 20) {
            wrappedAnchors.get(0).getAnchor().detach();
            wrappedAnchors.remove(0);
          }
          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));
          runOnUiThread(this::showOcclusionDialogIfNeeded);
          break;
        }
      }
    }
  }

  private void updateSlideUI() {
    if (pdfRenderer == null) return;

    if (currentPage != null) {
      currentPage.close();
    }

    currentPage = pdfRenderer.openPage(currentSlideIndex);
    Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
    currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    slideImage.setImageBitmap(bitmap);

    slideTitle.setText("Diapositiva " + (currentSlideIndex + 1));
    slideContent.setText("Página " + (currentSlideIndex + 1) + " de " + totalPages);
    btnPrev.setEnabled(currentSlideIndex > 0);
    btnNext.setEnabled(currentSlideIndex < totalPages - 1);
  }

  private void initPdfRenderer() {
    try {
      File file = new File(getCacheDir(), "temp.pdf");
      if (!file.exists()) {
        try (InputStream is = getAssets().open("Untitled design.pdf");
             FileOutputStream os = new FileOutputStream(file)) {
          byte[] buffer = new byte[1024];
          int length;
          while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
          }
        }
      }
      parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
      if (parcelFileDescriptor != null) {
        pdfRenderer = new PdfRenderer(parcelFileDescriptor);
        totalPages = pdfRenderer.getPageCount();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error initializing PdfRenderer", e);
    }
  }

  private void closePdfRenderer() {
    try {
      if (currentPage != null) {
        currentPage.close();
      }
      if (pdfRenderer != null) {
        pdfRenderer.close();
      }
      if (parcelFileDescriptor != null) {
        parcelFileDescriptor.close();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error closing PdfRenderer", e);
    }
  }

  private void showOcclusionDialogIfNeeded() {
    boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
    if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) return;
    new AlertDialog.Builder(this)
            .setTitle(com.example.proyectoarpdm.R.string.options_title_with_depth)
            .setMessage(com.example.proyectoarpdm.R.string.depth_use_explanation)
            .setPositiveButton(com.example.proyectoarpdm.R.string.button_text_enable_depth,
                    (dialog, which) -> depthSettings.setUseDepthForOcclusion(true))
            .setNegativeButton(com.example.proyectoarpdm.R.string.button_text_disable_depth,
                    (dialog, which) -> depthSettings.setUseDepthForOcclusion(false))
            .show();
  }

  private void launchInstantPlacementSettingsMenuDialog() {
    resetSettingsMenuDialogCheckboxes();
    new AlertDialog.Builder(this)
            .setTitle(com.example.proyectoarpdm.R.string.options_title_instant_placement)
            .setMultiChoiceItems(
                    getResources().getStringArray(com.example.proyectoarpdm.R.array.instant_placement_options_array),
                    instantPlacementSettingsMenuDialogCheckboxes,
                    (dialog, which, isChecked) -> instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
            .setPositiveButton(com.example.proyectoarpdm.R.string.done,
                    (dialog, which) -> applySettingsMenuDialogCheckboxes())
            .setNegativeButton(android.R.string.cancel,
                    (dialog, which) -> resetSettingsMenuDialogCheckboxes())
            .show();
  }

  private void launchDepthSettingsMenuDialog() {
    resetSettingsMenuDialogCheckboxes();
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      new AlertDialog.Builder(this)
              .setTitle(com.example.proyectoarpdm.R.string.options_title_with_depth)
              .setMultiChoiceItems(
                      getResources().getStringArray(com.example.proyectoarpdm.R.array.depth_options_array),
                      depthSettingsMenuDialogCheckboxes,
                      (dialog, which, isChecked) -> depthSettingsMenuDialogCheckboxes[which] = isChecked)
              .setPositiveButton(com.example.proyectoarpdm.R.string.done,
                      (dialog, which) -> applySettingsMenuDialogCheckboxes())
              .setNegativeButton(android.R.string.cancel,
                      (dialog, which) -> resetSettingsMenuDialogCheckboxes())
              .show();
    } else {
      new AlertDialog.Builder(this)
              .setTitle(com.example.proyectoarpdm.R.string.options_title_without_depth)
              .setPositiveButton(com.example.proyectoarpdm.R.string.done,
                      (dialog, which) -> applySettingsMenuDialogCheckboxes())
              .show();
    }
  }

  private void applySettingsMenuDialogCheckboxes() {
    depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
    depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
    instantPlacementSettings.setInstantPlacementEnabled(instantPlacementSettingsMenuDialogCheckboxes[0]);
    configureSession();
  }

  private void resetSettingsMenuDialogCheckboxes() {
    depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
    depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
    instantPlacementSettingsMenuDialogCheckboxes[0] = instantPlacementSettings.isInstantPlacementEnabled();
  }

  private boolean hasTrackingPlane() {
    for (Plane plane : session.getAllTrackables(Plane.class)) {
      if (plane.getTrackingState() == TrackingState.TRACKING) return true;
    }
    return false;
  }

  private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
    if (lightEstimate.getState() != LightEstimate.State.VALID) {
      virtualObjectShader.setBool("u_LightEstimateIsValid", false);
      return;
    }
    virtualObjectShader.setBool("u_LightEstimateIsValid", true);
    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);
    updateMainLight(lightEstimate.getEnvironmentalHdrMainLightDirection(),
            lightEstimate.getEnvironmentalHdrMainLightIntensity(), viewMatrix);
    updateSphericalHarmonicsCoefficients(lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
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
    virtualObjectShader.setVec3Array("u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
  }

  private void configureSession() {
    Config config = session.getConfig();
    config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
    config.setDepthMode(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            ? Config.DepthMode.AUTOMATIC : Config.DepthMode.DISABLED);
    config.setInstantPlacementMode(instantPlacementSettings.isInstantPlacementEnabled()
            ? InstantPlacementMode.LOCAL_Y_UP : InstantPlacementMode.DISABLED);
    session.configure(config);
  }
}

class WrappedAnchor {
  private Anchor anchor;
  private Trackable trackable;

  public WrappedAnchor(Anchor anchor, Trackable trackable) {
    this.anchor    = anchor;
    this.trackable = trackable;
  }

  public Anchor getAnchor()       { return anchor; }
  public Trackable getTrackable() { return trackable; }
}