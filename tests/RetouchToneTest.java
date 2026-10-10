package com.codex.cameraapp;

public final class RetouchToneTest {
    public static void main(String[] args) {
        check(RetouchTone.detailCorrection(0, 0) == 0, "Flat skin must not change exposure");
        check(RetouchTone.detailCorrection(25, 8) > 8, "Isolated dark spots should soften");
        check(RetouchTone.detailCorrection(25, 60) == 0, "High contrast detail must stay intact");
        check(RetouchTone.brightness(0) == 0 && RetouchTone.brightness(255) == 0, "Preserve black and white");
        for (float value = 0; value < 255; value += .1f) {
            check(Math.abs(RetouchTone.detailCorrection(25, value + .1f)
                    - RetouchTone.detailCorrection(25, value)) < .2f, "No abrupt feature boundary");
            check(Math.abs(RetouchTone.detailCorrection(value + .1f, 20)
                    - RetouchTone.detailCorrection(value, 20)) < .2f, "No blemish threshold step");
        }
        System.out.println("PASS: flat skin, spots, edge protection, tone endpoints, continuous blending");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
