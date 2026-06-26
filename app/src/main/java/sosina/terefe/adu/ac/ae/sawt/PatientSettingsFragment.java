package sosina.terefe.adu.ac.ae.sawt;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class PatientSettingsFragment extends Fragment {

    private TextView tv_patient_name, tv_patient_phone;
    private TextView tv_caregiver_name, tv_caregiver_phone;
    private LinearLayout emergency_contacts_container;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String patientUid = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_patient_settings, container, false);

        tv_patient_name = v.findViewById(R.id.tv_patient_name);
        tv_patient_phone = v.findViewById(R.id.tv_patient_phone);
        tv_caregiver_name = v.findViewById(R.id.tv_caregiver_name);
        tv_caregiver_phone = v.findViewById(R.id.tv_caregiver_phone);
        emergency_contacts_container = v.findViewById(R.id.emergency_contacts_container);
        Button btn_logout = v.findViewById(R.id.btn_logout);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        patientUid = mAuth.getCurrentUser().getUid();

        loadPatientDetails();

        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmLogout();
            }
        });

        Button btn_calibrate = v.findViewById(R.id.btn_calibrate);
        btn_calibrate.setOnClickListener(view -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CalibrationFragment())
                    .addToBackStack(null)
                    .commit();
        });


        return v;
    }

    private void loadPatientDetails() {
        db.collection("users").document(patientUid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        String name = doc.getString("name");
                        String phone = doc.getString("phone");
                        String caregiverPhone = doc.getString("caregiverPhone");

                        if (name != null) tv_patient_name.setText(name);
                        if (phone != null) tv_patient_phone.setText(phone);

                        if (caregiverPhone != null) {
                            tv_caregiver_phone.setText(caregiverPhone);
                            loadCaregiverName(caregiverPhone);
                        }

                        loadEmergencyContacts();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadCaregiverName(String caregiverPhone) {
        db.collection("users")
                .whereEqualTo("phone", caregiverPhone)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (!querySnapshot.isEmpty()) {
                            String name = querySnapshot.getDocuments().get(0).getString("name");
                            if (name != null) tv_caregiver_name.setText(name);
                        }
                    }
                });
    }

    private void loadEmergencyContacts() {
        emergency_contacts_container.removeAllViews();

        db.collection("users").document(patientUid)
                .collection("emergencyContacts")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot.isEmpty()) {
                            TextView empty = new TextView(getContext());
                            empty.setText("No emergency contacts added yet.");
                            empty.setTextColor(Color.parseColor("#888888"));
                            empty.setTextSize(14);
                            emergency_contacts_container.addView(empty);
                            return;
                        }

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String name = doc.getString("name");
                            String phone = doc.getString("phone");
                            if (name == null) continue;
                            addContactCard(name, phone);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load contacts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addContactCard(String name, String phone) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1A1A1A"));
        card.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        card.setLayoutParams(params);

        TextView tv_name = new TextView(getContext());
        tv_name.setText(name);
        tv_name.setTextColor(Color.WHITE);
        tv_name.setTextSize(16);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tv_name);

        if (phone != null) {
            TextView tv_phone = new TextView(getContext());
            tv_phone.setText(phone);
            tv_phone.setTextColor(Color.parseColor("#888888"));
            tv_phone.setTextSize(13);
            tv_phone.setPadding(0, 4, 0, 0);
            card.addView(tv_phone);
        }

        emergency_contacts_container.addView(card);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAuth.signOut();
                        getParentFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new LoginFragment())
                                .commit();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}