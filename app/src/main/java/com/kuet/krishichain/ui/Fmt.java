package com.kuet.krishichain.ui;

import android.content.Context;

import com.kuet.krishichain.R;

/** Small formatting helpers shared by screens. */
public final class Fmt {
    private Fmt() { }

    public static String duration(Context c, int minutes) {
        if (minutes >= 60) {
            return c.getString(R.string.duration_hm, minutes / 60, minutes % 60);
        }
        return c.getString(R.string.duration_min, minutes);
    }
}