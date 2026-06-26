package sosina.terefe.adu.ac.ae.sawt;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import androidx.annotation.NonNull;

public class AdminUsersFragment extends Fragment {

    private LinearLayout users_container;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0D0D"));


        TextView title = new TextView(getContext());
        title.setText("All Users");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(40, 40, 40, 4);
        root.addView(title);


        TextView subtitle = new TextView(getContext());
        subtitle.setText("Manage registered accounts");
        subtitle.setTextColor(Color.parseColor("#888888"));
        subtitle.setTextSize(13);
        subtitle.setPadding(40, 0, 40, 20);
        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);

        users_container = new LinearLayout(getContext());
        users_container.setOrientation(LinearLayout.VERTICAL);
        users_container.setPadding(32, 0, 32, 32);

        scrollView.addView(users_container);
        root.addView(scrollView);

        db = FirebaseFirestore.getInstance();
        loadUsers();

        return root;
    }

    private void loadUsers() {
        users_container.removeAllViews();

        db.collection("users").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot.isEmpty()) {
                            TextView empty = new TextView(getContext());
                            empty.setText("No users found.");
                            empty.setTextColor(Color.parseColor("#888888"));
                            empty.setTextSize(16);
                            empty.setPadding(0, 20, 0, 0);
                            users_container.addView(empty);
                            return;
                        }

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String uid = doc.getId();
                            String name = doc.getString("name");
                            String email = doc.getString("email");
                            String role = doc.getString("role");
                            String phone = doc.getString("phone");

                            if (name == null) name = "Unknown";
                            if (email == null) email = "No email";
                            if (role == null) role = "Unknown";
                            if (phone == null) phone = "No phone";

                            if (role.equals("admin")) continue;

                            addUserCard(uid, name, email, role, phone);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addUserCard(String uid, String name, String email, String role, String phone) {
        int dp = (int) getResources().getDisplayMetrics().density;


        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        card.setClipToOutline(true);
        card.setPadding(24 * dp, 20 * dp, 24 * dp, 20 * dp);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12 * dp);
        card.setLayoutParams(cardParams);


        TextView tv_name = new TextView(getContext());
        tv_name.setText(name);
        tv_name.setTextColor(Color.WHITE);
        tv_name.setTextSize(19);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tv_name);


        TextView tv_phone = new TextView(getContext());
        tv_phone.setText(phone);
        tv_phone.setTextColor(Color.parseColor("#888888"));
        tv_phone.setTextSize(17);
        tv_phone.setPadding(0, 4 * dp, 0, 0);
        card.addView(tv_phone);


        LinearLayout bottomRow = new LinearLayout(getContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 12 * dp, 0, 0);
        bottomRow.setLayoutParams(rowParams);


        TextView tv_role = new TextView(getContext());
        tv_role.setText(role);
        tv_role.setTextSize(13);
        tv_role.setTypeface(null, android.graphics.Typeface.BOLD);
        tv_role.setPadding(16 * dp, 6 * dp, 16 * dp, 6 * dp);
        tv_role.setTextColor(role.equals("patient") ?
                Color.parseColor("#4ADE80") : Color.parseColor("#FFFFFF"));
        tv_role.setBackground(getContext().getDrawable(R.drawable.zone_card_selected));

        bottomRow.addView(tv_role);

        View spacer = new View(getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        spacer.setLayoutParams(spacerParams);
        bottomRow.addView(spacer);


        ImageView btn_delete = new ImageView(getContext());
        btn_delete.setImageDrawable(getContext().getDrawable(R.drawable.outline_delete_24));
        btn_delete.setColorFilter(Color.parseColor("#F87171"));
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                70, 70);
        btn_delete.setLayoutParams(imgParams);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete(uid, name, card);
            }
        });

        bottomRow.addView(btn_delete);

        card.addView(bottomRow);
        users_container.addView(card);
    }

    private void confirmDelete(String uid, String name, LinearLayout card) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " + name + "?")
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteUser(uid, card);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUser(String uid, LinearLayout card) {
        db.collection("users").document(uid).delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        users_container.removeView(card);
                        Toast.makeText(getContext(), "User deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}