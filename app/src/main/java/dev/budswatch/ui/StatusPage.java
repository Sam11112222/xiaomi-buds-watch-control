package dev.budswatch.ui;

import android.content.Context;

/** A message with optional action rows (connecting / failed / Bluetooth off / permission needed). */
public final class StatusPage extends ListScreen {
    public StatusPage(Context ctx, String heading, String detail) {
        super(ctx);
        title(heading);
        if (detail != null && !detail.isEmpty()) note(detail);
    }

    public StatusPage action(String label, Runnable r) {
        row(label, "", r);
        return this;
    }

    @Override public void onChanged() {}
}
