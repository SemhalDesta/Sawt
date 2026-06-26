package sosina.terefe.adu.ac.ae.sawt;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

public class SosFragment extends Fragment {

    private MediaPlayer alarmPlayer;
    private Button btn_stop_alarm;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_sos, container, false);

        btn_stop_alarm = v.findViewById(R.id.btn_stop_alarm);

        playAlarm();

        btn_stop_alarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopAlarm();
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CommunicationFragment())
                        .commit();
            }
        });

        return v;
    }

    private void playAlarm() {
        AudioManager audioManager = (AudioManager) getContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
        );
        alarmPlayer = MediaPlayer.create(getContext(), R.raw.alarm_sound);
        alarmPlayer.setLooping(true);
        alarmPlayer.start();
    }

    private void stopAlarm() {
        if (alarmPlayer != null) {
            if (alarmPlayer.isPlaying()) {
                alarmPlayer.stop();
            }
            alarmPlayer.release();
            alarmPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarm();
    }
}