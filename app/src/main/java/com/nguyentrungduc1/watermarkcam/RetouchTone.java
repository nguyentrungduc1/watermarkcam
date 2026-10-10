package com.nguyentrungduc1.watermarkcam;

final class RetouchTone {
    static float softRange(float value, float low, float high, float feather) {
        return smoothstep((value - low + feather) / feather)
                * smoothstep((high + feather - value) / feather);
    }

    static float detailCorrection(float difference, float surroundingContrast) {
        float edgeProtection = 1 - smoothstep((surroundingContrast - 12) / 35);
        float spot = smoothstep((difference - 5) / 18);
        float weight = (.2f + .65f * spot) * edgeProtection;
        return Math.max(-5, Math.min(12, difference * weight));
    }

    static float brightness(float luminance) {
        float value = Math.max(0, Math.min(1, luminance / 255));
        return 24 * value * (1 - value);
    }

    private static float smoothstep(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
