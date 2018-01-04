/*
 * Copyright (c) 2015, Sergii Pylypenko
 *           (c) 2018, Joe Maples
 *           (c) 2018, CarbonROM
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of screen-dimmer-pixel-filter nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.server.smartpixels;

public class Grids {

    public static final int GridSize = 64;
    public static final int GridSideSize = 8;

    public static String[] PatternNames = new String[] {
            "12%",
            "25%",
            "38%",
            "50%",
            "62%",
            "75%",
            "88%",
    };

    public static byte[][] Patterns = new byte[][] {
            {
                    1, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 1, 0,
                    0, 1, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 0, 0,
                    0, 0, 1, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1,
                    0, 0, 0, 0, 1, 0, 0, 0,
            },
            {
                    1, 0, 0, 0, 1, 0, 0, 0,
                    0, 0, 1, 0, 0, 0, 1, 0,
                    0, 1, 0, 0, 0, 1, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 1,
                    1, 0, 0, 0, 1, 0, 0, 0,
                    0, 0, 1, 0, 0, 0, 1, 0,
                    0, 1, 0, 0, 0, 1, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 1,
            },
            {
                    1, 0, 0, 0, 1, 0, 0, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    0, 0, 1, 0, 0, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    1, 0, 0, 0, 1, 0, 0, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    0, 0, 1, 0, 0, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
            },
            {
                    1, 0, 1, 0, 1, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    0, 1, 0, 1, 0, 1, 0, 1,
            },
            {
                    0, 1, 1, 1, 0, 1, 1, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    1, 1, 0, 1, 1, 1, 0, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    0, 1, 1, 1, 0, 1, 1, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
                    1, 1, 0, 1, 1, 1, 0, 1,
                    1, 0, 1, 0, 1, 0, 1, 0,
            },
            {
                    0, 1, 1, 1, 0, 1, 1, 1,
                    1, 1, 0, 1, 1, 1, 0, 1,
                    1, 0, 1, 1, 1, 0, 1, 1,
                    1, 1, 1, 0, 1, 1, 1, 0,
                    0, 1, 1, 1, 0, 1, 1, 1,
                    1, 1, 0, 1, 1, 1, 0, 1,
                    1, 0, 1, 1, 1, 0, 1, 1,
                    1, 1, 1, 0, 1, 1, 1, 0,
            },
            {
                    0, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 0, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 0, 1,
                    1, 0, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 0, 1, 1,
                    1, 1, 0, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 0,
                    1, 1, 1, 1, 0, 1, 1, 1,
            },
    };

    // Indexes to shift screen pattern in both vertical and horizontal directions
    public static byte[] GridShift = new byte[] {
             0,  1,  8,  9,  2,  3, 10, 11,
             4,  5, 12, 13,  6,  7, 14, 15,
            16, 17, 24, 25, 18, 19, 26, 27,
            20, 21, 28, 29, 22, 23, 30, 31,
            32, 33, 40, 41, 34, 35, 42, 43,
            36, 37, 44, 45, 38, 39, 46, 47,
            48, 49, 56, 57, 50, 51, 58, 59,
            52, 53, 60, 61, 54, 55, 62, 63,
    };

    public static int[] ShiftTimeouts = new int[] { // In milliseconds
            15 * 1000,
            30 * 1000,
            60 * 1000,
            2 * 60 * 1000,
            5 * 60 * 1000,
            10 * 60 * 1000,
            20 * 60 * 1000,
            30 * 60 * 1000,
            60 * 60 * 1000,
    };

}
