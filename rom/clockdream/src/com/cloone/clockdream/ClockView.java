package com.cloone.clockdream;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Random;

/**
 * The whole screen saver: pure black, with the time, the day and the date. Nothing else.
 * The group drifts a little every minute so a static image never sits on the same pixels for hours.
 */
final class ClockView extends FrameLayout {

    private static final long DRIFT_EVERY_MS = 60000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final LinearLayout group;
    private final int reach;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            float dx = (random.nextFloat() * 2f - 1f) * reach;
            float dy = (random.nextFloat() * 2f - 1f) * reach * 0.6f;
            group.animate().translationX(dx).translationY(dy).setDuration(2500).start();
            handler.postDelayed(this, DRIFT_EVERY_MS);
        }
    };

    ClockView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        float density = context.getResources().getDisplayMetrics().density;
        reach = (int) (60 * density);

        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER_HORIZONTAL);

        TextClock time = clock(context, "HH:mm", 140, 0xFFFFFFFF, "sans-serif-thin");
        TextClock day = clock(context, "EEEE", 38, 0xFFE6E6E6, "sans-serif-light");
        TextClock date = clock(context, "d MMMM yyyy", 28, 0xFF9A9A9A, "sans-serif-light");
        day.setPadding(0, (int) (6 * density), 0, 0);
        date.setPadding(0, (int) (4 * density), 0, 0);
        group.addView(time);
        group.addView(day);
        group.addView(date);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(group, lp);
    }

    private static TextClock clock(Context c, String format, int sp, int color, String face) {
        TextClock t = new TextClock(c);
        t.setFormat24Hour(format);
        t.setFormat12Hour(format);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    void start() {
        handler.removeCallbacks(drift);
        handler.postDelayed(drift, DRIFT_EVERY_MS);
    }

    void stop() {
        handler.removeCallbacks(drift);
    }
}
