package com.sleepstage.detector.helpers;

import java.util.List;

public class SleepPhases {

    public enum SleepStages {
        UNDEFINED, WAKEFULNESS, AROUSAL, NREM1, NREM2, NREM3, REM;
    }

    final int WAKEFULNESS_BPM = 880;
    final int AROUSAL_BPM = 953;
    final int NREM1_BPM = 982;
    final int NREM2_BPM = 978;
    final int NREM3_BPM = 954;
    final int REM_BPM = 940;

    public int calculateMeanRRI (List<Integer> rriResults) {
        int meanRRI=0;

        for (Integer element: rriResults) {
            meanRRI+=element;
        }
        meanRRI /= rriResults.size();
        rriResults.clear();

        return meanRRI;
    }

    public SleepStages calculateSleepPhase (float meanRRI) {
        if(meanRRI < REM_BPM)
            return SleepStages.WAKEFULNESS;
        else if(meanRRI > REM_BPM && meanRRI < NREM3_BPM)
            return SleepStages.AROUSAL;
        else if(meanRRI > NREM2_BPM)
            return SleepStages.NREM1;
        else if(meanRRI > NREM3_BPM && meanRRI < NREM1_BPM)
            return SleepStages.NREM2;
        else if(meanRRI > REM_BPM && meanRRI < NREM2_BPM)
            return SleepStages.NREM3;
        else return  SleepStages.REM;
    }

}
