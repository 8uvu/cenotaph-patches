package app.epitaph.extension.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ceno settings screen, built the way Piko builds its own screen.
 *
 * The layout is one scrolling list of categories, each a run of rows with a
 * title, a summary and either a switch or a current value with a chevron. There
 * is no Save button: like Piko, a row applies the moment it changes, and the
 * screen is rendered straight from {@link CenotaphSettings}, so it cannot list a
 * setting that the Piko screen does not also show.
 */
public class EpitaphSettingsActivity extends Activity {

    private static final int PICK_WALLPAPER = 0xE91A;

    private static final int BG = 0xFF121212;
    private static final int CARD = 0xFF1B1B1B;
    private static final int DIVIDER = 0xFF262626;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int BODY = 0xFF9E9E9E;
    private static final int ACCENT = 0xFFCF9FFF;

    private String pickForThread;

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        readIntent();
        build();

        setContentView(scroll);

        if (pickForThread != null && !pickForThread.isEmpty()) {
            list.post(new Runnable() {
                @Override
                public void run() {
                    pickWallpaper();
                }
            });
        }
    }

    private void readIntent() {
        try {
            Intent intent = getIntent();
            if (intent == null) return;
            pickForThread = intent.getStringExtra("pick_for_thread");
            if (intent.getBooleanExtra("pick_wallpaper_now", false)
                    && (pickForThread == null || pickForThread.isEmpty())) {
                pickForThread = "";
            }
        } catch (Throwable ignored) {}
    }

    private void build() {
        list.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Ceno");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TITLE);
        list.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Instagram patches · everything here is local to this device");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(BODY);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(6);
        list.addView(subtitle, subParams);

        for (CenotaphSettings.Section section : CenotaphSettings.sections()) {
            TextView header = new TextView(this);
            header.setText(section.title);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextColor(ACCENT);
            header.setAllCaps(true);
            header.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = dp(22);
            headerParams.bottomMargin = dp(6);
            list.addView(header, headerParams);

            for (CenotaphSettings.Row row : section.rows) {
                addRow(row);
            }
        }

        TextView footer = new TextView(this);
        footer.setText("Changes take effect the next time Instagram starts.\n"
                + "Cenotaph " + buildName());
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footer.setTextColor(BODY);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(24);
        list.addView(footer, footerParams);
    }

    private String buildName() {
        return "2.11.0";
    }

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    private void addRow(final CenotaphSettings.Row row) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setBackgroundColor(CARD);
        container.setPadding(dp(14), dp(12), dp(14), dp(12));
        container.setClickable(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(row.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(TITLE);
        column.addView(title);

        TextView summary = new TextView(this);
        summary.setText(row.summary);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setTextColor(BODY);
        column.addView(summary);

        container.addView(column);

        switch (row.kind) {
            case CenotaphSettings.SWITCH: {
                final Switch toggle = new Switch(this);
                toggle.setChecked(CenotaphSettings.isOn(this, row));
                // The whole row toggles; a switch that also handles its own
                // click fires twice on some IG themes.
                toggle.setClickable(false);
                toggle.setFocusable(false);
                container.addView(toggle);
                container.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean next = !CenotaphSettings.isOn(EpitaphSettingsActivity.this, row);
                        CenotaphSettings.set(EpitaphSettingsActivity.this, row, next ? "1" : "0");
                        toggle.setChecked(next);
                    }
                });
                break;
            }
            case CenotaphSettings.TEXT:
            case CenotaphSettings.NUMBER: {
                final TextView value = new TextView(this);
                value.setText(shortValue(row, CenotaphSettings.display(this, row)));
                value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                value.setTextColor(ACCENT);
                value.setGravity(Gravity.END);
                value.setMaxWidth(dp(150));
                value.setSingleLine(true);
                container.addView(value);
                container.addView(chevron());
                container.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editText(row, value);
                    }
                });
                break;
            }
            case CenotaphSettings.CHOICE: {
                final TextView value = new TextView(this);
                value.setText(CenotaphSettings.display(this, row));
                value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                value.setTextColor(ACCENT);
                container.addView(value);
                container.addView(chevron());
                container.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        choose(row, value);
                    }
                });
                break;
            }
            case CenotaphSettings.ACTION: {
                container.addView(chevron());
                container.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        run(row);
                    }
                });
                break;
            }
            default:
                break;
        }

        list.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        list.addView(divider, dividerParams);
    }

    private TextView chevron() {
        TextView t = new TextView(this);
        t.setText("\\u203A");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTextColor(BODY);
        t.setPadding(dp(8), 0, 0, 0);
        return t;
    }

    /** Long values are shown truncated; the dialog edits the full string. */
    private String shortValue(CenotaphSettings.Row row, String value) {
        if (value == null || value.isEmpty()) return "—";
        String oneLine = value.replace('\n', ' ').trim();
        if (oneLine.length() <= 26) return oneLine;
        return oneLine.substring(0, 25) + "…";
    }

    private void editText(final CenotaphSettings.Row row, final TextView value) {
        final EditText input = new EditText(this);
        input.setText(CenotaphSettings.display(this, row));
        input.setHint(row.hint);
        input.setInputType(row.kind == CenotaphSettings.NUMBER
                ? InputType.TYPE_CLASS_NUMBER
                : (row.hint != null && row.hint.startsWith("/")
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF666666);

        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String stored = CenotaphSettings.set(EpitaphSettingsActivity.this, row,
                                input.getText().toString());
                        value.setText(row.kind == CenotaphSettings.CHOICE
                                ? stored : shortValue(row, stored));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void choose(final CenotaphSettings.Row row, final TextView value) {
        final String[] options = row.choices.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String stored = CenotaphSettings.set(EpitaphSettingsActivity.this, row,
                                options[which]);
                        value.setText(stored);
                    }
                })
                .show();
    }

    private void run(CenotaphSettings.Row row) {
        if (CenotaphSettings.ACTION_PICK.equals(row.key)) {
            pickForThread = null;
            pickWallpaper();
            return;
        }
        if (CenotaphSettings.ACTION_RESET.equals(row.key)) {
            new AlertDialog.Builder(this)
                    .setTitle("Reset all Ceno settings?")
                    .setMessage("Every value above returns to its default. Piko's own settings are untouched.")
                    .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            CenotaphSettings.resetAll(EpitaphSettingsActivity.this);
                            build();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void pickWallpaper() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_WALLPAPER);
        } catch (Throwable t) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Throwable ignored) {}

        if (pickForThread != null && !pickForThread.isEmpty()) {
            EpitaphPref.setWallpaperEnabled(this, true);
            EpitaphPref.setWallpaperForThread(this, pickForThread, uri.toString());
            Toast.makeText(this, "Wallpaper set for this chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        EpitaphPref.setWallpaperEnabled(this, true);
        EpitaphPref.setWallpaperPath(this, uri.toString());
        Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show();
        build();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
