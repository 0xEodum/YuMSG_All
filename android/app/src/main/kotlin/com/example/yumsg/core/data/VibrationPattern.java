package com.example.yumsg.core.data;

public class VibrationPattern {
    private long[] pattern;
    private boolean repeat;

    public VibrationPattern(long[] pattern, boolean repeat) {
        this.pattern = pattern;
        this.repeat = repeat;
    }

    public long[] getPattern() { return pattern; }
    public boolean isRepeat() { return repeat; }
}
