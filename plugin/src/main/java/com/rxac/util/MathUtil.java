package com.rxac.util;

import java.util.Collection;

/** Small numerical helpers used across checks. */
public final class MathUtil {

    private MathUtil() {}

    public static double hypot2d(double a, double b) {
        return Math.sqrt(a * a + b * b);
    }

    /** Population standard deviation. */
    public static double stddev(Collection<? extends Number> values) {
        int n = values.size();
        if (n < 2) return 0;
        double mean = 0;
        for (Number v : values) mean += v.doubleValue();
        mean /= n;
        double var = 0;
        for (Number v : values) {
            double d = v.doubleValue() - mean;
            var += d * d;
        }
        return Math.sqrt(var / n);
    }

    public static double mean(Collection<? extends Number> values) {
        if (values.isEmpty()) return 0;
        double sum = 0;
        for (Number v : values) sum += v.doubleValue();
        return sum / values.size();
    }

    /**
     * Greatest common divisor for floating rotation deltas. Legitimate mouse
     * input is quantized by sensitivity; the deltas share a common divisor.
     * Aimbots that set angles directly break this quantization, so a tiny or
     * erratic GCD across samples is a strong signal.
     */
    public static double gcd(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b > 1.0E-4) {
            double t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /** Wrap an angle delta into [-180, 180]. */
    public static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }
}
