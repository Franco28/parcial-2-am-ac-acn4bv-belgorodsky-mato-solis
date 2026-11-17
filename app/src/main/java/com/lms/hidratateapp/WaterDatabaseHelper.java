package com.lms.hidratateapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class WaterDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "hydration_records.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "water_records";

    public WaterDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "amount_ml INTEGER NOT NULL CHECK(amount_ml > 0), " +
                "recorded_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_water_recorded_at ON " + TABLE_NAME + "(recorded_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No se ha implementado una migración de esquema");
    }

    public long insertRecord(int amountMl, long timestamp) {
        if (amountMl <= 0 || amountMl > 5000) {
            throw new IllegalArgumentException("Cantidad de agua inválida");
        }
        ContentValues values = new ContentValues();
        values.put("amount_ml", amountMl);
        values.put("recorded_at", timestamp);
        long id = getWritableDatabase().insertOrThrow(TABLE_NAME, null, values);
        if (id <= 0) {
            throw new SQLException("No se pudo insertar el registro");
        }
        return id;
    }

    public boolean deleteRecord(long recordId) {
        return getWritableDatabase().delete(TABLE_NAME, "id = ?",
                new String[]{String.valueOf(recordId)}) == 1;
    }

    public List<WaterRecord> findRecordsForDay(long timestamp) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(timestamp);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);

        List<WaterRecord> records = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_NAME,
                new String[]{"id", "amount_ml", "recorded_at"},
                "recorded_at >= ? AND recorded_at < ?",
                new String[]{String.valueOf(start.getTimeInMillis()), String.valueOf(end.getTimeInMillis())},
                null, null, "recorded_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                records.add(new WaterRecord(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2)));
            }
        }
        return records;
    }
}
