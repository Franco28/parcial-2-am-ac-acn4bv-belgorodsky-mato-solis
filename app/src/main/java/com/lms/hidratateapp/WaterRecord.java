package com.lms.hidratateapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class WaterRecord {
    public final long id;
    public final int amount;
    public final long timestamp;
    public final String time;

    public WaterRecord(long id, int amount, long timestamp) {
        this.id = id;
        this.amount = amount;
        this.timestamp = timestamp;
        this.time = new SimpleDateFormat("HH:mm", new Locale("es", "AR")).format(new Date(timestamp));
    }
}
