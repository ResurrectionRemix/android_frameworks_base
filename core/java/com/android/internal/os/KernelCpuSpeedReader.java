/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.os;

import android.text.TextUtils;
import android.system.OsConstants;
import android.util.Slog;

import libcore.io.Libcore;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Reads CPU time of a specific core spent at various frequencies and provides a delta from the
 * last call to {@link #readDelta}. Each line in the proc file has the format:
 *
 * freq time
 *
 * where time is measured in jiffies.
 */
public class KernelCpuSpeedReader {
    private static final String TAG = "KernelCpuSpeedReader";

    private final String mProcFileStats, mProcFileOnline;
    private final long[] mLastSpeedTimes;
    private final long[] mDeltaSpeedTimes;

    // How long a CPU jiffy is in milliseconds.
    private final long mJiffyMillis;

    // The maximum amount of read attempts
    private final int MAX_READ_TRIES = 3;

    private int mFailureCount;

    /**
     * @param cpuNumber The cpu (cpu0, cpu1, etc) whose state to read.
     */
    public KernelCpuSpeedReader(int cpuNumber, int numSpeedSteps) {
        mProcFileStats = String.format("/sys/devices/system/cpu/cpu%d/cpufreq/stats/time_in_state",
                cpuNumber);
        mProcFileOnline = String.format("/sys/devices/system/cpu/cpu%d/online",
                cpuNumber);
        mLastSpeedTimes = new long[numSpeedSteps];
        mDeltaSpeedTimes = new long[numSpeedSteps];
        long jiffyHz = Libcore.os.sysconf(OsConstants._SC_CLK_TCK);
        mJiffyMillis = 1000/jiffyHz;
    }

    /**
     * This checks whether the system is possibly affected
     * by the bug where the stats interface disappears from sysfs.
     * @return the result of this check
     */
    private boolean checkForSysFsBug() {
        try (BufferedReader reader = new BufferedReader(new FileReader(mProcFileOnline))) {
            String line;
            if ((line = reader.readLine()) != null) {
                final int cpuonline = Integer.parseInt(line);
                return cpuonline > 0;
            }
        } catch (IOException | NumberFormatException e) {
            Slog.e(TAG, "Failed to read cpu online status: " + e.getMessage());
        }
        return false;
    }

    /**
     * The returned array is modified in subsequent calls to {@link #readDelta}.
     * @return The time (in milliseconds) spent at different cpu speeds since the last call to
     * {@link #readDelta}.
     */
    public long[] readDelta() {
        // Return if we encountered too many read failures already
        if (mFailureCount >= MAX_READ_TRIES) {
            Arrays.fill(mDeltaSpeedTimes, 0);
            return mDeltaSpeedTimes;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(mProcFileStats))) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(' ');
            String line;
            int speedIndex = 0;
            while (speedIndex < mLastSpeedTimes.length && (line = reader.readLine()) != null) {
                splitter.setString(line);
                Long.parseLong(splitter.next());

                long time = Long.parseLong(splitter.next()) * mJiffyMillis;
                if (time < mLastSpeedTimes[speedIndex]) {
                    // The stats reset when the cpu hotplugged. That means that the time
                    // we read is offset from 0, so the time is the delta.
                    mDeltaSpeedTimes[speedIndex] = time;
                } else {
                    mDeltaSpeedTimes[speedIndex] = time - mLastSpeedTimes[speedIndex];
                }
                mLastSpeedTimes[speedIndex] = time;
                speedIndex++;
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to read cpu-freq: " + e.getMessage());
            Arrays.fill(mDeltaSpeedTimes, 0);
            if (checkForSysFsBug()) {
                // Increment the failure counter based on the detection result
                mFailureCount++;
            }
        }
        return mDeltaSpeedTimes;
    }
}
