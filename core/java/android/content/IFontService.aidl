/*
 * Copyright (C) 2018 The Dirty Unicorns Project
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

package android.content;

import android.content.FontInfo;

import java.util.Map;

interface IFontService {
    /**
     * Apply a specified font pack
     * @param info the FontInfo object to apply
     */
    void applyFont(in FontInfo info);

    /**
     * @return current FontInfo
     */
    FontInfo getFontInfo();

    /**
     * @return A Map<String, List<FontInfo>> of all the packages that provides fonts
     *         mapped to a list of all the fonts that package provides
     */
    Map getAllFonts();
}
