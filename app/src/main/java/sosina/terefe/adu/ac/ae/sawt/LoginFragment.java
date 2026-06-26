package sosina.terefe.adu.ac.ae.sawt;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import com.google.firebase.messaging.FirebaseMessaging;

public class LoginFragment extends Fragment {

    private LinearLayout layout_signin, layout_register, layout_patient;
    private TextView tabSigninOnRegister, register_tab, signin_tab, tv_switch_register;
    private Button btn_patient, btn_careGiver;
    private EditText et_email, et_password;
    private Button btn_signin;
    private EditText et_name, et_phone, et_email_register, et_password_register;
    private EditText et_phone_caregiver;
    private Button btn_register;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String selectedRole = "caregiver";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_login, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        layout_signin = v.findViewById(R.id.layout_signin);
        layout_register = v.findViewById(R.id.layout_register);
        layout_patient = v.findViewById(R.id.layout_patient);

        tabSigninOnRegister = v.findViewById(R.id.tab_signin_register);
        register_tab = v.findViewById(R.id.tab_register);
        signin_tab = v.findViewById(R.id.tab_signin);

        btn_patient = v.findViewById(R.id.btn_patient);
        btn_careGiver = v.findViewById(R.id.btn_caregiver);

        tv_switch_register = v.findViewById(R.id.tv_switch_register);

        et_email = v.findViewById(R.id.et_email);
        et_password = v.findViewById(R.id.et_password);
        btn_signin = v.findViewById(R.id.btn_main);

        et_name = v.findViewById(R.id.et_name);
        et_phone = v.findViewById(R.id.et_phone);
        et_email_register = v.findViewById(R.id.et_email_register);
        et_password_register = v.findViewById(R.id.et_password_register);
        et_phone_caregiver = v.findViewById(R.id.et_phone_caregiver);
        btn_register = v.findViewById(R.id.btn_register);

        register_tab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_signin.setVisibility(View.INVISIBLE);
                layout_register.setVisibility(View.VISIBLE);
            }
        });

        tabSigninOnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_register.setVisibility(View.INVISIBLE);
                layout_signin.setVisibility(View.VISIBLE);
            }
        });

        btn_patient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedRole = "patient";
                layout_patient.setVisibility(View.VISIBLE);
            }
        });

        btn_careGiver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedRole = "caregiver";
                layout_patient.setVisibility(View.GONE);
            }
        });

        tv_switch_register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_signin.setVisibility(View.INVISIBLE);
                layout_register.setVisibility(View.VISIBLE);
            }
        });

        btn_signin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleSignIn(view);
            }
        });

        btn_register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleRegister(view);
            }
        });

        return v;
    }

    public void handleSignIn(View v) {
        String email = et_email.getText().toString().trim();
        String password = et_password.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        String uid = mAuth.getCurrentUser().getUid();

                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                                        if (!isAdded()) return;
                                        String role = documentSnapshot.getString("role");
                                        if (role == null) {
                                            Toast.makeText(getContext(), "User data not found. Please register again.", Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        navigateBasedOnRole(role);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(getContext(), "Data fetching failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void handleRegister(View v) {
        String name = et_name.getText().toString().trim();
        String phone = et_phone.getText().toString().trim();
        String email = et_email_register.getText().toString().trim();
        String password = et_password_register.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String caregiverPhone = "";
        if (selectedRole.equals("patient")) {
            caregiverPhone = et_phone_caregiver.getText().toString().trim();
            if (caregiverPhone.isEmpty()) {
                Toast.makeText(getContext(), "Please enter caregiver's phone number", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String finalCaregiverPhone = caregiverPhone;

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        String uid = mAuth.getCurrentUser().getUid();

                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("phone", phone);
                        user.put("role", selectedRole);
                        if (selectedRole.equals("patient")) {
                            user.put("caregiverPhone", finalCaregiverPhone);
                        }

                        db.collection("users").document(uid).set(user)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Toast.makeText(getContext(), "Account created successfully", Toast.LENGTH_SHORT).show();
                                        navigateBasedOnRole(selectedRole);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(getContext(), "Failed to save data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Registration Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateBasedOnRole(String role) {
        if (!isAdded()) return;

        // Grab the current FCM token now that we know who's logged in,
        // and save it to this user's Firestore doc so SOS/notifications can reach them.
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(SawtMessagingService::saveTokenForCurrentUser)
                .addOnFailureListener(e ->
                        android.util.Log.e("SAWT", "Failed to fetch FCM token: " + e.getMessage()));

        Fragment nextFragment;

        switch (role) {
            case "patient":
                nextFragment = new PatientHomeFragment();
                break;
            case "admin":
                nextFragment = new AdminFragment();
                break;
            default:
                nextFragment = new CaregiverSetupFragment();
                break;
        }

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, nextFragment)
                .commit();
    }
}