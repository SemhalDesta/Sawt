package sosina.terefe.adu.ac.ae.sawt;

import android.content.Context;
import android.content.SharedPreferences;

public class BlinkConfig {
    public final int minBlinkMs;
    public final int maxBlinkMs;
    public final float earCloseThreshold; // EAR below this  = eye considered CLOSED
    public final float earOpenThreshold;  // EAR above this  = eye considered OPEN

    private static final float DEFAULT_OPEN_EAR   = 0.30f;
    private static final float DEFAULT_CLOSED_EAR = 0.10f;

    private BlinkConfig(int min, int max, float earClose, float earOpen) {
        this.minBlinkMs = min;
        this.maxBlinkMs = max;
        this.earCloseThreshold = earClose;
        this.earOpenThreshold = earOpen;
    }

    public static BlinkConfig load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                CalibrationFragment.PREFS_NAME, Context.MODE_PRIVATE);
        int min = prefs.getInt(CalibrationFragment.KEY_MIN_BLINK, 60);
        int max = prefs.getInt(CalibrationFragment.KEY_MAX_BLINK, 500);
        float openEAR   = prefs.getFloat(CalibrationFragment.KEY_OPEN_EAR, DEFAULT_OPEN_EAR);
        float closedEAR = prefs.getFloat(CalibrationFragment.KEY_CLOSED_EAR, DEFAULT_CLOSED_EAR);

        // Guard against a degenerate calibration (e.g. bad lighting collapsed both samples together)
        float range = Math.max(0.02f, openEAR - closedEAR);

        // Hysteresis gap, same idea as the existing 0.55/0.25 blendshape thresholds:
        // "closed" needs to drop further than "open" needs to recover, to avoid flicker at the boundary.
        float earClose = closedEAR + 0.25f * range;
        float earOpen  = closedEAR + 0.55f * range;

        return new BlinkConfig(min, max, earClose, earOpen);
    }
}