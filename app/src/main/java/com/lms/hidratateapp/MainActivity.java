package com.lms.hidratateapp;

import android.content.Intent;
import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private CircularProgressView circularProgress;
    private TextView tvCurrentWater;
    private TextView tvGoal;
    private TextView tvPercentage;
    private EditText etCustomAmount;
    private LinearLayout historyContainer;
    private TextView tvEmptyHistory;

    private static final int DEFAULT_GOAL_ML = 2000;
    private int dailyGoalMl = DEFAULT_GOAL_ML;

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    int newGoal = result.getData().getIntExtra(SettingsActivity.RESULT_GOAL_ML, dailyGoalMl);
                    if (newGoal >= 500 && newGoal <= 10000 && newGoal % 250 == 0) {
                        dailyGoalMl = newGoal;
                        updateUI();
                    }
                }
            }
    );

    private int currentWater = 0;
    private final List<WaterRecord> recordList = new ArrayList<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private WaterDatabaseHelper waterDatabase;
    private String activeDayKey;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        waterDatabase = new WaterDatabaseHelper(this);
        activeDayKey = getDayKey();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvDate = findViewById(R.id.tvDate);
        SimpleDateFormat sdfDate = new SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-AR"));
        String rawDate = sdfDate.format(new Date());
        String finalDate = rawDate.isEmpty() ? "" : rawDate.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-AR")) + rawDate.substring(1);
        tvDate.setText(finalDate);

        dailyGoalMl = HydrationPreferences.getDailyGoal(this);
        circularProgress = findViewById(R.id.circularProgress);
        tvCurrentWater = findViewById(R.id.tvCurrentWater);
        tvGoal = findViewById(R.id.tvGoal);
        tvPercentage = findViewById(R.id.tvPercentage);
        etCustomAmount = findViewById(R.id.etCustomAmount);
        historyContainer = findViewById(R.id.historyContainer);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        Button btn250 = findViewById(R.id.btn250);
        Button btn500 = findViewById(R.id.btn500);
        Button btn750 = findViewById(R.id.btn750);
        Button btnAdd = findViewById(R.id.btnAdd);

        btn250.setOnClickListener(v -> addWater(250));
        btn500.setOnClickListener(v -> addWater(500));
        btn750.setOnClickListener(v -> addWater(750));

        btnAdd.setOnClickListener(v -> {
            String text = etCustomAmount.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.error_empty_amount, Toast.LENGTH_SHORT).show();
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount <= 0) {
                Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount > 5000) {
                Toast.makeText(this, R.string.error_amount_too_large, Toast.LENGTH_SHORT).show();
                return;
            }

            addWater(amount);
            etCustomAmount.setText("");
        });

        ScrollView mainScroll = findViewById(R.id.mainScroll);
        TextView todayNavItem = findViewById(R.id.todayNavItem);
        TextView settingsNavItem = findViewById(R.id.settingsNavItem);

        todayNavItem.setOnClickListener(v -> mainScroll.smoothScrollTo(0, 0));
        settingsNavItem.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_CURRENT_GOAL_ML, dailyGoalMl);
            intent.putExtra(SettingsActivity.EXTRA_TODAY_CONSUMED_ML, currentWater);
            settingsLauncher.launch(intent);
        });

        updateUI();
        loadTodayRecords();
    }

    private void addWater(int amount) {
        final long timestamp = System.currentTimeMillis();
        databaseExecutor.execute(() -> {
            try {
                waterDatabase.insertRecord(amount, timestamp);
                List<WaterRecord> today = waterDatabase.findRecordsForDay(System.currentTimeMillis());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    int previous = currentWater;
                    displayRecords(today);
                    playWaterPourSound();
                    if (previous < dailyGoalMl && currentWater >= dailyGoalMl) {
                        Toast.makeText(this, R.string.goal_reached, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (RuntimeException exception) {
                handleDatabaseError(exception);
            }
        });
    }

    private void removeRecord(WaterRecord record) {
        databaseExecutor.execute(() -> {
            try {
                boolean deleted = waterDatabase.deleteRecord(record.id);
                List<WaterRecord> today = waterDatabase.findRecordsForDay(System.currentTimeMillis());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    displayRecords(today);
                    if (deleted) {
                        playWaterEraseSound();
                        Toast.makeText(this, R.string.record_deleted, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (RuntimeException exception) {
                handleDatabaseError(exception);
            }
        });
    }

    private void loadTodayRecords() {
        databaseExecutor.execute(() -> {
            try {
                List<WaterRecord> today = waterDatabase.findRecordsForDay(System.currentTimeMillis());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) displayRecords(today);
                });
            } catch (RuntimeException exception) {
                handleDatabaseError(exception);
            }
        });
    }

    private void displayRecords(List<WaterRecord> today) {
        recordList.clear();
        recordList.addAll(today);
        long sum = 0L;
        for (WaterRecord record : today) sum += record.amount;
        currentWater = (int) Math.min(sum, Integer.MAX_VALUE);
        updateUI();
        rebuildHistoryViews();
    }

    private void handleDatabaseError(RuntimeException exception) {
        Log.e("HidratateApp", "Error de almacenamiento local", exception);
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, R.string.database_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String getDayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String today = getDayKey();
        if (activeDayKey != null && !today.equals(activeDayKey) && waterDatabase != null) {
            activeDayKey = today;
            loadTodayRecords();
        }
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.execute(() -> waterDatabase.close());
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private void playWaterPourSound() {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.water_pour);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error al reproducir el sonido water_pour", e);
        }
    }

    private void playWaterEraseSound() {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.water_erase);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error al reproducir el sonido water_erase", e);
        }
    }

    private void updateUI() {
        tvCurrentWater.setText(getString(R.string.water_amount, String.valueOf(currentWater)));
        tvGoal.setText(getString(R.string.goal_amount, String.valueOf(dailyGoalMl)));

        int percentage = (int) Math.min(Integer.MAX_VALUE,
                Math.round(((double) currentWater / dailyGoalMl) * 100));
        tvPercentage.setText(getString(R.string.percentage, percentage));

        circularProgress.setProgress(percentage);
    }

    private void rebuildHistoryViews() {
        historyContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (recordList.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            for (WaterRecord record : recordList) {
                View rowView = inflater.inflate(R.layout.item_history, historyContainer, false);
                TextView tvRecordAmount = rowView.findViewById(R.id.tvRecordAmount);
                TextView tvRecordTime = rowView.findViewById(R.id.tvRecordTime);
                ImageView btnDeleteRecord = rowView.findViewById(R.id.btnDeleteRecord);

                tvRecordAmount.setText(getString(R.string.water_amount, String.valueOf(record.amount)));
                tvRecordTime.setText(record.time);

                btnDeleteRecord.setOnClickListener(v -> removeRecord(record));

                historyContainer.addView(rowView);
            }
            TextView countView = new TextView(this);
            countView.setText(getResources().getQuantityString(
                    R.plurals.history_records_count, recordList.size(), recordList.size()));
            countView.setTextColor(getColor(R.color.text_secondary));
            countView.setTextSize(12);
            countView.setPadding(12, 8, 12, 8);
            historyContainer.addView(countView);
        }
    }
}