package com.cloone.carhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lightweight car home screen: big tiles, Android Auto first, all-apps drawer. */
public class MainActivity extends Activity {

    private static final int BG_TOP = 0xFF0B1020;
    private static final int BG_BOTTOM = 0xFF18223F;
    private static final int TEXT = 0xFFF2F5FF;
    private static final int TEXT_DIM = 0xFF9AA6C7;

    /** label, accent colour, candidate packages in preference order. */
    private static final Object[][] TILES = {
        {"Android Auto", 0xFF2F6BFF, new String[]{"com.andrerinas.headunitrevived"}},
        {"Maps",         0xFF1FA463, new String[]{"com.google.android.apps.maps", "com.waze", "com.here.app.maps"}},
        {"Music",        0xFF1DB954, new String[]{"com.spotify.music", "app.rvx.android.apps.youtube.music", "com.jancar.music"}},
        {"Phone",        0xFF8E5CF6, new String[]{"com.jancar.bluetooth"}},
        {"Video",      0xFFE5484D, new String[]{"com.google.android.youtube", "com.jancar.video"}},
        {"Files",         0xFF0EA5C6, new String[]{"com.mediatek.filemanager"}},
        {"Settings",     0xFF64748B, new String[]{"com.jancar.settings", "com.android.settings"}},
        {"All apps",     0xFFF59E0B, null},
    };

    private PackageManager pm;
    private FrameLayout root;
    private GridView drawer;
    private List<ResolveInfo> apps = new ArrayList<ResolveInfo>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        startBluetoothGuard();
        root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM});
        root.setBackground(bg);
        root.addView(buildHome(), new FrameLayout.LayoutParams(-1, -1));
        drawer = buildDrawer();
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The unit force-stops background apps when the car is switched off, and a stopped app gets no
        // boot broadcast. The launcher is always relaunched, so it keeps the guard alive.
        startBluetoothGuard();
    }

    private void startBluetoothGuard() {
        try {
            Intent g = new Intent().setClassName("com.cloone.btguard", "com.cloone.btguard.GuardService");
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(g); else startService(g);
        } catch (Exception ignored) {
            // guard not installed: nothing to do
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Pressing HOME while on the drawer returns to the tiles.
        drawer.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) drawer.setVisibility(View.GONE);
        // On a home screen, back does nothing.
    }

    // ---------- home page ----------

    private View buildHome() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(28), dp(20), dp(28), dp(20));

        // Left: clock + date
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(dp(270), -1));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(78);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        left.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE");
        date.setFormat12Hour("EEEE");
        date.setTextColor(TEXT);
        date.setTextSize(24);
        date.setPadding(dp(4), dp(6), 0, 0);
        left.addView(date);

        TextClock date2 = new TextClock(this);
        date2.setFormat24Hour("d MMMM yyyy");
        date2.setFormat12Hour("d MMMM yyyy");
        date2.setTextColor(TEXT_DIM);
        date2.setTextSize(17);
        date2.setPadding(dp(4), dp(2), 0, 0);
        left.addView(date2);

        // Right: 2 x 4 grid of tiles
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        row.addView(grid, new LinearLayout.LayoutParams(0, -1, 1f));
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1f));
            for (int c = 0; c < 4; c++) {
                Object[] t = TILES[r * 4 + c];
                View tile = buildTile((String) t[0], (Integer) t[1], (String[]) t[2]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(8), dp(8), dp(8), dp(8));
                line.addView(tile, lp);
            }
        }
        return row;
    }

    private View buildTile(final String label, int accent, final String[] pkgs) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{dim(accent, 0.55f), dim(accent, 0.30f)});
        card.setCornerRadius(dp(26));
        card.setStroke(dp(1), dim(accent, 0.9f));
        tile.setBackground(card);

        final Intent launch = pkgs == null ? null : firstLaunchable(pkgs);
        ImageView icon = new ImageView(this);
        Drawable d = null;
        if (launch != null) {
            try { d = pm.getActivityIcon(launch); } catch (Exception ignored) { }
        }
        if (d != null) {
            icon.setImageDrawable(d);
        } else {
            icon.setImageDrawable(initialBadge(label, accent));
        }
        tile.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(TEXT);
        name.setTextSize(19);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(6), dp(12), dp(6), 0);
        name.setSingleLine(true);
        tile.addView(name);

        if (pkgs != null && launch == null) tile.setAlpha(0.45f);
        press(tile);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (pkgs == null) { showDrawer(); return; }
                Intent i = firstLaunchable(pkgs);
                if (i == null) {
                    Toast.makeText(MainActivity.this, label + " is not installed", Toast.LENGTH_SHORT).show();
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open " + label, Toast.LENGTH_SHORT).show();
                }
            }
        });
        return tile;
    }

    // ---------- all apps ----------

    private GridView buildDrawer() {
        GridView g = new GridView(this);
        g.setNumColumns(7);
        g.setBackgroundColor(0xFF101830);
        g.setPadding(dp(24), dp(20), dp(24), dp(20));
        g.setVerticalSpacing(dp(12));
        g.setClipToPadding(false);
        g.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        g.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int p) { return apps.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, ViewGroup parent) {
                LinearLayout cell;
                ImageView icon; TextView name;
                if (v == null) {
                    cell = new LinearLayout(MainActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(dp(4), dp(8), dp(4), dp(8));
                    icon = new ImageView(MainActivity.this);
                    cell.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
                    name = new TextView(MainActivity.this);
                    name.setTextColor(TEXT);
                    name.setTextSize(13);
                    name.setGravity(Gravity.CENTER);
                    name.setSingleLine(true);
                    name.setPadding(0, dp(6), 0, 0);
                    cell.addView(name);
                    cell.setTag(new View[]{icon, name});
                } else {
                    cell = (LinearLayout) v;
                    View[] t = (View[]) cell.getTag();
                    icon = (ImageView) t[0]; name = (TextView) t[1];
                }
                ResolveInfo ri = apps.get(p);
                icon.setImageDrawable(ri.loadIcon(pm));
                name.setText(ri.loadLabel(pm));
                return cell;
            }
        });
        g.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> a, View v, int p, long id) {
                ResolveInfo ri = apps.get(p);
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); drawer.setVisibility(View.GONE); } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open app", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return g;
    }

    private void showDrawer() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        List<ResolveInfo> keep = new ArrayList<ResolveInfo>();
        for (ResolveInfo ri : list) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) keep.add(ri);
        }
        Collections.sort(keep, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        apps = keep;
        ((BaseAdapter) drawer.getAdapter()).notifyDataSetChanged();
        drawer.setVisibility(View.VISIBLE);
    }

    // ---------- helpers ----------

    /** First installed + launchable package from the list, or null. */
    private Intent firstLaunchable(String[] pkgs) {
        for (String p : pkgs) {
            Intent i = pm.getLaunchIntentForPackage(p);
            if (i != null) return i;
            // Vendor apps sometimes have no LAUNCHER category.
            Intent q = new Intent(Intent.ACTION_MAIN).setPackage(p);
            List<ResolveInfo> r = pm.queryIntentActivities(q, 0);
            if (!r.isEmpty()) {
                Intent x = new Intent(Intent.ACTION_MAIN);
                x.setClassName(r.get(0).activityInfo.packageName, r.get(0).activityInfo.name);
                return x;
            }
        }
        return null;
    }

    private Drawable initialBadge(String label, int accent) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(dim(accent, 0.9f));
        return g;
    }

    private void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start(); break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); break;
                }
                return false;
            }
        });
    }

    private static int dim(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
