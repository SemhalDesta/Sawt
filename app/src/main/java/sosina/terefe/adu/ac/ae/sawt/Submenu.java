package sosina.terefe.adu.ac.ae.sawt;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Submenu extends Fragment {

    private String category;
    private String[] options;
    private int totalZones = 0;
    private TextView tv_category_title;
    private TextView tv_yes_no_toggle;
    private LinearLayout options_container;
    private ScrollView options_scroll;
    private PreviewView cameraPreview;

    private List<LinearLayout> zoneViews = new ArrayList<>();
    private LinearLayout backZoneView;

    private FaceLandmarker faceLandmarker;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private int currentZone = 0;
    private boolean isSelecting = false;
    private CountDownTimer dwellTimer;
    private boolean painLocationSelected = false;
    private String selectedPainLocation = "";
    private boolean eyesCurrentlyClosed = false;
    private long eyeCloseStartTime = 0;
    private static final float BLINK_CLOSE_THRESHOLD = 0.55f;
    private static final float BLINK_OPEN_THRESHOLD = 0.25f;
    private float EAR_CLOSE_THRESHOLD = 0.15f;   // ← add, overwritten by calibration below
    private float EAR_OPEN_THRESHOLD  = 0.21f;
    private int MIN_BLINK_DURATION_MS = 60;
    private int MAX_BLINK_DURATION_MS = 500;
    private static final int DWELL_DURATION_MS = 5000;

    public Submenu(String category) {
        this.category = category;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_submenu, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tv_category_title = v.findViewById(R.id.tv_category_title);
        tv_yes_no_toggle = v.findViewById(R.id.tv_yes_no_toggle);
        options_container = v.findViewById(R.id.options_container);
        options_scroll = v.findViewById(R.id.options_scroll);
        cameraPreview = v.findViewById(R.id.camera_preview);

        tv_category_title.setText(category);

        loadOptionsFromFirestore();

        tv_yes_no_toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dwellTimer != null) dwellTimer.cancel();
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new YesNoFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        BlinkConfig config = BlinkConfig.load(requireContext());   // ← add these 3 lines
        MIN_BLINK_DURATION_MS = config.minBlinkMs;
        MAX_BLINK_DURATION_MS = config.maxBlinkMs;
        EAR_CLOSE_THRESHOLD = config.earCloseThreshold;   // ← add
        EAR_OPEN_THRESHOLD  = config.earOpenThreshold;

        return v;
    }

    private void loadOptionsFromFirestore() {
        db.collection("menus")
                .whereEqualTo("name", category)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot.isEmpty()) {
                            Log.e("SAWT", "Category not found: " + category);
                            return;
                        }
                        String categoryId = querySnapshot.getDocuments().get(0).getId();
                        db.collection("menus").document(categoryId)
                                .collection("options")
                                .get()
                                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                    @Override
                                    public void onSuccess(QuerySnapshot optionsSnapshot) {
                                        ArrayList<String> optionList = new ArrayList<>();
                                        for (QueryDocumentSnapshot doc : optionsSnapshot) {
                                            String text = doc.getString("text");
                                            if (text != null) optionList.add(text);
                                        }
                                        options = new String[optionList.size()];
                                        for (int i = 0; i < optionList.size(); i++) {
                                            options[i] = optionList.get(i);
                                        }
                                        if (isAdded()) {
                                            requireActivity().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    buildZones();
                                                }
                                            });
                                        }
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e("SAWT", "Failed to load options: " + e.getMessage());
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("SAWT", "Failed to load category: " + e.getMessage());
                    }
                });
    }

    private void buildZones() {
        options_container.removeAllViews();
        zoneViews.clear();
        backZoneView = null;

        if (options == null || options.length == 0) return;

        int heightInPx = (int) (120 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < options.length; i++) {
            LinearLayout zone = buildZoneCard(options[i], heightInPx, marginPx, false);
            zoneViews.add(zone);
            options_container.addView(zone);
        }

        backZoneView = buildBackZone(heightInPx, marginPx);
        options_container.addView(backZoneView);

        totalZones = options.length + 1;

        currentZone = 0;
        resetBlinkState();
        highlightZone(0);
        setupFaceLandmarker();
        startCamera();
        startDwellTimer();
    }

    private void buildPainSeverityZones() {
        options = new String[]{"Mild", "Severe"};
        options_container.removeAllViews();
        zoneViews.clear();
        backZoneView = null;

        int heightInPx = (int) (120 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < options.length; i++) {
            LinearLayout zone = buildZoneCard(options[i], heightInPx, marginPx, false);
            zoneViews.add(zone);
            options_container.addView(zone);
        }

        backZoneView = buildBackZone(heightInPx, marginPx);
        options_container.addView(backZoneView);

        totalZones = options.length + 1;

        currentZone = 0;
        isSelecting = false;
        resetBlinkState();
        highlightZone(0);
        startDwellTimer();
    }

    private LinearLayout buildZoneCard(String text, int heightInPx, int marginPx, boolean isBack) {
        LinearLayout zone = new LinearLayout(getContext());
        zone.setOrientation(LinearLayout.VERTICAL);
        zone.setGravity(android.view.Gravity.CENTER);
        zone.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        zone.setClipToOutline(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightInPx);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        zone.setLayoutParams(params);

        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(isBack ? Color.parseColor("#888888") : Color.WHITE);
        tv.setTextSize(isBack ? 18 : 22);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        zone.addView(tv);

        return zone;
    }

    private LinearLayout buildBackZone(int heightInPx, int marginPx) {
        return buildZoneCard("Go Back to categories", heightInPx, marginPx, true);
    }

    private void highlightZone(int index) {
        for (int i = 0; i < zoneViews.size(); i++) {
            if (i == index) {
                zoneViews.get(i).setBackground(
                        getContext().getDrawable(R.drawable.zone_card_selected));
            } else {
                zoneViews.get(i).setBackground(
                        getContext().getDrawable(R.drawable.zone_card_normal));
            }
            zoneViews.get(i).setClipToOutline(true);
        }

        if (backZoneView != null) {
            if (index == totalZones - 1) {
                backZoneView.setBackground(
                        getContext().getDrawable(R.drawable.zone_card_selected));
            } else {
                backZoneView.setBackground(
                        getContext().getDrawable(R.drawable.zone_card_normal));
            }
            backZoneView.setClipToOutline(true);
        }

        if (options_scroll != null) {
            if (index < zoneViews.size()) {
                LinearLayout zone = zoneViews.get(index);
                options_scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        options_scroll.smoothScrollTo(0, zone.getTop());
                    }
                });
            } else if (backZoneView != null) {
                options_scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        options_scroll.smoothScrollTo(0, backZoneView.getTop());
                    }
                });
            }
        }
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

        if (!result.faceBlendshapes().isEmpty()) {
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
                advanceZone();
            }
        }
    }

    private void resetBlinkState() {
        eyesCurrentlyClosed = false;
        eyeCloseStartTime = 0;
    }

    private void advanceZone() {
        if (totalZones == 0) return;
        currentZone = (currentZone + 1) % totalZones;
        highlightZone(currentZone);
        startDwellTimer();
    }

    private void startDwellTimer() {
        if (dwellTimer != null) dwellTimer.cancel();

        dwellTimer = new CountDownTimer(DWELL_DURATION_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!isSelecting && isAdded()) {
                    isSelecting = true;
                    if (currentZone == totalZones - 1) {
                        goBack();
                    } else {
                        selectOption(currentZone);
                    }
                    isSelecting = false;
                }
            }
        }.start();
    }

    private void goBack() {
        if (dwellTimer != null) dwellTimer.cancel();
        getParentFragmentManager().popBackStack();
    }

    private void selectOption(int index) {
        if (options == null || index >= options.length) return;
        String selected = options[index];

        if (category.equals("Pain") && !painLocationSelected) {
            selectedPainLocation = selected;
            painLocationSelected = true;
            tv_category_title.setText("Pain - " + selectedPainLocation);
            buildPainSeverityZones();
            return;
        }

        String finalMessage;
        if (category.equals("Pain")) {
            finalMessage = selectedPainLocation + " pain - " + selected;
        } else {
            finalMessage = selected;
        }

        sendToCaregiver(finalMessage);

        if (dwellTimer != null) dwellTimer.cancel();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CommunicationFragment())
                .commit();
    }


    private void sendToCaregiver(String message) {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot patientDoc) {
                        String patientName = patientDoc.getString("name");
                        String caregiverPhone = patientDoc.getString("caregiverPhone");

                        // Always log to Firestore first — this is what the caregiver feed reads
                        logToFirestore(patientName, uid, message);

                        // Then attempt FCM notification (best-effort)
                        if (caregiverPhone != null && !caregiverPhone.isEmpty()) {
                            db.collection("users")
                                    .whereEqualTo("phone", caregiverPhone)
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                        @Override
                                        public void onSuccess(QuerySnapshot caregiverQuery) {
                                            if (!caregiverQuery.isEmpty()) {
                                                String fcmToken = caregiverQuery.getDocuments()
                                                        .get(0).getString("fcmToken");
                                                if (fcmToken != null) {
                                                    sendNotification(fcmToken,
                                                            patientName + " says:",
                                                            message);
                                                }
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.e("SAWT", "Caregiver lookup failed: " + e.getMessage());
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("SAWT", "Patient doc fetch failed: " + e.getMessage());
                    }
                });
    }

    private void logToFirestore(String patientName, String uid, String msg) {
        Map<String, Object> log = new HashMap<>();
        log.put("patientName", patientName);
        log.put("message", msg);
        log.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(uid)
                .collection("logs")
                .add(log)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference ref) {
                        Log.d("SAWT", "Log saved: " + msg);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("SAWT", "Log failed: " + e.getMessage());
                    }
                });
    }

    private void sendNotification(String fcmToken, String title, String message) {
        com.google.firebase.functions.FirebaseFunctions functions =
                com.google.firebase.functions.FirebaseFunctions.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", fcmToken);
        data.put("title", title);
        data.put("message", message);
        functions.getHttpsCallable("sendSosNotification")
                .call(data)
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.functions.HttpsCallableResult>() {
                    @Override
                    public void onSuccess(com.google.firebase.functions.HttpsCallableResult result) {
                        android.util.Log.d("SAWT", "Notification sent");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        android.util.Log.e("SAWT", "Failed: " + e.getMessage());
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dwellTimer != null) dwellTimer.cancel();
        if (faceLandmarker != null) faceLandmarker.close();
    }
}