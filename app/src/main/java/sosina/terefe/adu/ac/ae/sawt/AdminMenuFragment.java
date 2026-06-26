package sosina.terefe.adu.ac.ae.sawt;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class AdminMenuFragment extends Fragment {

    private LinearLayout menu_container;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0D0D"));


        TextView title = new TextView(getContext());
        title.setText("Menu Management");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(40, 40, 40, 4);
        root.addView(title);


        TextView subtitle = new TextView(getContext());
        subtitle.setText("Add and edit patient communication options");
        subtitle.setTextColor(Color.parseColor("#888888"));
        subtitle.setTextSize(13);
        subtitle.setPadding(40, 0, 40, 20);
        root.addView(subtitle);


        Button btn_add_category = new Button(getContext());
        btn_add_category.setText(" + Add Category" );
        btn_add_category.setBackground(getContext().getDrawable(R.drawable.zone_physical_bg));
        btn_add_category.setTextColor(Color.parseColor("#4ADE80"));
        btn_add_category.setTextSize(14);
        btn_add_category.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(24, 0, 24, 8);
        btn_add_category.setLayoutParams(btnParams);
        btn_add_category.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddCategoryDialog();
            }
        });
        root.addView(btn_add_category);

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);

        menu_container = new LinearLayout(getContext());
        menu_container.setOrientation(LinearLayout.VERTICAL);
        menu_container.setPadding(32, 0, 32, 32);

        scrollView.addView(menu_container);
        root.addView(scrollView);

        db = FirebaseFirestore.getInstance();
        checkAndSeedMenus();

        return root;
    }

    private void checkAndSeedMenus() {
        db.collection("menus").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot.isEmpty()) {
                            seedDefaultMenus();
                        } else {
                            loadMenus();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to check menus: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void seedDefaultMenus() {
        String[][] defaults = {
                {"Physical Needs", "0", "I am thirsty", "I am hungry", "I need the bathroom", "I feel nauseous"},
                {"Pain", "1", "Head", "Chest", "Arm", "Leg"},
                {"Emotional State", "2", "I am okay", "I am scared", "I am sad", "I am frustrated"}
        };

        final int[] seeded = {0};

        for (String[] category : defaults) {
            String categoryName = category[0];
            String order = category[1];

            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put("name", categoryName);
            categoryData.put("order", Integer.parseInt(order));

            db.collection("menus").add(categoryData)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference docRef) {
                            for (int i = 2; i < category.length; i++) {
                                Map<String, Object> option = new HashMap<>();
                                option.put("text", category[i]);
                                docRef.collection("options").add(option);
                            }
                            seeded[0]++;
                            if (seeded[0] == defaults.length) {
                                loadMenus();
                            }
                        }
                    });
        }
    }

    private void loadMenus() {
        if (getContext() == null) return;
        menu_container.removeAllViews();

        db.collection("menus")
                .orderBy("order")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String categoryId = doc.getId();
                            String categoryName = doc.getString("name");
                            if (categoryName == null) continue;
                            addCategorySection(categoryId, categoryName);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getContext(), "Failed to load menus: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addCategorySection(String categoryId, String categoryName) {
        if (getContext() == null) return;
        int dp = (int) getResources().getDisplayMetrics().density;

        // Category card — rounded dark with border
        LinearLayout categoryCard = new LinearLayout(getContext());
        categoryCard.setOrientation(LinearLayout.VERTICAL);
        categoryCard.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        categoryCard.setClipToOutline(true);
        categoryCard.setPadding(24 * dp, 20 * dp, 24 * dp, 20 * dp);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12 * dp);
        categoryCard.setLayoutParams(cardParams);


        LinearLayout headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tv_name = new TextView(getContext());
        tv_name.setText(categoryName);
        tv_name.setTextColor(Color.WHITE);
        tv_name.setTextSize(17);
        tv_name.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv_name.setLayoutParams(nameParams);
        headerRow.addView(tv_name);


        ImageView btn_delete_cat = new ImageView(getContext());
        btn_delete_cat.setImageDrawable(getContext().getDrawable(R.drawable.outline_delete_24));
        btn_delete_cat.setColorFilter(Color.parseColor("#F87171"));
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(90, 90);
        btn_delete_cat.setLayoutParams(delParams);
        btn_delete_cat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDeleteCategory(categoryId, categoryName, categoryCard);
            }
        });
        headerRow.addView(btn_delete_cat);
        categoryCard.addView(headerRow);

        View divider = new View(getContext());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(0, 12 * dp, 0, 12 * dp);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(Color.parseColor("#333333"));
        categoryCard.addView(divider);


        LinearLayout options_container = new LinearLayout(getContext());
        options_container.setOrientation(LinearLayout.VERTICAL);
        categoryCard.addView(options_container);


        Button btn_add_option = new Button(getContext());
        btn_add_option.setText(" + Add Option ");
        btn_add_option.setBackground(getContext().getDrawable(R.drawable.zone_physical_bg));
        btn_add_option.setTextColor(Color.parseColor("#4ADE80"));
        btn_add_option.setTextSize(12);
        btn_add_option.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams addOptParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addOptParams.setMargins(0, 8 * dp, 0, 0);
        btn_add_option.setLayoutParams(addOptParams);
        btn_add_option.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddOptionDialog(categoryId, options_container);
            }
        });
        categoryCard.addView(btn_add_option);

        menu_container.addView(categoryCard);
        loadOptions(categoryId, options_container);
    }

    private void loadOptions(String categoryId, LinearLayout options_container) {
        db.collection("menus").document(categoryId)
                .collection("options")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        options_container.removeAllViews();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String optionId = doc.getId();
                            String text = doc.getString("text");
                            if (text == null) continue;
                            addOptionRow(categoryId, optionId, text, options_container);
                        }
                    }
                });
    }

    private void addOptionRow(String categoryId, String optionId, String text,
                              LinearLayout options_container) {
        if (getContext() == null) return;
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 6 * dp, 0, 6 * dp);
        row.setLayoutParams(rowParams);


        TextView tv_text = new TextView(getContext());
        tv_text.setText(text);
        tv_text.setTextColor(Color.parseColor("#CCCCCC"));
        tv_text.setTextSize(14);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv_text.setLayoutParams(textParams);
        row.addView(tv_text);


        ImageView btn_edit = new ImageView(getContext());
        btn_edit.setImageDrawable(getContext().getDrawable(R.drawable.outline_edit_24));
        btn_edit.setColorFilter(Color.parseColor("#6BA4F8"));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(75, 75);
        editParams.setMargins(0, 0, 30, 0);
        btn_edit.setLayoutParams(editParams);
        btn_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditOptionDialog(categoryId, optionId, text, tv_text);
            }
        });
        row.addView(btn_edit);

        ImageView btn_delete = new ImageView(getContext());
        btn_delete.setImageDrawable(getContext().getDrawable(R.drawable.outline_delete_24));
        btn_delete.setColorFilter(Color.parseColor("#F87171"));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(75, 75);
        btn_delete.setLayoutParams(deleteParams);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteOption(categoryId, optionId, row, options_container);
            }
        });
        row.addView(btn_delete);

        options_container.addView(row);
    }

    private void showAddCategoryDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Category name");
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(getContext())
                .setTitle("  Add Category  ")
                .setView(input)
                .setPositiveButton("Add", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> data = new HashMap<>();
                        data.put("name", name);
                        data.put("order", 99);
                        db.collection("menus").add(data)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Toast.makeText(getContext(), "Category added", Toast.LENGTH_SHORT).show();
                                        loadMenus();
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddOptionDialog(String categoryId, LinearLayout options_container) {
        EditText input = new EditText(getContext());
        input.setHint("Option text");
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(getContext())
                .setTitle(" Add Option ")
                .setView(input)
                .setPositiveButton("Add", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) {
                            Toast.makeText(getContext(), "Please enter option text", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> option = new HashMap<>();
                        option.put("text", text);
                        db.collection("menus").document(categoryId)
                                .collection("options").add(option)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference docRef) {
                                        Toast.makeText(getContext(), "Option added", Toast.LENGTH_SHORT).show();
                                        addOptionRow(categoryId, docRef.getId(), text, options_container);
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditOptionDialog(String categoryId, String optionId,
                                      String currentText, TextView tv_text) {
        EditText input = new EditText(getContext());
        input.setText(currentText);
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(getContext())
                .setTitle("Edit Option")
                .setView(input)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String newText = input.getText().toString().trim();
                        if (newText.isEmpty()) {
                            Toast.makeText(getContext(), "Please enter option text", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> update = new HashMap<>();
                        update.put("text", newText);
                        db.collection("menus").document(categoryId)
                                .collection("options").document(optionId)
                                .update(update)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        tv_text.setText(newText);
                                        Toast.makeText(getContext(), "Option updated", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOption(String categoryId, String optionId, LinearLayout row,
                              LinearLayout options_container) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Option")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        db.collection("menus").document(categoryId)
                                .collection("options").document(optionId)
                                .delete()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        options_container.removeView(row);
                                        Toast.makeText(getContext(), "Option deleted", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteCategory(String categoryId, String categoryName,
                                       LinearLayout categoryCard) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Category")
                .setMessage("Delete \"" + categoryName + "\" and all its options?")
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        db.collection("menus").document(categoryId).delete()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        menu_container.removeView(categoryCard);
                                        Toast.makeText(getContext(), "Category deleted", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}