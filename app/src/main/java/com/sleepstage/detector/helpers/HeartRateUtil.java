package com.sleepstage.detector.helpers;

public class HeartRateUtil {

    public static int extractHeartRate(byte[] bytes) {
        return (int) bytes[1];
    }
}
