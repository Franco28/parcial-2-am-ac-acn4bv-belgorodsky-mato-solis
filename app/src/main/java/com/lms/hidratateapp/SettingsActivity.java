package com.lms.hidratateapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_CURRENT_GOAL_ML = "com.lms.hidratateapp.CURRENT_GOAL_ML";
    public static final String EXTRA_TODAY_CONSUMED_ML = "com.lms.hidratateapp.TODAY_CONSUMED_ML";
    public static final String RESULT_GOAL_ML = "com.lms.hidratateapp.NEW_GOAL_ML";

    private static final int MIN_GOAL_ML = 500;
    private static final int MAX_GOAL_ML = 10000;
    private static final int STEP_ML = 250;

    private final NumberFormat formatter = NumberFormat.getIntegerInstance(new Locale("es", "AR"));
    private TextView tvSettingsGoal;
    private int selectedGoalMl;
    private SwitchCompat remindersSwitch;

    private final ActivityResultLauncher<String> permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    activateReminders();
                } else {
                    remindersSwitch.setChecked(false);
                    HydrationPreferences.saveRemindersEnabled(this, false);
                    ReminderScheduler.cancel(this);
                    Toast.makeText(this, R.string.reminder_permission_denied, Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        selectedGoalMl = getIntent().getIntExtra(EXTRA_CURRENT_GOAL_ML, 2000);
        if (selectedGoalMl < MIN_GOAL_ML || selectedGoalMl > MAX_GOAL_ML) {
            selectedGoalMl = 2000;
        }

        tvSettingsGoal = findViewById(R.id.tvSettingsGoal);
        TextView tvSettingsToday = findViewById(R.id.tvSettingsToday);
        int consumedMl = Math.max(0, getIntent().getIntExtra(EXTRA_TODAY_CONSUMED_ML, 0));
        tvSettingsToday.setText(getString(R.string.settings_today_consumption, formatter.format(consumedMl)));

        Button btnMinus = findViewById(R.id.btnGoalMinus);
        Button btnPlus = findViewById(R.id.btnGoalPlus);
        Button btnSave = findViewById(R.id.btnSaveGoal);
        Button btnBack = findViewById(R.id.btnSettingsBack);

        btnMinus.setOnClickListener(v -> changeGoal(-STEP_ML));
        btnPlus.setOnClickListener(v -> changeGoal(STEP_ML));
        btnSave.setOnClickListener(v -> saveGoal());
        btnBack.setOnClickListener(v -> finish());
        updateGoalText();
        configureReminders();
    }

    private void configureReminders() {
        remindersSwitch = findViewById(R.id.switchReminders);
        Spinner spinner = findViewById(R.id.spinnerReminderInterval);
        Button testButton = findViewById(R.id.btnTestReminder);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.reminder_intervals, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(HydrationPreferences.getReminderInterval(this) == 30 ? 0 : 1);
        remindersSwitch.setChecked(HydrationPreferences.remindersEnabled(this) && ReminderScheduler.canNotify(this));

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int minutes = position == 0 ? 30 : 60;
                HydrationPreferences.saveReminderInterval(SettingsActivity.this, minutes);
                if (HydrationPreferences.remindersEnabled(SettingsActivity.this)) {
                    ReminderScheduler.schedule(SettingsActivity.this);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        remindersSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                HydrationPreferences.saveRemindersEnabled(this, false);
                ReminderScheduler.cancel(this);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                activateReminders();
            }
        });

        testButton.setOnClickListener(v -> {
            if (ReminderNotifier.show(this)) {
                Toast.makeText(this, R.string.reminder_test_sent, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.reminder_permission_denied, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void activateReminders() {
        HydrationPreferences.saveRemindersEnabled(this, true);
        remindersSwitch.setChecked(true);
        ReminderScheduler.schedule(this);
    }

    private void changeGoal(int increment) {
        int updated = selectedGoalMl + increment;
        if (updated < MIN_GOAL_ML || updated > MAX_GOAL_ML) {
            Toast.makeText(this, R.string.settings_goal_limits, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedGoalMl = updated;
        updateGoalText();
    }

    private void updateGoalText() {
        tvSettingsGoal.setText(getString(R.string.settings_goal_label, formatter.format(selectedGoalMl)));
    }

    private void saveGoal() {
        Intent data = new Intent();
        data.putExtra(RESULT_GOAL_ML, selectedGoalMl);
        HydrationPreferences.saveDailyGoal(this, selectedGoalMl);
        setResult(Activity.RESULT_OK, data);
        Toast.makeText(this, R.string.settings_goal_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
