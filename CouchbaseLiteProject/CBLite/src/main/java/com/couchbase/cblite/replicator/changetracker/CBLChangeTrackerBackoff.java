package com.couchbase.cblite.replicator.changetracker;

import android.os.Debug;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;

public class CBLChangeTrackerBackoff {

    private static int MAX_SLEEP_MILLISECONDS = 5 * 60 * 1000;  // 5 mins
    // private int currentSleepMilliseconds;
    private int numAttempts = 0;

    public void resetBackoff() {
        numAttempts = 0;
    }

    public int getSleepMilliseconds() {

        int result =  (int) (Math.pow(numAttempts, 2) - 1) / 2;

        result *= 100;

        if (result < MAX_SLEEP_MILLISECONDS) {
            increaseBackoff();
        }

        result = Math.abs(result);

        return result;

    }

    public void sleepAppropriateAmountOfTime() {
        try {
            int sleepMilliseconds = getSleepMilliseconds();
            if (sleepMilliseconds > 0) {
                Log.d(CBLDatabase.TAG, "Sleeping for " + sleepMilliseconds);
                Thread.sleep(sleepMilliseconds);
            }
        } catch (InterruptedException e1) {
        }
    }

    private void increaseBackoff() {
        numAttempts += 1;
    }

}
