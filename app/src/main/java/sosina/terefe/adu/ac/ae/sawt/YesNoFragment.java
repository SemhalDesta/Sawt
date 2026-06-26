package sosina.terefe.adu.ac.ae.sawt;

import android.os.Bundle;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Locale;

public class YesNoFragment extends Fragment {

    private LinearLayout zone_yes, zone_no;
    private PreviewView cameraPreview;
    private FaceLandmarker faceLandmarker;
    private int currentZone = 0;
    private CountDownTimer dwellTimer;
    private boolean isSelecting = false;
    private boolean isBlinking = false;
    private long lastBlinkTime = 0;
    private int blinkCount = 0;
    private long firstBlinkTime = 0;
    private TextToSpeech tts;
    private static final float BLINK_CLOSE_THRESHOLD = 0.55f;
    private static final float BLINK_OPEN_THRESHOLD = 0.25f;
    private float EAR_CLOSE_THRESHOLD = 0.15f;   // ← add, overwritten by calibration below
    private float EAR_OPEN_THRESHOLD  = 0.21f;
    private int MIN_BLINK_DURATION_MS = 60;
    private int MAX_BLINK_DURATION_MS = 500;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_yes_no, container, false);

        zone_yes = view.findViewById(R.id.zone_yes);
        zone_no = view.findViewById(R.id.zone_no);
        cameraPreview = view.findViewById(R.id.camera_preview);

        tts = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);
                }
            }
        });

        setupFaceLandmarker();
        startCamera();

        // Start on Yes by default and begin dwell timer
        currentZone = 0;
        highlightZone(currentZone);
        startDwellTimer();

        BlinkConfig config = BlinkConfig.load(requireContext());
        MIN_BLINK_DURATION_MS = config.minBlinkMs;
        MAX_BLINK_DURATION_MS = config.maxBlinkMs;
        EAR_CLOSE_THRESHOLD = config.earCloseThreshold;   // ← add
        EAR_OPEN_THRESHOLD  = config.earOpenThreshold;

        return view;
    }

    private void setupFaceLandmarker() {
        FaceLandmarkerOptions options = FaceLandmarkerOptions.builder()
                .setBaseOptions(
                        com.google.mediapipe.tasks.core.BaseOptions.builder()
                                .setModelAssetPath("face_landmarker.task")
                                .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setResultListener(this::onFaceLandmarkerResult)
                .setErrorListener(e ->
                        android.util.Log.e("SAWT", "FaceLandmarker error: " + e.getMessage())
                )
                .build();

        faceLandmarker = FaceLandmarker.createFromOptions(requireContext(), options);
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();

                    imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(requireContext()),
                            new ImageAnalysis.Analyzer() {
                                @Override
                                public void analyze(ImageProxy imageProxy) {
                                    analyzeFrame(imageProxy);
                                }
                            }
                    );

                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(
                            getViewLifecycleOwner(),
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis
                    );

                } catch (ExecutionException | InterruptedException e) {
                    android.util.Log.e("SAWT", "Camera error: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        android.graphics.Bitmap bitmap = imageProxy.toBitmap();
        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        long frameTime = System.currentTimeMillis();
        faceLandmarker.detectAsync(mpImage, frameTime);
        imageProxy.close();
    }

    private void onFaceLandmarkerResult(FaceLandmarkerResult result, MPImage image) {
        if (result.faceLandmarks().isEmpty()) return;

        if (result.faceBlendshapes().isPresent() && !result.faceBlendshapes().get().isEmpty()) {
            List<com.google.mediapipe.tasks.components.containers.Category> blendshapes =
                    result.faceBlendshapes().get().get(0);

            float leftBlink = 0f;
            float rightBlink = 0f;

            for (com.google.mediapipe.tasks.components.containers.Category shape : blendshapes) {
                if (shape.categoryName().equals("eyeBlinkLeft")) leftBlink = shape.score();
                if (shape.categoryName().equals("eyeBlinkRight")) rightBlink = shape.score();
            }

            float finalLeft = leftBlink;
            float finalRight = rightBlink;
            float finalEar = EyeMetrics.averageEAR(result.faceLandmarks().get(0));

            if (isAdded()) {
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkBlink(finalLeft, finalRight, finalEar);
                    }
                });
            }
        }
    }

    private void checkBlink(float leftBlink, float rightBlink, float ear) {
        if (isSelecting) return;

        long now = System.currentTimeMillis();

        if (leftBlink > BLINK_CLOSE_THRESHOLD && rightBlink > BLINK_CLOSE_THRESHOLD
                && ear < EAR_CLOSE_THRESHOLD && !isBlinking) {
            isBlinking = true;
            lastBlinkTime = now;
        }

        if (isBlinking && ((leftBlink < BLINK_OPEN_THRESHOLD && rightBlink < BLINK_OPEN_THRESHOLD)
                || ear > EAR_OPEN_THRESHOLD)) {
            isBlinking = false;
            long blinkDuration = now - lastBlinkTime;

            // reject blinks that are too short (noise) OR too long (resting/looking away)
            if (blinkDuration >= MIN_BLINK_DURATION_MS && blinkDuration <= MAX_BLINK_DURATION_MS) {
                if (blinkCount == 0) {
                    blinkCount = 1;
                    firstBlinkTime = now;
                } else if (blinkCount == 1 && now - firstBlinkTime < 500) {
                    blinkCount = 0;
                    goBack();
                    return;
                } else {
                    blinkCount = 1;
                    firstBlinkTime = now;
                }

                if (blinkCount == 1) {
                    advanceZone();
                }
            }
        }

        if (blinkCount == 1 && now - firstBlinkTime > 500) {
            blinkCount = 0;
        }
    }
    private void advanceZone() {
        currentZone = (currentZone + 1) % 2;
        highlightZone(currentZone);
        startDwellTimer();
    }

    private void startDwellTimer() {
        if (dwellTimer != null) dwellTimer.cancel();

        dwellTimer = new CountDownTimer(3000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!isSelecting) {
                    isSelecting = true;
                    String answer = currentZone == 0 ? "YES" : "NO";
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null);
                    // Reset to Yes and restart timer for next question
                    currentZone = 0;
                    isSelecting = false;
                    highlightZone(currentZone);
                    startDwellTimer();
                }
            }
        }.start();
    }

    private void highlightZone(int index) {
        zone_yes.setBackgroundColor(android.graphics.Color.parseColor("#1A2A1A"));
        zone_no.setBackgroundColor(android.graphics.Color.parseColor("#2A1A1A"));

        if (index == 0) {
            zone_yes.setBackgroundResource(R.drawable.zone_active_bg);
        } else {
            zone_no.setBackgroundResource(R.drawable.zone_active_bg);
        }
    }

    private void goBack() {
        if (dwellTimer != null) dwellTimer.cancel();
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (dwellTimer != null) dwellTimer.cancel();
        if (faceLandmarker != null) faceLandmarker.close();
    }
}