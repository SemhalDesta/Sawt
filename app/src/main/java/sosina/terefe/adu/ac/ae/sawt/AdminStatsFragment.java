package sosina.terefe.adu.ac.ae.sawt;

import static android.view.View.VISIBLE;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class AdminStatsFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout stats_container;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0D0D"));

        TextView title = new TextView(getContext());
        title.setText("Statistics");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(40, 40, 40, 20);

        TextView tv_title_2= new TextView(getContext());
        tv_title_2.setText("Overview of registered data");
        tv_title_2.setTextColor(Color.parseColor("#888888"));
        title.setPadding(40, 40, 40, 20);
        root.addView(title);
        root.addView(tv_title_2);


        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);

        stats_container = new LinearLayout(getContext());
        stats_container.setOrientation(LinearLayout.VERTICAL);
        stats_container.setPadding(32, 0, 32, 32);

        scrollView.addView(stats_container);
        root.addView(scrollView);

        db = FirebaseFirestore.getInstance();
        loadStats();

        return root;
    }

    private void loadStats() {
        stats_container.removeAllViews();


        TextView loading = new TextView(getContext());
        loading.setText("Loading...");
        loading.setTextColor(Color.parseColor("#888888"));
        loading.setTextSize(16);
        loading.setPadding(0, 20, 0, 0);
        stats_container.addView(loading);

        db.collection("users").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        stats_container.removeAllViews();

                        int totalUsers = 0;
                        int totalPatients = 0;
                        int totalCaregivers = 0;

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String role = doc.getString("role");
                            if (role == null) continue;
                            if (role.equals("admin")) continue;
                            totalUsers++;
                            if (role.equals("patient")) totalPatients++;
                            if (role.equals("caregiver")) totalCaregivers++;
                        }

                        addStatCard("Total Users", totalUsers, "#71f5b7");
                        addStatCard("Total Patients", totalPatients, "#71f5b7");
                        addStatCard("Total Caregivers", totalCaregivers, "#71f5b7");

                        loadSosCount();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load stats: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadSosCount() {

        db.collection("users")
                .whereEqualTo("role", "patient")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot patients) {
                        final int[] totalSos = {0};
                        final int[] patientsChecked = {0};
                        final int patientCount = patients.size();

                        if (patientCount == 0) {
                            addStatCard("Total SOS Alerts", 0, "#F87171");
                            return;
                        }

                        for (QueryDocumentSnapshot patient : patients) {
                            String patientUid = patient.getId();

                            db.collection("users").document(patientUid)
                                    .collection("logs")
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                        @Override
                                        public void onSuccess(QuerySnapshot logs) {
                                            for (QueryDocumentSnapshot log : logs) {
                                                String message = log.getString("message");
                                                if (message != null &&
                                                        (message.toLowerCase().contains("sos") ||
                                                                message.toLowerCase().contains("emergency"))) {
                                                    totalSos[0]++;
                                                }
                                            }
                                            patientsChecked[0]++;
                                            if (patientsChecked[0] == patientCount) {
                                                addStatCard("Total SOS Alerts", totalSos[0], "#E65100");
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            patientsChecked[0]++;
                                            if (patientsChecked[0] == patientCount) {
                                                addStatCard("Total SOS Alerts", totalSos[0], "#E65100");
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    private void addStatCard(String label, int count, String color) {
        if (getContext() == null) return;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);

        card.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        card.setPadding(32, 32, 32, 32);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);


        TextView tv_count = new TextView(getContext());
        tv_count.setText(String.valueOf(count));
        tv_count.setTextColor(Color.parseColor(color));
        tv_count.setTextSize(48);
        tv_count.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tv_count);


        TextView tv_label = new TextView(getContext());
        tv_label.setText(label);
        tv_label.setTextColor(Color.parseColor("#888888"));
        tv_label.setTextSize(14);
        card.addView(tv_label);

        stats_container.addView(card);
    }
}