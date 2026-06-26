package sosina.terefe.adu.ac.ae.sawt;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

/**
 * Computes the Eye Aspect Ratio (EAR) from MediaPipe FaceLandmarker output.
 * EAR = vertical eye opening / horizontal eye width.
 * It drops sharply when an eye closes, independently of the
 * eyeBlinkLeft/eyeBlinkRight blendshape score MediaPipe also gives us.
 * Using both together is more reliable than either alone — and EAR in
 * particular can be calibrated per patient, since eye shape/size varies.
 */
public class EyeMetrics {

    // Landmark indices from MediaPipe's 478-point face mesh topology
    private static final int R_OUTER = 33,  R_INNER = 133, R_TOP = 159, R_BOTTOM = 145;
    private static final int L_OUTER = 263, L_INNER = 362, L_TOP = 386, L_BOTTOM = 374;

    public static float averageEAR(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 470) return 0f;
        float rightEAR = singleEAR(landmarks, R_OUTER, R_INNER, R_TOP, R_BOTTOM);
        float leftEAR  = singleEAR(landmarks, L_OUTER, L_INNER, L_TOP, L_BOTTOM);
        return (rightEAR + leftEAR) / 2f;
    }

    private static float singleEAR(List<NormalizedLandmark> lm, int outer, int inner, int top, int bottom) {
        float width  = distance(lm.get(outer), lm.get(inner));
        float height = distance(lm.get(top), lm.get(bottom));
        if (width <= 0f) return 0f;
        return height / width;
    }

    private static float distance(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}