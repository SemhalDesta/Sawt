package sosina.terefe.adu.ac.ae.sawt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class CaregiverSetupFragment extends Fragment {

    private LinearLayout tab_feed, tab_reminders, tab_settings;
    private TextView tab_feed_text, tab_reminders_text, tab_settings_text;
    private ImageView tab_feed_icon, tab_reminders_icon, tab_settings_icon;
    private View tab_feed_dot, tab_reminders_dot, tab_settings_dot;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_caregiver_setup, container, false);

        tab_feed = v.findViewById(R.id.tab_feed);
        tab_reminders = v.findViewById(R.id.tab_reminders);
        tab_settings = v.findViewById(R.id.tab_settings);
        tab_feed_text = v.findViewById(R.id.tab_feed_text);
        tab_reminders_text = v.findViewById(R.id.tab_reminders_text);
        tab_settings_text = v.findViewById(R.id.tab_settings_text);
        tab_feed_icon = v.findViewById(R.id.tab_feed_icon);
        tab_reminders_icon = v.findViewById(R.id.tab_reminders_icon);
        tab_settings_icon = v.findViewById(R.id.tab_settings_icon);
        tab_feed_dot = v.findViewById(R.id.tab_feed_dot);
        tab_reminders_dot = v.findViewById(R.id.tab_reminders_dot);
        tab_settings_dot = v.findViewById(R.id.tab_settings_dot);


        loadFragment(new CaregiverFeedFragment());
        setActiveTab(0);

        tab_feed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new CaregiverFeedFragment());
                setActiveTab(0);
            }
        });

        tab_reminders.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new CaregiverRemindersFragment());
                setActiveTab(1);
            }
        });

        tab_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new CaregiverSettingsFragment());
                setActiveTab(2);
            }
        });

        return v;
    }

    private void loadFragment(Fragment fragment) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.caregiver_fragment_container, fragment)
                .commit();
    }

    private void setActiveTab(int index) {

        int inactive = 0xFF888888;
        int active = 0xFFFFFFFF;

        tab_feed_text.setTextColor(inactive);
        tab_reminders_text.setTextColor(inactive);
        tab_settings_text.setTextColor(inactive);

        tab_feed_icon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        tab_reminders_icon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        tab_settings_icon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));

        tab_feed_dot.setVisibility(View.INVISIBLE);
        tab_reminders_dot.setVisibility(View.INVISIBLE);
        tab_settings_dot.setVisibility(View.INVISIBLE);


        switch (index) {
            case 0:
                tab_feed_text.setTextColor(active);
                tab_feed_icon.setImageTintList(android.content.res.ColorStateList.valueOf(active));
                tab_feed_dot.setVisibility(View.VISIBLE);
                break;
            case 1:
                tab_reminders_text.setTextColor(active);
                tab_reminders_icon.setImageTintList(android.content.res.ColorStateList.valueOf(active));
                tab_reminders_dot.setVisibility(View.VISIBLE);
                break;
            case 2:
                tab_settings_text.setTextColor(active);
                tab_settings_icon.setImageTintList(android.content.res.ColorStateList.valueOf(active));
                tab_settings_dot.setVisibility(View.VISIBLE);
                break;
        }
    }
}