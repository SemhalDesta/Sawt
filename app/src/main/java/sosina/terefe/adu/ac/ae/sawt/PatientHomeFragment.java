package sosina.terefe.adu.ac.ae.sawt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class PatientHomeFragment extends Fragment {

    private TextView tv_patient_name, tv_greeting;
    private View dot_1, dot_2;
    private PreviewView cameraPreview;
    private FaceLandmarker faceLandmarker;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private boolean eyesCurrentlyClosed = false;
    private long eyeCloseStartTime = 0;
    private int blinkCount = 0;
    private long firstBlinkTime = 0;

    private static final float BLINK_CLOSE_THRESHOLD = 0.55f;
    private static final float BLINK_OPEN_THRESHOLD = 0.25f;
    private float EAR_CLOSE_THRESHOLD = 0.15f;   // ← add, overwritten by calibration below
    private float EAR_OPEN_THRESHOLD  = 0.21f;
    //private static final int MIN_BLINK_DURATION_MS = 60;
    //private static final int MAX_BLINK_DURATION_MS = 500;

    private int MIN_BLINK_DURATION_MS = 60;
    private int MAX_BLINK_DURATION_MS = 500;
    private static final int DOUBLE_BLINK_WINDOW_MS = 1500;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_patient_home, container, false);

        tv_patient_name = v.findViewById(R.id.tv_patient_name);
        tv_greeting = v.findViewById(R.id.tv_greeting);
        dot_1 = v.findViewById(R.id.dot_1);
        dot_2 = v.findViewById(R.id.dot_2);
        cameraPreview = v.findViewById(R.id.camera_preview);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadPatientName();
        setGreeting();
        setupFaceLandmarker();
        startCamera();

        BlinkConfig config = BlinkConfig.load(requireContext());
        MIN_BLINK_DURATION_MS = config.minBlinkMs;
        MAX_BLINK_DURATION_MS = config.maxBlinkMs;
        EAR_CLOSE_THRESHOLD = config.earCloseThreshold;   // ← add
        EAR_OPEN_THRESHOLD  = config.earOpenThreshold;

        tv_patient_name.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new PatientSettingsFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            }
        });

        return v;
    }

    private void loadPatientName() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot document) {
                        if (document.exists() && isAdded()) {
                            String name = document.getString("name");
                            if (name != null) tv_patient_name.setText(name);
                        }
                    }
                });
    }

    private void setGreeting() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Good morning,";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good afternoon,";
        } else {
            greeting = "Good evening,";
        }
        tv_greeting.setText(greeting);
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
                        android.util.Log.e("SAWT", "Home error: " + e.getMessage())
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
        long now = System.currentTimeMillis();

        boolean bothEyesClosed = (leftBlink > BLINK_CLOSE_THRESHOLD && rightBlink > BLINK_CLOSE_THRESHOLD)
                && ear < EAR_CLOSE_THRESHOLD;
        boolean bothEyesOpen   = (leftBlink < BLINK_OPEN_THRESHOLD  && rightBlink < BLINK_OPEN_THRESHOLD)
                || ear > EAR_OPEN_THRESHOLD;

        if (bothEyesClosed && !eyesCurrentlyClosed) {
            eyesCurrentlyClosed = true;
            eyeCloseStartTime = now;
        }

        if (eyesCurrentlyClosed && bothEyesOpen) {
            long closedDuration = now - eyeCloseStartTime;
            eyesCurrentlyClosed = false;

            if (closedDuration >= MIN_BLINK_DURATION_MS && closedDuration <= MAX_BLINK_DURATION_MS) {

                if (blinkCount == 0) {
                    blinkCount = 1;
                    firstBlinkTime = now;
                    animateDot(1);
                } else if (blinkCount == 1 && (now - firstBlinkTime) <= DOUBLE_BLINK_WINDOW_MS) {
                    blinkCount = 0;
                    animateDot(0);
                    enterCommunicationBoard();
                } else {
                    blinkCount = 1;
                    firstBlinkTime = now;
                    animateDot(1);
                }
            }
        }


        if (blinkCount == 1 && (now - firstBlinkTime) > DOUBLE_BLINK_WINDOW_MS) {
            blinkCount = 0;
            animateDot(0);
        }
    }

    private void animateDot(int activeDots) {
        if (!isAdded()) return;
        dot_1.setAlpha(activeDots >= 1 ? 1.0f : 0.2f);
        dot_2.setAlpha(activeDots >= 2 ? 1.0f : 0.2f);
    }

    private void enterCommunicationBoard() {
        if (!isAdded()) return;
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CommunicationFragment())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (faceLandmarker != null) faceLandmarker.close();
    }
}