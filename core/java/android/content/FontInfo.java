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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public class FontInfo implements Parcelable, Comparable<FontInfo> {
    public static final String DEFAULT_FONT_PACKAGE = "android";
    public static final String DEFAULT_FONT_NAME = "Roboto";
    public static final String DEFAULT_FONT_PATH = "/system/fonts/Roboto-Regular.ttf";

    private static final FontInfo sDefaultInfo = new FontInfo(DEFAULT_FONT_PACKAGE, DEFAULT_FONT_NAME,
            DEFAULT_FONT_PATH);

    public String packageName;
    public String fontName;
    public String previewPath;

    public static FontInfo getDefaultFontInfo() {
        return new FontInfo(sDefaultInfo);
    }

    public static final Parcelable.Creator<FontInfo> CREATOR = new Parcelable.Creator<FontInfo>() {
        public FontInfo createFromParcel(Parcel in) {
            return new FontInfo(in);
        }

        public FontInfo[] newArray(int size) {
            return new FontInfo[size];
        }
    };

    public FontInfo() {
    }

    public FontInfo(String packageName, String fontName, String previewPath) {
        this.packageName = packageName;
        this.fontName = fontName;
        this.previewPath = previewPath;
    }

    public FontInfo(FontInfo from) {
        this.packageName = from.packageName;
        this.fontName = from.fontName;
        this.previewPath = from.previewPath;
    }

    public FontInfo(Parcel in) {
        this.packageName = in.readString();
        this.fontName = in.readString();
        this.previewPath = in.readString();
    }

    public void updateFrom(FontInfo info) {
        this.packageName = info.packageName;
        this.fontName = info.fontName;
        this.previewPath = info.previewPath;
    }

    public String toDelimitedString() {
        return this.packageName + "|"
                + this.fontName + "|"
                + this.previewPath;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.fontName);
        dest.writeString(this.previewPath);
    }

    @Override
    public String toString() {
        return "FontInfo{" +
                "packageName='" + packageName + '\'' +
                ", fontName='" + fontName + '\'' +
                ", previewPath='" + previewPath + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof FontInfo))
            return false;
        FontInfo other = (FontInfo) obj;
        return TextUtils.equals(this.packageName, other.packageName)
                && TextUtils.equals(this.fontName, other.fontName)
                && TextUtils.equals(this.previewPath, other.previewPath);
    }

    @Override
    public int compareTo(FontInfo o) {
        int result = this.fontName.toString().compareToIgnoreCase(o.fontName.toString());
        return result;
    }
}
