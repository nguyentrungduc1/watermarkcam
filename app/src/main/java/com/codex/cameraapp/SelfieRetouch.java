package com.codex.cameraapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

final class SelfieRetouch {
    static Bitmap apply(android.content.Context context, Bitmap original, boolean smooth, boolean brighten) {
        float scale = Math.min(1f, 960f / Math.max(original.getWidth(), original.getHeight()));
        Bitmap work = Bitmap.createScaledBitmap(original, Math.round(original.getWidth() * scale),
                Math.round(original.getHeight() * scale), true);
        try {
            int w = work.getWidth(), h = work.getHeight();
            Bitmap mask = SkinSegmentation.confidence(context, work);
            int radius = Math.max(3, Math.min(10, w / 100));
            int[] pixels = new int[w * h], coverage = new int[w * h], delta = new int[w * h];
            work.getPixels(pixels, 0, w, 0, 0, w, h);
            mask.getPixels(coverage, 0, w, 0, 0, w, h);
            mask.recycle();
            java.util.Arrays.fill(delta, Color.rgb(128, 128, 128));
            int[] dx = new int[16], dy = new int[16];
            for (int k = 0; k < 16; k++) {
                dx[k] = Math.round((float) Math.cos(k * Math.PI / 8) * radius);
                dy[k] = Math.round((float) Math.sin(k * Math.PI / 8) * radius);
            }
            // Heal isolated dark spots from surrounding skin; broad edges and facial features stay intact.
            for (int y = radius; y < h - radius; y++) for (int x = radius; x < w - radius; x++) {
                int i = y * w + x;
                float strength = Color.red(coverage[i]) / 255f;
                if (strength < .02f) continue;
                int r = Color.red(pixels[i]), g = Color.green(pixels[i]), b = Color.blue(pixels[i]);
                int sr = 0, sg = 0, sb = 0, low = 255, high = 0, count = 0;
                for (int k = 0; k < 16; k++) {
                    int j = (y + dy[k]) * w + x + dx[k];
                    int c = pixels[j];
                    int luminance = (Color.red(c) + 2 * Color.green(c) + Color.blue(c)) / 4;
                    low = Math.min(low, luminance); high = Math.max(high, luminance);
                    sr += Color.red(c); sg += Color.green(c); sb += Color.blue(c); count++;
                }
                float ar = sr / (float) count, ag = sg / (float) count, ab = sb / (float) count;
                float difference = (ar + 2 * ag + ab - r - 2 * g - b) / 4;
                float change = smooth ? RetouchTone.detailCorrection(difference, high - low) * strength : 0;
                int correctionValue = clamp(128 + change);
                delta[i] = Color.rgb(correctionValue, correctionValue, correctionValue);
            }
            Bitmap correction = Bitmap.createBitmap(delta, w, h, Bitmap.Config.ARGB_8888);
            Bitmap full = Bitmap.createScaledBitmap(correction, original.getWidth(), original.getHeight(), true);
            int[] row = new int[original.getWidth()], adjustment = new int[original.getWidth()];
            for (int y = 0; y < original.getHeight(); y++) {
                original.getPixels(row, 0, row.length, 0, y, row.length, 1);
                full.getPixels(adjustment, 0, row.length, 0, y, row.length, 1);
                for (int x = 0; x < row.length; x++) {
                    float change = Color.red(adjustment[x]) - 128;
                    float luminance = (Color.red(row[x]) + 2 * Color.green(row[x]) + Color.blue(row[x])) / 4f;
                    // One continuous tone curve avoids different exposure around protected facial details.
                    if (brighten) change += RetouchTone.brightness(luminance);
                    row[x] = Color.rgb(clamp(Color.red(row[x]) + change),
                            clamp(Color.green(row[x]) + change), clamp(Color.blue(row[x]) + change));
                }
                original.setPixels(row, 0, row.length, 0, y, row.length, 1);
            }
            if (full != correction) full.recycle();
            correction.recycle();
            Log.i("LucidCamera", "Skin segmentation retouch complete");
        } catch (Exception error) {
            Log.w("LucidCamera", "Face retouch unavailable", error);
        } finally {
            if (work != original) work.recycle();
        }
        return original;
    }

    private static int clamp(float value) { return Math.max(0, Math.min(255, Math.round(value))); }
}
