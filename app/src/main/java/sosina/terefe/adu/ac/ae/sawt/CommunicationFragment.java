
package sosina.terefe.adu.ac.ae.sawt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
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

public class CommunicationFragment extends Fragment {

    private TextView tv_patient_name, tv_yes_no_toggle;
    private LinearLayout zone_sos, zone_back;
    private LinearLayout zones_container;
    private ScrollView zones_scroll;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private MediaPlayer mediaPlayer;
    private PreviewView cameraPreview;
    private FaceLandmarker faceLandmarker;
    private FirebaseFunctions functions;

    private List<LinearLayout> zoneViews = new ArrayList<>();
    private List<String> categoryNames = new ArrayList<>();

    private int currentZone = 0;
    private boolean isSelecting = false;

    // --- Blink state ---
    private boolean eyesCurrentlyClosed = false;
    private long eyeCloseStartTime = 0;
    private boolean blinkRegistered = false;
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
    private static final int DOUBLE_BLINK_WINDOW_MS = 1200;
    private static final int DWELL_DURATION_MS = 5000;

    private CountDownTimer dwellTimer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_communication, container, false);

        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.CALL_PHONE}, 1);
        }

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setupFaceLandmarker();
            startCamera();
        } else {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.CAMERA}, 2);
        }

        BlinkConfig config = BlinkConfig.load(requireContext());
        MIN_BLINK_DURATION_MS = config.minBlinkMs;
        MAX_BLINK_DURATION_MS = config.maxBlinkMs;
        EAR_CLOSE_THRESHOLD = config.earCloseThreshold;   // ← add
        EAR_OPEN_THRESHOLD  = config.earOpenThreshold;

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        functions = FirebaseFunctions.getInstance();

        tv_patient_name = v.findViewById(R.id.tv_patient_name);
        tv_yes_no_toggle = v.findViewById(R.id.tv_yes_no_toggle);
        zone_sos = v.findViewById(R.id.zone_sos);
        zone_back = v.findViewById(R.id.zone_back);
        zones_container = v.findViewById(R.id.zones_container);
        zones_scroll = v.findViewById(R.id.zones_scroll);
        cameraPreview = v.findViewById(R.id.camera_preview);

        loadPatientName();
        loadCategoriesFromFirestore();

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



        return v;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupFaceLandmarker();
                startCamera();
            }
        }
    }

    private void loadCategoriesFromFirestore() {
        db.collection("menus")
                .orderBy("order")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (!isAdded()) return;

                        while (zones_container.getChildCount() > 2) {
                            zones_container.removeViewAt(0);
                        }
                        zoneViews.clear();
                        categoryNames.clear();

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String name = doc.getString("name");
                            if (name == null) continue;
                            if (name.equalsIgnoreCase("SOS")) continue;

                            addCategoryZone(name);
                            categoryNames.add(name);
                        }

                        categoryNames.add("Back");
                        categoryNames.add("SOS");

                        currentZone = 0;
                        resetBlinkState();
                        highlightZone(0);
                        startDwellTimer();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        android.util.Log.e("SAWT", "Failed to load categories: " + e.getMessage());
                    }
                });
    }

    private void addCategoryZone(String name) {
        int heightInPx = (int) (120 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);
        int padPx = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout zone = new LinearLayout(getContext());
        zone.setOrientation(LinearLayout.HORIZONTAL);
        zone.setGravity(android.view.Gravity.CENTER_VERTICAL);
        zone.setBackground(getContext().getDrawable(R.drawable.zone_comm_normal));
        zone.setClipToOutline(true);

        LinearLayout.LayoutParams zoneParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightInPx);
        zoneParams.setMargins(marginPx, marginPx, marginPx, marginPx);
        zone.setLayoutParams(zoneParams);
        zone.setPadding(padPx, padPx, padPx, padPx);

        TextView tv_name = new TextView(getContext());
        tv_name.setText(name);
        tv_name.setTextColor(Color.parseColor("#E8E8E8"));
        tv_name.setTextSize(22);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        zone.addView(tv_name);

        zoneViews.add(zone);

        int insertIndex = zones_container.getChildCount() - 2;
        zones_container.addView(zone, insertIndex < 0 ? 0 : insertIndex);
    }

    private void highlightZone(int index) {
        for (int i = 0; i < zoneViews.size(); i++) {
            zoneViews.get(i).setBackground(
                    getContext().getDrawable(R.drawable.zone_comm_normal));
            zoneViews.get(i).setClipToOutline(true);
        }

        zone_sos.setBackgroundResource(R.drawable.zone_sos_bg);
        zone_back.setBackground(getContext().getDrawable(R.drawable.zone_comm_normal));
        zone_back.setClipToOutline(true);

        if (index < zoneViews.size()) {
            zoneViews.get(index).setBackground(
                    getContext().getDrawable(R.drawable.zone_comm_selected));
            zoneViews.get(index).setClipToOutline(true);

            LinearLayout zone = zoneViews.get(index);
            zones_scroll.post(new Runnable() {
                @Override
                public void run() {
                    zones_scroll.smoothScrollTo(0, zone.getTop());
                }
            });
        } else if (index < categoryNames.size() && categoryNames.get(index).equals("Back")) {
            zone_back.setBackground(getContext().getDrawable(R.drawable.zone_comm_selected));
            zone_back.setClipToOutline(true);
            zones_scroll.post(new Runnable() {
                @Override
                public void run() {
                    zones_scroll.smoothScrollTo(0, zone_back.getTop());
                }
            });
        } else {
            zone_sos.setBackgroundResource(R.drawable.zone_active_bg);
            zones_scroll.post(new Runnable() {
                @Override
                public void run() {
                    zones_scroll.smoothScrollTo(0, zone_sos.getTop());
                }
            });
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

                onValidBlink();
            }
        }
    }

    private void onValidBlink() {

        advanceZone();
    }

    private void resetBlinkState() {
        eyesCurrentlyClosed = false;
        eyeCloseStartTime = 0;
        blinkCount = 0;
        firstBlinkTime = 0;
    }

    private void advanceZone() {
        if (categoryNames.isEmpty()) return;
        currentZone = (currentZone + 1) % categoryNames.size();
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
                if (!isSelecting && !categoryNames.isEmpty() && isAdded()) {
                    isSelecting = true;
                    String selected = categoryNames.get(currentZone);
                    if (selected.equals("SOS")) {
                        triggerSOS();
                    } else if (selected.equals("Back")) {
                        goToHome();
                    } else {
                        goToSubmenu(selected);
                    }
                    isSelecting = false;
                }
            }
        }.start();
    }

    private void loadPatientName() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot document) {
                        if (document.exists()) {
                            String name = document.getString("name");
                            tv_patient_name.setText(name);
                        }
                    }
                });

        tv_patient_name.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (dwellTimer != null) dwellTimer.cancel();
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new PatientSettingsFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            }
        });
    }

    private void goToHome() {
        if (dwellTimer != null) dwellTimer.cancel();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new PatientHomeFragment())
                .commit();
    }

    private void goToSubmenu(String category) {
        if (dwellTimer != null) dwellTimer.cancel();
        Submenu submenuFragment = new Submenu(category);
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, submenuFragment)
                .addToBackStack(null)
                .commit();
    }

    private void triggerSOS() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot patientDoc) {
                        String patientName = patientDoc.getString("name");
                        String caregiverPhone = patientDoc.getString("caregiverPhone");
                        callCaregiver(caregiverPhone);

                        logSosToFirestore(uid, patientName);

                        db.collection("users")
                                .document(uid)
                                .collection("emergencyContacts")
                                .get()
                                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                    @Override
                                    public void onSuccess(QuerySnapshot contactsSnapshot) {
                                        for (DocumentSnapshot contact : contactsSnapshot.getDocuments()) {
                                            String fcmToken = contact.getString("fcmToken");
                                            String contactName = contact.getString("name");
                                            if (fcmToken != null) {
                                                sendNotification(
                                                        fcmToken,
                                                        "SOS Emergency",
                                                        patientName + " needs immediate help!!!"
                                                );
                                                android.util.Log.d("SAWT", "SOS sent to: " + contactName);
                                            }
                                        }
                                    }
                                });


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
                                                        "SOS from " + patientName,
                                                        patientName + " needs immediate help!!!");
                                            }
                                        }
                                    }
                                });
                    }
                });

        if (dwellTimer != null) dwellTimer.cancel();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new SosFragment())
                .addToBackStack(null)
                .commit();
    }

    private void logSosToFirestore(String uid, String patientName) {
        Map<String, Object> log = new HashMap<>();
        log.put("patientName", patientName);
        log.put("message", "SOS - Emergency help needed!");
        log.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(uid)
                .collection("logs")
                .add(log)
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.DocumentReference>() {
                    @Override
                    public void onSuccess(com.google.firebase.firestore.DocumentReference ref) {
                        android.util.Log.d("SAWT", "SOS log saved to feed");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        android.util.Log.e("SAWT", "SOS log failed: " + e.getMessage());
                    }
                });
    }

    private void callCaregiver(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return;
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(callIntent);
    }

    private void sendNotification(String fcmToken, String title, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", fcmToken);
        data.put("title", title);
        data.put("message", message);

        functions.getHttpsCallable("sendSosNotification")
                .call(data)
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.functions.HttpsCallableResult>() {
                    @Override
                    public void onSuccess(com.google.firebase.functions.HttpsCallableResult result) {
                        android.util.Log.d("SAWT", "Notification sent successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        android.util.Log.e("SAWT", "Failed to send: " + e.getMessage());
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dwellTimer != null) dwellTimer.cancel();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (faceLandmarker != null) {
            faceLandmarker.close();
        }
    }
}