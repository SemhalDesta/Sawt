package sosina.terefe.adu.ac.ae.sawt;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaregiverFeedFragment extends Fragment {

    private LinearLayout feed_container;
    private Spinner patient_spinner;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<ListenerRegistration> listeners = new ArrayList<>();
    private Map<String, List<View>> cardsByPatient = new HashMap<>();
    private List<String> patientNames = new ArrayList<>();
    private List<String> patientUids = new ArrayList<>();
    private String selectedPatientUid = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_caregiver_feed, container, false);

        feed_container = v.findViewById(R.id.feed_container);
        patient_spinner = v.findViewById(R.id.patient_spinner);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadFeed();

        return v;
    }

    private void loadFeed() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    String caregiverPhone = document.getString("phone");
                    if (caregiverPhone != null) {
                        findPatients(caregiverPhone);
                    }
                });
    }

    private void findPatients(String caregiverPhone) {
        db.collection("users")
                .whereEqualTo("caregiverPhone", caregiverPhone)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> patients = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) patients.add(doc);

                    if (patients.size() > 1) {
                        patientNames.add("All Patients");
                        patientUids.add(null);

                        for (QueryDocumentSnapshot doc : patients) {
                            String name = doc.getString("name");
                            patientNames.add(name != null ? name : "Patient");
                            patientUids.add(doc.getId());
                        }

                        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                                getContext(),
                                android.R.layout.simple_spinner_item,
                                patientNames) {
                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                TextView tv = (TextView) super.getView(position, convertView, parent);
                                tv.setTextColor(Color.WHITE);
                                tv.setTextSize(14);
                                return tv;
                            }
                            @Override
                            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                                tv.setTextColor(Color.WHITE);
                                tv.setBackgroundColor(Color.parseColor("#1A1A1A"));
                                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                                return tv;
                            }
                        };
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        patient_spinner.setAdapter(adapter);
                        patient_spinner.setVisibility(View.VISIBLE);

                        patient_spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                                selectedPatientUid = patientUids.get(position);
                                applyFilter();
                            }
                            @Override
                            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                        });
                    }

                    for (QueryDocumentSnapshot doc : patients) {
                        cardsByPatient.put(doc.getId(), new ArrayList<>());
                        listenToPatientLogs(doc.getId());
                    }
                });
    }

    private void applyFilter() {
        for (Map.Entry<String, List<View>> entry : cardsByPatient.entrySet()) {
            String uid = entry.getKey();
            boolean show = selectedPatientUid == null || selectedPatientUid.equals(uid);
            for (View card : entry.getValue()) {
                card.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void listenToPatientLogs(String patientUid) {
        ListenerRegistration listener = db.collection("users")
                .document(patientUid)
                .collection("logs")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {
                            String message = change.getDocument().getString("message");
                            String patientName = change.getDocument().getString("patientName");
                            String category = change.getDocument().getString("category"); // add this
                            long timestamp = change.getDocument().getLong("timestamp") != null
                                    ? change.getDocument().getLong("timestamp") : 0;
                            addMessageCard(message, patientName, timestamp, patientUid, category); // pass it
                        }
                    }
                });
        listeners.add(listener);
    }

    private void addMessageCard(String message, String patientName, long timestamp, String patientUid, String category) {
        if (getContext() == null) return;
        category = getCategory(message, category);
        int categoryColor = getCategoryColor(category);

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(getContext().getDrawable(R.drawable.card_rounded));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);
        card.setClipToOutline(true);

        View accent = new View(getContext());
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(4),
                LinearLayout.LayoutParams.MATCH_PARENT);
        accent.setLayoutParams(accentParams);
        accent.setBackgroundColor(categoryColor);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        content.setLayoutParams(contentParams);

        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tv_message = new TextView(getContext());
        tv_message.setText(message);
        tv_message.setTextColor(Color.WHITE);
        tv_message.setTextSize(15);
        tv_message.setTypeface(null, android.graphics.Typeface.BOLD);
        tv_message.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tv_time = new TextView(getContext());
        tv_time.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(new Date(timestamp)));
        tv_time.setTextColor(Color.parseColor("#888888"));
        tv_time.setTextSize(11);

        topRow.addView(tv_message);
        topRow.addView(tv_time);

        TextView tv_patient = new TextView(getContext());
        tv_patient.setText(patientName);
        tv_patient.setTextColor(Color.parseColor("#888888"));
        tv_patient.setTextSize(12);
        tv_patient.setPadding(0, 4, 0, 8);

        TextView tv_category = new TextView(getContext());
        tv_category.setText(category);
        tv_category.setTextColor(categoryColor);
        tv_category.setTextSize(11);
        tv_category.setPadding(dp(10), 4, dp(10), 4);

        content.addView(topRow);
        content.addView(tv_patient);
        content.addView(tv_category);

        card.addView(accent);
        card.addView(content);

        boolean visible = selectedPatientUid == null || selectedPatientUid.equals(patientUid);
        card.setVisibility(visible ? View.VISIBLE : View.GONE);

        feed_container.addView(card, 0);

        if (!cardsByPatient.containsKey(patientUid)) {
            cardsByPatient.put(patientUid, new ArrayList<>());
        }
        cardsByPatient.get(patientUid).add(card);
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private String getCategory(String message, String category) {
        if (category != null && !category.isEmpty()) return category;
        // fallback keyword detection for old logs that don't have category field
        if (message == null) return "General";
        message = message.toLowerCase();
        if (message.contains("hungry") || message.contains("thirsty") ||
                message.contains("bathroom") || message.contains("nauseous") ||
                message.contains("cold") || message.contains("hot")) return "Physical Needs";
        if (message.contains("pain") || message.contains("head") ||
                message.contains("chest") || message.contains("arm") ||
                message.contains("leg") || message.contains("mild") ||
                message.contains("severe")) return "Pain";
        if (message.contains("okay") || message.contains("scared") ||
                message.contains("sad") || message.contains("frustrated")) return "Emotional State";
        if (message.contains("help") || message.contains("sos") ||
                message.contains("emergency")) return "SOS";
        return "General";
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case "Physical Needs": return Color.parseColor("#2E7D32");
            case "Pain": return Color.parseColor("#B71C1C");
            case "Emotional State": return Color.parseColor("#6A1B9A");
            case "SOS": return Color.parseColor("#E65100");
            default: return Color.parseColor("#37474F");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (ListenerRegistration l : listeners) l.remove();
    }
}