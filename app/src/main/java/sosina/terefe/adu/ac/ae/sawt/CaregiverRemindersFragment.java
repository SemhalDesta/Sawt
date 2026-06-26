package sosina.terefe.adu.ac.ae.sawt;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class CaregiverRemindersFragment extends Fragment {

    private LinearLayout reminders_container;
    private TextView tv_patient_subtitle;
    private ReminderDatabaseHelper dbHelper;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String patientName = "";
    private long selectedTriggerMillis = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_caregiver_reminders, container, false);

        reminders_container = v.findViewById(R.id.reminders_container);
        tv_patient_subtitle = v.findViewById(R.id.tv_patient_subtitle);
        Button btn_add = v.findViewById(R.id.btn_add_reminder);

        dbHelper = new ReminderDatabaseHelper(getContext());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        ensureExactAlarmPermission();
        loadPatientName();

        btn_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddDialog();
            }
        });

        return v;
    }

    private void loadPatientName() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        if (!isAdded()) return; // fragment was navigated away from before this returned

                        if (doc.getString("name") != null) {
                            patientName = doc.getString("name");
                            tv_patient_subtitle.setText(patientName + "'s schedule");
                        }
                        loadReminders();
                    }
                });
    }

    private void loadReminders() {
        reminders_container.removeAllViews();
        ArrayList<Reminder> reminders = dbHelper.selectAll();

        if (reminders.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No reminders yet. Tap + Add Reminder to add one.");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setTextSize(14);
            empty.setPadding(0, 20, 0, 0);
            reminders_container.addView(empty);
            return;
        }

        for (Reminder reminder : reminders) {
            addReminderCard(reminder);
        }
    }

    private void addReminderCard(Reminder reminder) {
        int dp = (int) getResources().getDisplayMetrics().density;


        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
        card.setPadding(20 * dp, 20 * dp, 20 * dp, 20 * dp);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12 * dp);
        card.setLayoutParams(cardParams);


        CheckBox checkBox = new CheckBox(getContext());
        checkBox.setChecked(reminder.getIsDone() == 1);
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#888888")));
        card.addView(checkBox);

        LinearLayout textSection = new LinearLayout(getContext());
        textSection.setOrientation(LinearLayout.VERTICAL);
        textSection.setPadding(16 * dp, 0, 0, 0);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textSection.setLayoutParams(textParams);


        TextView tv_title = new TextView(getContext());
        tv_title.setText(reminder.getTitle());
        tv_title.setTextColor(reminder.getIsDone() == 1 ?
                Color.parseColor("#666666") : Color.WHITE);
        tv_title.setTextSize(15);
        tv_title.setTypeface(null, android.graphics.Typeface.BOLD);
        textSection.addView(tv_title);


        TextView tv_detail = new TextView(getContext());
        tv_detail.setText(reminder.getTime() + "  ·  " + reminder.getFrequency());
        tv_detail.setTextColor(Color.parseColor("#888888"));
        tv_detail.setTextSize(12);
        tv_detail.setPadding(0, 4 * dp, 0, 0);
        textSection.addView(tv_detail);

        card.addView(textSection);


        TextView tv_status = new TextView(getContext());
        tv_status.setText(reminder.getIsDone() == 1 ? "Done" : "Upcoming");
        tv_status.setTextColor(reminder.getIsDone() == 1 ?
                Color.parseColor("#4ADE80") : Color.parseColor("#03A9F4"));


        tv_status.setTextSize(11);
        tv_status.setTypeface(null, android.graphics.Typeface.BOLD);
        tv_status.setPadding(16 * dp, 6 * dp, 16 * dp, 6 * dp);
        tv_status.setBackground(getContext().getDrawable(reminder.getIsDone() == 1? R.drawable.zone_physical_bg:R.drawable.zone_emotion_bg));
        card.addView(tv_status);


        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int newDone = checkBox.isChecked() ? 1 : 0;
                dbHelper.updateDone(reminder.getId(), newDone);
                if (newDone == 1) {
                    ReminderScheduler.cancel(getContext(), reminder.getId()); // don't notify for something already marked done
                }
                tv_title.setTextColor(newDone == 1 ?
                        Color.parseColor("#666666") : Color.WHITE);
                tv_status.setText(newDone == 1 ? "Done" : "Upcoming");
                tv_status.setTextColor(newDone == 1 ?
                        Color.parseColor("#4ADE80") : Color.parseColor("#03A9F4"));
                tv_status.setBackground(getContext().getDrawable(R.drawable.zone_card_normal));
            }
        });


        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Reminder")
                        .setMessage("Delete \"" + reminder.getTitle() + "\"?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //dbHelper.deleteById(reminder.getId());
                                dbHelper.deleteById(reminder.getId());
                                ReminderScheduler.cancel(getContext(), reminder.getId());
                                reminders_container.removeView(card);
                                Toast.makeText(getContext(), "Reminder deleted",
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });

        reminders_container.addView(card);
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_reminder, null);

        EditText et_title = dialogView.findViewById(R.id.et_title);
        EditText et_time = dialogView.findViewById(R.id.et_time);
        Spinner spinner = dialogView.findViewById(R.id.spinner_frequency);

        String[] frequencies = {"daily", "weekly", "once"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, frequencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        selectedTriggerMillis = 0;

        et_time.setOnClickListener(view -> {
            java.util.Calendar now = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(getContext(), (datePicker, year, month, day) -> {
                java.util.Calendar picked = java.util.Calendar.getInstance();
                picked.set(year, month, day);

                new android.app.TimePickerDialog(getContext(), (timePicker, hour, minute) -> {
                    picked.set(java.util.Calendar.HOUR_OF_DAY, hour);
                    picked.set(java.util.Calendar.MINUTE, minute);
                    picked.set(java.util.Calendar.SECOND, 0);

                    selectedTriggerMillis = picked.getTimeInMillis();

                    java.text.SimpleDateFormat fmt =
                            new java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault());
                    et_time.setText(fmt.format(picked.getTime()));
                }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), false).show();

            }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH))
                    .show();
        });

        new AlertDialog.Builder(getContext())
                .setTitle("Add Reminder")
                .setView(dialogView)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = et_title.getText().toString().trim();
                        String time = et_time.getText().toString().trim();
                        String frequency = spinner.getSelectedItem().toString();

                        if (title.isEmpty() || time.isEmpty() || selectedTriggerMillis == 0) {
                            Toast.makeText(getContext(), "Please fill in all fields, including date & time",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Reminder reminder = new Reminder(0, title, time, frequency, 0,
                                patientName, selectedTriggerMillis);
                        long newId = dbHelper.insert(reminder);
                        reminder.setId((int) newId);

                        ReminderScheduler.schedule(getContext(), reminder);

                        loadReminders();
                        Toast.makeText(getContext(), "Reminder added", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
    }

    private void ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am = (android.app.AlarmManager)
                    getContext().getSystemService(android.content.Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                android.content.Intent intent =
                        new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }
}