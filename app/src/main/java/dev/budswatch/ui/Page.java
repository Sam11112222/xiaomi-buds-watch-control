package dev.budswatch.ui;

import android.view.View;

/** One screen of the watch UI. */
public interface Page {
    View view();
    void onChanged();
    /** Bezel / crown rotation; delta > 0 = clockwise. @return true if consumed. */
    default boolean onRotary(float delta) { return false; }
}
