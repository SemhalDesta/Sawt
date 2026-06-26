package sosina.terefe.adu.ac.ae.sawt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CalibrationFragment extends Fragment {

    public static final String PREFS_NAME = "sawt_blink_prefs";
    public static final String KEY_MIN_BLINK = "calibrated_min_blink_ms";
    public static final String KEY_MAX_BLINK = "calibrated_max_blink_ms";
    public static final String KEY_OPEN_EAR = "calibrated_open_ear";
    public static final String KEY_CLOSED_EAR = "calibrated_closed_ear";

    // Fallback defaults if calibration finds too few samples
    private static final int DEFAULT_MIN_MS = 60;
    private static final int DEFAULT_MAX_MS = 500;
    private static final float DEFAULT_OPEN_EAR = 0.30f;
    private static final float DEFAULT_CLOSED_EAR = 0.10f;

    private enum Phase { BASELINE, BLINKING }
    private Phase currentPhase = Phase.BASELINE;

    private PreviewView cameraPreview;
    private TextView tv_instruction, tv_count, tv_countdown, tv_result;
    private ProgressBar progressBar;
    private Button btn_done;

    private FaceLandmarker faceLandmarker;

    // Raw measurements collected during session
    private final List<Long> blinkDurations = new ArrayList<>();
    private final List<Float> openEarSamples = new ArrayList<>();
    private final List<Float> closedEarMins = new ArrayList<>();

    private boolean eyesCurrentlyClosed = false;
    private long eyeCloseStartTime = 0;
    private float minEarDuringCurrentClosure = Float.MAX_VALUE;

    // Fixed detection thresholds for calibration (same as current app)
    private static final float CLOSE_THRESHOLD = 0.55f;
    private static final float OPEN_THRESHOLD  = 0.25f;

    private CountDownTimer countDownTimer;
    private boolean calibrationRunning = false;
    private boolean calibrationDone    = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_calibration, container, false);

        cameraPreview  = v.findViewById(R.id.calib_camera_preview);
        tv_instruction = v.findViewById(R.id.tv_calib_instruction);
        tv_count       = v.findViewById(R.id.tv_calib_count);
        tv_countdown   = v.findViewById(R.id.tv_calib_countdown);
        tv_result      = v.findViewById(R.id.tv_calib_result);
        progressBar    = v.findViewById(R.id.calib_progress);
        btn_done       = v.findViewById(R.id.btn_calib_done);

        btn_done.setVisibility(View.GONE);
        tv_result.setVisibility(View.GONE);

        setupFaceLandmarker();
        startCamera();
        startBaselinePhase();

        btn_done.setOnClickListener(view -> goHome());

        return v;
    }

    private void setupFaceLandmarker() {
        FaceLandmarker.FaceLandmarkerOptions options =
                FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(
                                com.google.mediapipe.tasks.core.BaseOptions.builder()
                                        .setModelAssetPath("face_landmarker.task")
                                        .build()
                        )
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumFaces(1)
                        .setOutputFaceBlendshapes(true)
                        .setResultListener(this::onResult)
                        .setErrorListener(e ->
                                android.util.Log.e("SAWT_CALIB", e.getMessage()))
                        .build();
        faceLandmarker = FaceLandmarker.createFromOptions(requireContext(), options);
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()),
                        this::analyzeFrame);
                provider.unbindAll();
                provider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (ExecutionException | InterruptedException e) {
                android.util.Log.e("SAWT_CALIB", "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy proxy) {
        if (proxy.getImage() == null) { proxy.close(); return; }
        android.graphics.Bitmap bmp = proxy.toBitmap();
        MPImage mpImage = new BitmapImageBuilder(bmp).build();
        faceLandmarker.detectAsync(mpImage, System.currentTimeMillis());
        proxy.close();
    }

    private void onResult(FaceLandmarkerResult result, MPImage image) {
        if (!calibrationRunning || result.faceLandmarks().isEmpty()) return;
        if (!result.faceBlendshapes().isPresent()) return;

        List<com.google.mediapipe.tasks.components.containers.Category> shapes =
                result.faceBlendshapes().get().get(0);

        float left = 0f, right = 0f;
        for (var s : shapes) {
            if (s.categoryName().equals("eyeBlinkLeft"))  left  = s.score();
            if (s.categoryName().equals("eyeBlinkRight")) right = s.score();
        }

        float ear = EyeMetrics.averageEAR(result.faceLandmarks().get(0));

        float fLeft = left, fRight = right, fEar = ear;
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (currentPhase == Phase.BASELINE) {
                    processBaselineFrame(fEar);
                } else {
                    processBlinkFrame(fLeft, fRight, fEar);
                }
            });
        }
    }

    /** Phase 1: patient keeps eyes open, we just sample EAR to learn their personal "open" value. */
    private void processBaselineFrame(float ear) {
        if (ear > 0f) {
            openEarSamples.add(ear);
        }
    }

    /** Phase 2: existing blendshape-based blink detection, now also tracking the EAR low-point per blink. */
    private void processBlinkFrame(float left, float right, float ear) {
        long now = System.currentTimeMillis();
        boolean closed = left > CLOSE_THRESHOLD && right > CLOSE_THRESHOLD;
        boolean open   = left < OPEN_THRESHOLD  && right < OPEN_THRESHOLD;

        if (closed && !eyesCurrentlyClosed) {
            eyesCurrentlyClosed = true;
            eyeCloseStartTime = now;
            minEarDuringCurrentClosure = Float.MAX_VALUE;
        }

        if (eyesCurrentlyClosed && ear < minEarDuringCurrentClosure) {
            minEarDuringCurrentClosure = ear;
        }

        if (eyesCurrentlyClosed && open) {
            long duration = now - eyeCloseStartTime;
            eyesCurrentlyClosed = false;
            // Accept anything between 30ms and 800ms as a potential blink
            // (wider window than normal operation — we're measuring, not filtering)
            if (duration >= 30 && duration <= 800) {
                blinkDurations.add(duration);
                if (minEarDuringCurrentClosure < Float.MAX_VALUE) {
                    closedEarMins.add(minEarDuringCurrentClosure);
                }
                tv_count.setText("Blinks detected: " + blinkDurations.size());
            }
        }
    }

    private void startBaselinePhase() {
        currentPhase = Phase.BASELINE;
        calibrationRunning = true;
        tv_instruction.setText("Look at the camera and keep your eyes OPEN.\nHold still for 3 seconds.");
        progressBar.setMax(3000);

        countDownTimer = new CountDownTimer(3000, 100) {
            @Override public void onTick(long ms) {
                progressBar.setProgress((int) (3000 - ms));
                tv_countdown.setText((ms / 1000 + 1) + "s");
            }
            @Override public void onFinish() {
                startBlinkPhase();
            }
        }.start();
    }

    private void startBlinkPhase() {
        currentPhase = Phase.BLINKING;
        tv_instruction.setText("Now blink naturally for 20 seconds.\nBlink as you normally would.");
        progressBar.setMax(20000);
        progressBar.setProgress(0);

        countDownTimer = new CountDownTimer(20000, 100) {
            @Override public void onTick(long ms) {
                progressBar.setProgress((int) (20000 - ms));
                tv_countdown.setText((ms / 1000 + 1) + "s");
            }
            @Override public void onFinish() {
                calibrationRunning = false;
                calibrationDone = true;
                progressBar.setProgress(20000);
                tv_countdown.setText("Done!");
                finishCalibration();
            }
        }.start();
    }

    private void finishCalibration() {
        int minMs, maxMs;
        float openEAR, closedEAR;
        StringBuilder result = new StringBuilder();

        if (blinkDurations.size() < 3) {
            minMs = DEFAULT_MIN_MS;
            maxMs = DEFAULT_MAX_MS;
            result.append("⚠ Not enough blinks detected (" + blinkDurations.size()
                    + ").\nUsing default timing.\n");
        } else {
            Collections.sort(blinkDurations);
            int trimCount = Math.max(0, (int) (blinkDurations.size() * 0.1));
            List<Long> trimmed = blinkDurations.subList(0, blinkDurations.size() - trimCount);

            long shortest = trimmed.get(0);
            long longest  = trimmed.get(trimmed.size() - 1);

            minMs = (int) Math.max(30,  shortest - 20);
            maxMs = (int) Math.min(700, longest  + 20);

            result.append("✓ Blink timing calibrated: " + minMs + "ms – " + maxMs + "ms\n");
        }

        if (openEarSamples.size() < 10) {
            openEAR = DEFAULT_OPEN_EAR;
            result.append("⚠ Not enough open-eye samples — using default eye-shape baseline.\n");
        } else {
            float sum = 0f;
            for (float s : openEarSamples) sum += s;
            openEAR = sum / openEarSamples.size();
        }

        if (closedEarMins.size() < 3) {
            closedEAR = DEFAULT_CLOSED_EAR;
            result.append("⚠ Not enough closed-eye samples — using default eye-shape baseline.\n");
        } else {
            float sum = 0f;
            for (float s : closedEarMins) sum += s;
            closedEAR = sum / closedEarMins.size();
        }

        boolean fullyCalibrated = blinkDurations.size() >= 3
                && openEarSamples.size() >= 10
                && closedEarMins.size() >= 3;

        if (fullyCalibrated) {
            result.append("✓ Eye-shape calibrated for your face.\n");
            tv_result.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            result.append("Try again in better lighting for full accuracy.");
            tv_result.setTextColor(Color.parseColor("#FFA500"));
        }

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_MIN_BLINK, minMs)
                .putInt(KEY_MAX_BLINK, maxMs)
                .putFloat(KEY_OPEN_EAR, openEAR)
                .putFloat(KEY_CLOSED_EAR, closedEAR)
                .apply();

        tv_result.setText(result.toString());
        tv_instruction.setText("Calibration complete.");
        tv_result.setVisibility(View.VISIBLE);
        btn_done.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        if (!isAdded()) return;
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new PatientHomeFragment())
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) countDownTimer.cancel();
        if (faceLandmarker != null) faceLandmarker.close();
    }
}