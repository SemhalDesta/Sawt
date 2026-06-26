package sosina.terefe.adu.ac.ae.sawt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class AdminFragment extends Fragment {

    private LinearLayout tab_users, tab_stats, tab_menu;
    private TextView tab_users_text, tab_stats_text, tab_menu_text;
    private Button btn_admin_logout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin, container, false);

        tab_users = v.findViewById(R.id.tab_users);
        tab_stats = v.findViewById(R.id.tab_stats);
        tab_menu = v.findViewById(R.id.tab_menu);
        tab_users_text = v.findViewById(R.id.tab_users_text);
        tab_stats_text = v.findViewById(R.id.tab_stats_text);
        tab_menu_text = v.findViewById(R.id.tab_menu_text);

        btn_admin_logout = v.findViewById(R.id.btn_admin_logout);
        btn_admin_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to logout?")
                        .setPositiveButton("Logout", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                                getParentFragmentManager()
                                        .beginTransaction()
                                        .replace(R.id.fragment_container, new LoginFragment())
                                        .commit();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });


        loadFragment(new AdminUsersFragment());
        setActiveTab(tab_users_text);

        tab_users.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new AdminUsersFragment());
                setActiveTab(tab_users_text);
            }
        });

        tab_stats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new AdminStatsFragment());
                setActiveTab(tab_stats_text);
            }
        });

        tab_menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadFragment(new AdminMenuFragment());
                setActiveTab(tab_menu_text);
            }
        });

        return v;
    }

    private void loadFragment(Fragment fragment) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.admin_fragment_container, fragment)
                .commit();
    }

    private void setActiveTab(TextView activeTab) {
        tab_users_text.setTextColor(0xFF888888);
        tab_stats_text.setTextColor(0xFF888888);
        tab_menu_text.setTextColor(0xFF888888);
        activeTab.setTextColor(0xFFFFFFFF);
    }
}