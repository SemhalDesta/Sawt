package sosina.terefe.adu.ac.ae.sawt;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class CaregiverSettingsFragment extends Fragment {

    private TextView tv_caregiver_name, tv_caregiver_phone;
    private LinearLayout patients_container;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String caregiverPhone = "";
    private String caregiverUid = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_caregiver_settings, container, false);

        tv_caregiver_name = v.findViewById(R.id.tv_caregiver_name);
        tv_caregiver_phone = v.findViewById(R.id.tv_caregiver_phone);
        patients_container = v.findViewById(R.id.patients_container);
        Button btn_logout = v.findViewById(R.id.btn_logout);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        caregiverUid = mAuth.getCurrentUser().getUid();

        loadProfile();

        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmLogout();
            }
        });

        return v;
    }

    private void loadProfile() {
        db.collection("users").document(caregiverUid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        String name = doc.getString("name");
                        String phone = doc.getString("phone");

                        if (name != null) tv_caregiver_name.setText(name);
                        if (phone != null) {
                            tv_caregiver_phone.setText(phone);
                            caregiverPhone = phone;
                            loadLinkedPatients();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadLinkedPatients() {
        patients_container.removeAllViews();

        db.collection("users")
                .whereEqualTo("caregiverPhone", caregiverPhone)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot.isEmpty()) {
                            TextView empty = new TextView(getContext());
                            empty.setText("No linked patients yet.");
                            empty.setTextColor(Color.parseColor("#888888"));
                            empty.setTextSize(14);
                            patients_container.addView(empty);
                            return;
                        }

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String patientUid = doc.getId();
                            String name = doc.getString("name");
                            String phone = doc.getString("phone");
                            if (name == null) continue;
                            addPatientCard(patientUid, name, phone);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load patients: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addPatientCard(String patientUid, String name, String phone) {
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        card.setClipToOutline(true);
        card.setPadding(20 * dp, 20 * dp, 20 * dp, 20 * dp);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12 * dp);
        card.setLayoutParams(cardParams);

        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textSection = new LinearLayout(getContext());
        textSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textSection.setLayoutParams(textParams);

        TextView tv_name = new TextView(getContext());
        tv_name.setText(name);
        tv_name.setTextColor(Color.WHITE);
        tv_name.setTextSize(16);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        textSection.addView(tv_name);

        if (phone != null) {
            TextView tv_phone = new TextView(getContext());
            tv_phone.setText(phone);
            tv_phone.setTextColor(Color.parseColor("#888888"));
            tv_phone.setTextSize(13);
            tv_phone.setPadding(0, 4 * dp, 0, 0);
            textSection.addView(tv_phone);
        }

        topRow.addView(textSection);


        Button btn_edit = new Button(getContext());
        btn_edit.setText("Contacts");
        btn_edit.setBackground(getContext().getDrawable(R.drawable.zone_physical_bg));
        btn_edit.setTextColor(Color.parseColor("#4ADE80"));
        btn_edit.setTextSize(12);
        btn_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPatientContacts(patientUid, name, card);
            }
        });
        topRow.addView(btn_edit);
        card.addView(topRow);

        LinearLayout contacts_container = new LinearLayout(getContext());
        contacts_container.setTag("contacts_" + patientUid);
        contacts_container.setOrientation(LinearLayout.VERTICAL);
        contacts_container.setVisibility(View.GONE);
        LinearLayout.LayoutParams contactsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        contactsParams.setMargins(0, 12 * dp, 0, 0);
        contacts_container.setLayoutParams(contactsParams);
        card.addView(contacts_container);

        patients_container.addView(card);
    }

    private void showPatientContacts(String patientUid, String patientName, LinearLayout card) {
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout contacts_container = (LinearLayout) card.findViewWithTag("contacts_" + patientUid);
        if (contacts_container == null) return;

        if (contacts_container.getVisibility() == View.VISIBLE) {
            contacts_container.setVisibility(View.GONE);
            return;
        }

        contacts_container.setVisibility(View.VISIBLE);
        contacts_container.removeAllViews();


        Button btn_add = new Button(getContext());
        btn_add.setText("+ Add Emergency Contact");
        btn_add.setBackground(getContext().getDrawable(R.drawable.zone_physical_bg));
        btn_add.setTextColor(Color.parseColor("#4ADE80"));
        btn_add.setTextSize(12);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, 0, 8 * dp);
        btn_add.setLayoutParams(btnParams);
        btn_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddContactDialog(patientUid, contacts_container);
            }
        });
        contacts_container.addView(btn_add);

        loadPatientContacts(patientUid, contacts_container);
    }

    private void loadPatientContacts(String patientUid, LinearLayout contacts_container) {
        db.collection("users").document(patientUid)
                .collection("emergencyContacts")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        while (contacts_container.getChildCount() > 1) {
                            contacts_container.removeViewAt(1);
                        }

                        if (querySnapshot.isEmpty()) {
                            TextView empty = new TextView(getContext());
                            empty.setText("No emergency contacts yet.");
                            empty.setTextColor(Color.parseColor("#888888"));
                            empty.setTextSize(13);
                            empty.setPadding(0, 8, 0, 0);
                            contacts_container.addView(empty);
                            return;
                        }

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String contactId = doc.getId();
                            String name = doc.getString("name");
                            String phone = doc.getString("phone");
                            if (name == null) continue;
                            addContactRow(patientUid, contactId, name, phone, contacts_container);
                        }
                    }
                });
    }

    private void addContactRow(String patientUid, String contactId, String name,
                               String phone, LinearLayout contacts_container) {
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        row.setClipToOutline(true);
        row.setPadding(16 * dp, 12 * dp, 16 * dp, 12 * dp);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 4 * dp, 0, 4 * dp);
        row.setLayoutParams(rowParams);

        LinearLayout textSection = new LinearLayout(getContext());
        textSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textSection.setLayoutParams(textParams);

        TextView tv_name = new TextView(getContext());
        tv_name.setText(name);
        tv_name.setTextColor(Color.WHITE);
        tv_name.setTextSize(14);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        textSection.addView(tv_name);

        if (phone != null) {
            TextView tv_phone = new TextView(getContext());
            tv_phone.setText(phone);
            tv_phone.setTextColor(Color.parseColor("#888888"));
            tv_phone.setTextSize(12);
            textSection.addView(tv_phone);
        }

        row.addView(textSection);

        Button btn_delete = new Button(getContext());
        btn_delete.setText("Delete");
        btn_delete.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        btn_delete.setTextColor(Color.parseColor("#FF4444"));
        btn_delete.setTextSize(11);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Contact")
                        .setMessage("Delete \"" + name + "\"?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                db.collection("users").document(patientUid)
                                        .collection("emergencyContacts")
                                        .document(contactId)
                                        .delete()
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void unused) {
                                                contacts_container.removeView(row);
                                                Toast.makeText(getContext(), "Contact deleted", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        row.addView(btn_delete);

        contacts_container.addView(row);
    }

    private void showAddContactDialog(String patientUid, LinearLayout contacts_container) {
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextView label_name = new TextView(getContext());
        label_name.setText("Name");
        label_name.setTextSize(14);
        label_name.setPadding(0, 0, 0, 8);
        layout.addView(label_name);

        EditText et_name = new EditText(getContext());
        et_name.setHint("Contact name");
        layout.addView(et_name);

        TextView label_phone = new TextView(getContext());
        label_phone.setText("Phone");
        label_phone.setTextSize(14);
        label_phone.setPadding(0, 16, 0, 8);
        layout.addView(label_phone);

        EditText et_phone = new EditText(getContext());
        et_phone.setHint("Phone number");
        et_phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(et_phone);

        new AlertDialog.Builder(getContext())
                .setTitle("Add Emergency Contact")
                .setView(layout)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = et_name.getText().toString().trim();
                        String phone = et_phone.getText().toString().trim();

                        if (name.isEmpty() || phone.isEmpty()) {
                            Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> contact = new HashMap<>();
                        contact.put("name", name);
                        contact.put("phone", phone);

                        db.collection("users").document(patientUid)
                                .collection("emergencyContacts")
                                .add(contact)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference docRef) {
                                        Toast.makeText(getContext(), "Contact added", Toast.LENGTH_SHORT).show();
                                        addContactRow(patientUid, docRef.getId(), name, phone, contacts_container);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAuth.signOut();
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new LoginFragment())
                                .commit();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}