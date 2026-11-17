package com.lms.hidratateapp;

import android.app.Activity;
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
        setResult(Activity.RESULT_OK, data);
        Toast.makeText(this, R.string.settings_goal_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
