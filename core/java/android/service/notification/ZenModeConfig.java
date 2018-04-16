/**
 * Copyright (c) 2014, The Android Open Source Project
 *
 * Licensed under the Apache License,  2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.notification;

import android.app.ActivityManager;
import android.app.NotificationManager.Policy;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted configuration for zen mode.
 *
 * @hide
 */
public class ZenModeConfig implements Parcelable {
    private static String TAG = "ZenModeConfig";

    public static final int SOURCE_ANYONE = 0;
    public static final int SOURCE_CONTACT = 1;
    public static final int SOURCE_STAR = 2;
    public static final int MAX_SOURCE = SOURCE_STAR;
    private static final int DEFAULT_SOURCE = SOURCE_CONTACT;

    public static final int[] ALL_DAYS = { Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY };
    public static final int[] WEEKNIGHT_DAYS = { Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,  };
    public static final int[] WEEKEND_DAYS = { Calendar.SATURDAY, Calendar.SUNDAY };

    public static final int[] MINUTE_BUCKETS = generateMinuteBuckets();
    private static final int SECONDS_MS = 1000;
    private static final int MINUTES_MS = 60 * SECONDS_MS;
    private static final int DAY_MINUTES = 24 * 60;
    private static final int ZERO_VALUE_MS = 10 * SECONDS_MS;

    private static final boolean DEFAULT_ALLOW_CALLS = true;
    private static final boolean DEFAULT_ALLOW_MESSAGES = false;
    private static final boolean DEFAULT_ALLOW_REMINDERS = true;
    private static final boolean DEFAULT_ALLOW_EVENTS = true;
    private static final boolean DEFAULT_ALLOW_REPEAT_CALLERS = false;
    private static final boolean DEFAULT_ALLOW_SCREEN_OFF = true;
    private static final boolean DEFAULT_ALLOW_SCREEN_ON = true;

    private static final int XML_VERSION = 2;
    public static final String ZEN_TAG = "zen";
    private static final String ZEN_ATT_VERSION = "version";
    private static final String ZEN_ATT_USER = "user";
    private static final String ALLOW_TAG = "allow";
    private static final String ALLOW_ATT_CALLS = "calls";
    private static final String ALLOW_ATT_REPEAT_CALLERS = "repeatCallers";
    private static final String ALLOW_ATT_MESSAGES = "messages";
    private static final String ALLOW_ATT_FROM = "from";
    private static final String ALLOW_ATT_CALLS_FROM = "callsFrom";
    private static final String ALLOW_ATT_MESSAGES_FROM = "messagesFrom";
    private static final String ALLOW_ATT_REMINDERS = "reminders";
    private static final String ALLOW_ATT_EVENTS = "events";
    private static final String ALLOW_ATT_SCREEN_OFF = "visualScreenOff";
    private static final String ALLOW_ATT_SCREEN_ON = "visualScreenOn";

    private static final String CONDITION_TAG = "condition";
    private static final String CONDITION_ATT_COMPONENT = "component";
    private static final String CONDITION_ATT_ID = "id";
    private static final String CONDITION_ATT_SUMMARY = "summary";
    private static final String CONDITION_ATT_LINE1 = "line1";
    private static final String CONDITION_ATT_LINE2 = "line2";
    private static final String CONDITION_ATT_ICON = "icon";
    private static final String CONDITION_ATT_STATE = "state";
    private static final String CONDITION_ATT_FLAGS = "flags";

    private static final String MANUAL_TAG = "manual";
    private static final String AUTOMATIC_TAG = "automatic";

    private static final String RULE_ATT_ID = "ruleId";
    private static final String RULE_ATT_ENABLED = "enabled";
    private static final String RULE_ATT_SNOOZING = "snoozing";
    private static final String RULE_ATT_NAME = "name";
    private static final String RULE_ATT_COMPONENT = "component";
    private static final String RULE_ATT_ZEN = "zen";
    private static final String RULE_ATT_CONDITION_ID = "conditionId";
    private static final String RULE_ATT_CREATION_TIME = "creationTime";
    private static final String RULE_ATT_ENABLER = "enabler";

    public boolean allowCalls = DEFAULT_ALLOW_CALLS;
    public boolean allowRepeatCallers = DEFAULT_ALLOW_REPEAT_CALLERS;
    public boolean allowMessages = DEFAULT_ALLOW_MESSAGES;
    public boolean allowReminders = DEFAULT_ALLOW_REMINDERS;
    public boolean allowEvents = DEFAULT_ALLOW_EVENTS;
    public int allowCallsFrom = DEFAULT_SOURCE;
    public int allowMessagesFrom = DEFAULT_SOURCE;
    public int user = UserHandle.USER_SYSTEM;
    public boolean allowWhenScreenOff = DEFAULT_ALLOW_SCREEN_OFF;
    public boolean allowWhenScreenOn = DEFAULT_ALLOW_SCREEN_ON;

    public ZenRule manualRule;
    public ArrayMap<String, ZenRule> automaticRules = new ArrayMap<>();

    public ZenModeConfig() { }

    public ZenModeConfig(Parcel source) {
        allowCalls = source.readInt() == 1;
        allowRepeatCallers = source.readInt() == 1;
        allowMessages = source.readInt() == 1;
        allowReminders = source.readInt() == 1;
        allowEvents = source.readInt() == 1;
        allowCallsFrom = source.readInt();
        allowMessagesFrom = source.readInt();
        user = source.readInt();
        manualRule = source.readParcelable(null);
        final int len = source.readInt();
        if (len > 0) {
            final String[] ids = new String[len];
            final ZenRule[] rules = new ZenRule[len];
            source.readStringArray(ids);
            source.readTypedArray(rules, ZenRule.CREATOR);
            for (int i = 0; i < len; i++) {
                automaticRules.put(ids[i], rules[i]);
            }
        }
        allowWhenScreenOff = source.readInt() == 1;
        allowWhenScreenOn = source.readInt() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(allowCalls ? 1 : 0);
        dest.writeInt(allowRepeatCallers ? 1 : 0);
        dest.writeInt(allowMessages ? 1 : 0);
        dest.writeInt(allowReminders ? 1 : 0);
        dest.writeInt(allowEvents ? 1 : 0);
        dest.writeInt(allowCallsFrom);
        dest.writeInt(allowMessagesFrom);
        dest.writeInt(user);
        dest.writeParcelable(manualRule, 0);
        if (!automaticRules.isEmpty()) {
            final int len = automaticRules.size();
            final String[] ids = new String[len];
            final ZenRule[] rules = new ZenRule[len];
            for (int i = 0; i < len; i++) {
                ids[i] = automaticRules.keyAt(i);
                rules[i] = automaticRules.valueAt(i);
            }
            dest.writeInt(len);
            dest.writeStringArray(ids);
            dest.writeTypedArray(rules, 0);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(allowWhenScreenOff ? 1 : 0);
        dest.writeInt(allowWhenScreenOn ? 1 : 0);
    }

    @Override
    public String toString() {
        return new StringBuilder(ZenModeConfig.class.getSimpleName()).append('[')
                .append("user=").append(user)
                .append(",allowCalls=").append(allowCalls)
                .append(",allowRepeatCallers=").append(allowRepeatCallers)
                .append(",allowMessages=").append(allowMessages)
                .append(",allowCallsFrom=").append(sourceToString(allowCallsFrom))
                .append(",allowMessagesFrom=").append(sourceToString(allowMessagesFrom))
                .append(",allowReminders=").append(allowReminders)
                .append(",allowEvents=").append(allowEvents)
                .append(",allowWhenScreenOff=").append(allowWhenScreenOff)
                .append(",allowWhenScreenOn=").append(allowWhenScreenOn)
                .append(",automaticRules=").append(automaticRules)
                .append(",manualRule=").append(manualRule)
                .append(']').toString();
    }

    private Diff diff(ZenModeConfig to) {
        final Diff d = new Diff();
        if (to == null) {
            return d.addLine("config", "delete");
        }
        if (user != to.user) {
            d.addLine("user", user, to.user);
        }
        if (allowCalls != to.allowCalls) {
            d.addLine("allowCalls", allowCalls, to.allowCalls);
        }
        if (allowRepeatCallers != to.allowRepeatCallers) {
            d.addLine("allowRepeatCallers", allowRepeatCallers, to.allowRepeatCallers);
        }
        if (allowMessages != to.allowMessages) {
            d.addLine("allowMessages", allowMessages, to.allowMessages);
        }
        if (allowCallsFrom != to.allowCallsFrom) {
            d.addLine("allowCallsFrom", allowCallsFrom, to.allowCallsFrom);
        }
        if (allowMessagesFrom != to.allowMessagesFrom) {
            d.addLine("allowMessagesFrom", allowMessagesFrom, to.allowMessagesFrom);
        }
        if (allowReminders != to.allowReminders) {
            d.addLine("allowReminders", allowReminders, to.allowReminders);
        }
        if (allowEvents != to.allowEvents) {
            d.addLine("allowEvents", allowEvents, to.allowEvents);
        }
        if (allowWhenScreenOff != to.allowWhenScreenOff) {
            d.addLine("allowWhenScreenOff", allowWhenScreenOff, to.allowWhenScreenOff);
        }
        if (allowWhenScreenOn != to.allowWhenScreenOn) {
            d.addLine("allowWhenScreenOn", allowWhenScreenOn, to.allowWhenScreenOn);
        }
        final ArraySet<String> allRules = new ArraySet<>();
        addKeys(allRules, automaticRules);
        addKeys(allRules, to.automaticRules);
        final int N = allRules.size();
        for (int i = 0; i < N; i++) {
            final String rule = allRules.valueAt(i);
            final ZenRule fromRule = automaticRules != null ? automaticRules.get(rule) : null;
            final ZenRule toRule = to.automaticRules != null ? to.automaticRules.get(rule) : null;
            ZenRule.appendDiff(d, "automaticRule[" + rule + "]", fromRule, toRule);
        }
        ZenRule.appendDiff(d, "manualRule", manualRule, to.manualRule);
        return d;
    }

    public static Diff diff(ZenModeConfig from, ZenModeConfig to) {
        if (from == null) {
            final Diff d = new Diff();
            if (to != null) {
                d.addLine("config", "insert");
            }
            return d;
        }
        return from.diff(to);
    }

    private static <T> void addKeys(ArraySet<T> set, ArrayMap<T, ?> map) {
        if (map != null) {
            for (int i = 0; i < map.size(); i++) {
                set.add(map.keyAt(i));
            }
        }
    }

    public boolean isValid() {
        if (!isValidManualRule(manualRule)) return false;
        final int N = automaticRules.size();
        for (int i = 0; i < N; i++) {
            if (!isValidAutomaticRule(automaticRules.valueAt(i))) return false;
        }
        return true;
    }

    private static boolean isValidManualRule(ZenRule rule) {
        return rule == null || Global.isValidZenMode(rule.zenMode) && sameCondition(rule);
    }

    private static boolean isValidAutomaticRule(ZenRule rule) {
        return rule != null && !TextUtils.isEmpty(rule.name) && Global.isValidZenMode(rule.zenMode)
                && rule.conditionId != null && sameCondition(rule);
    }

    private static boolean sameCondition(ZenRule rule) {
        if (rule == null) return false;
        if (rule.conditionId == null) {
            return rule.condition == null;
        } else {
            return rule.condition == null || rule.conditionId.equals(rule.condition.id);
        }
    }

    private static int[] generateMinuteBuckets() {
        final int maxHrs = 24;
        final int[] buckets = new int[maxHrs + 3];
        buckets[0] = 15;
        buckets[1] = 30;
        buckets[2] = 45;
        for (int i = 1; i <= maxHrs; i++) {
            buckets[2 + i] = 60 * i;
        }
        return buckets;
    }

    public static String sourceToString(int source) {
        switch (source) {
            case SOURCE_ANYONE:
                return "anyone";
            case SOURCE_CONTACT:
                return "contacts";
            case SOURCE_STAR:
                return "stars";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ZenModeConfig)) return false;
        if (o == this) return true;
        final ZenModeConfig other = (ZenModeConfig) o;
        return other.allowCalls == allowCalls
                && other.allowRepeatCallers == allowRepeatCallers
                && other.allowMessages == allowMessages
                && other.allowCallsFrom == allowCallsFrom
                && other.allowMessagesFrom == allowMessagesFrom
                && other.allowReminders == allowReminders
                && other.allowEvents == allowEvents
                && other.allowWhenScreenOff == allowWhenScreenOff
                && other.allowWhenScreenOn == allowWhenScreenOn
                && other.user == user
                && Objects.equals(other.automaticRules, automaticRules)
                && Objects.equals(other.manualRule, manualRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowCalls, allowRepeatCallers, allowMessages, allowCallsFrom,
                allowMessagesFrom, allowReminders, allowEvents, allowWhenScreenOff,
                allowWhenScreenOn,
                user, automaticRules, manualRule);
    }

    private static String toDayList(int[] days) {
        if (days == null || days.length == 0) return "";
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(days[i]);
        }
        return sb.toString();
    }

    private static int[] tryParseDayList(String dayList, String sep) {
        if (dayList == null) return null;
        final String[] tokens = dayList.split(sep);
        if (tokens.length == 0) return null;
        final int[] rt = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            final int day = tryParseInt(tokens[i], -1);
            if (day == -1) return null;
            rt[i] = day;
        }
        return rt;
    }

    private static int tryParseInt(String value, int defValue) {
        if (TextUtils.isEmpty(value)) return defValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    private static long tryParseLong(String value, long defValue) {
        if (TextUtils.isEmpty(value)) return defValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    public static ZenModeConfig readXml(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int type = parser.getEventType();
        if (type != XmlPullParser.START_TAG) return null;
        String tag = parser.getName();
        if (!ZEN_TAG.equals(tag)) return null;
        final ZenModeConfig rt = new ZenModeConfig();
        rt.user = safeInt(parser, ZEN_ATT_USER, rt.user);
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            tag = parser.getName();
            if (type == XmlPullParser.END_TAG && ZEN_TAG.equals(tag)) {
                return rt;
            }
            if (type == XmlPullParser.START_TAG) {
                if (ALLOW_TAG.equals(tag)) {
                    rt.allowCalls = safeBoolean(parser, ALLOW_ATT_CALLS, false);
                    rt.allowRepeatCallers = safeBoolean(parser, ALLOW_ATT_REPEAT_CALLERS,
                            DEFAULT_ALLOW_REPEAT_CALLERS);
                    rt.allowMessages = safeBoolean(parser, ALLOW_ATT_MESSAGES, false);
                    rt.allowReminders = safeBoolean(parser, ALLOW_ATT_REMINDERS,
                            DEFAULT_ALLOW_REMINDERS);
                    rt.allowEvents = safeBoolean(parser, ALLOW_ATT_EVENTS, DEFAULT_ALLOW_EVENTS);
                    final int from = safeInt(parser, ALLOW_ATT_FROM, -1);
                    final int callsFrom = safeInt(parser, ALLOW_ATT_CALLS_FROM, -1);
                    final int messagesFrom = safeInt(parser, ALLOW_ATT_MESSAGES_FROM, -1);
                    if (isValidSource(callsFrom) && isValidSource(messagesFrom)) {
                        rt.allowCallsFrom = callsFrom;
                        rt.allowMessagesFrom = messagesFrom;
                    } else if (isValidSource(from)) {
                        Slog.i(TAG, "Migrating existing shared 'from': " + sourceToString(from));
                        rt.allowCallsFrom = from;
                        rt.allowMessagesFrom = from;
                    } else {
                        rt.allowCallsFrom = DEFAULT_SOURCE;
                        rt.allowMessagesFrom = DEFAULT_SOURCE;
                    }
                    rt.allowWhenScreenOff =
                            safeBoolean(parser, ALLOW_ATT_SCREEN_OFF, DEFAULT_ALLOW_SCREEN_OFF);
                    rt.allowWhenScreenOn =
                            safeBoolean(parser, ALLOW_ATT_SCREEN_ON, DEFAULT_ALLOW_SCREEN_ON);
                } else if (MANUAL_TAG.equals(tag)) {
                    rt.manualRule = readRuleXml(parser);
                } else if (AUTOMATIC_TAG.equals(tag)) {
                    final String id = parser.getAttributeValue(null, RULE_ATT_ID);
                    final ZenRule automaticRule = readRuleXml(parser);
                    if (id != null && automaticRule != null) {
                        automaticRule.id = id;
                        rt.automaticRules.put(id, automaticRule);
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to reach END_DOCUMENT");
    }

    public void writeXml(XmlSerializer out) throws IOException {
        out.startTag(null, ZEN_TAG);
        out.attribute(null, ZEN_ATT_VERSION, Integer.toString(XML_VERSION));
        out.attribute(null, ZEN_ATT_USER, Integer.toString(user));

        out.startTag(null, ALLOW_TAG);
        out.attribute(null, ALLOW_ATT_CALLS, Boolean.toString(allowCalls));
        out.attribute(null, ALLOW_ATT_REPEAT_CALLERS, Boolean.toString(allowRepeatCallers));
        out.attribute(null, ALLOW_ATT_MESSAGES, Boolean.toString(allowMessages));
        out.attribute(null, ALLOW_ATT_REMINDERS, Boolean.toString(allowReminders));
        out.attribute(null, ALLOW_ATT_EVENTS, Boolean.toString(allowEvents));
        out.attribute(null, ALLOW_ATT_CALLS_FROM, Integer.toString(allowCallsFrom));
        out.attribute(null, ALLOW_ATT_MESSAGES_FROM, Integer.toString(allowMessagesFrom));
        out.attribute(null, ALLOW_ATT_SCREEN_OFF, Boolean.toString(allowWhenScreenOff));
        out.attribute(null, ALLOW_ATT_SCREEN_ON, Boolean.toString(allowWhenScreenOn));
        out.endTag(null, ALLOW_TAG);

        if (manualRule != null) {
            out.startTag(null, MANUAL_TAG);
            writeRuleXml(manualRule, out);
            out.endTag(null, MANUAL_TAG);
        }
        final int N = automaticRules.size();
        for (int i = 0; i < N; i++) {
            final String id = automaticRules.keyAt(i);
            final ZenRule automaticRule = automaticRules.valueAt(i);
            out.startTag(null, AUTOMATIC_TAG);
            out.attribute(null, RULE_ATT_ID, id);
            writeRuleXml(automaticRule, out);
            out.endTag(null, AUTOMATIC_TAG);
        }
        out.endTag(null, ZEN_TAG);
    }

    public static ZenRule readRuleXml(XmlPullParser parser) {
        final ZenRule rt = new ZenRule();
        rt.enabled = safeBoolean(parser, RULE_ATT_ENABLED, true);
        rt.snoozing = safeBoolean(parser, RULE_ATT_SNOOZING, false);
        rt.name = parser.getAttributeValue(null, RULE_ATT_NAME);
        final String zen = parser.getAttributeValue(null, RULE_ATT_ZEN);
        rt.zenMode = tryParseZenMode(zen, -1);
        if (rt.zenMode == -1) {
            Slog.w(TAG, "Bad zen mode in rule xml:" + zen);
            return null;
        }
        rt.conditionId = safeUri(parser, RULE_ATT_CONDITION_ID);
        rt.component = safeComponentName(parser, RULE_ATT_COMPONENT);
        rt.creationTime = safeLong(parser, RULE_ATT_CREATION_TIME, 0);
        rt.enabler = parser.getAttributeValue(null, RULE_ATT_ENABLER);
        rt.condition = readConditionXml(parser);
        return rt;
    }

    public static void writeRuleXml(ZenRule rule, XmlSerializer out) throws IOException {
        out.attribute(null, RULE_ATT_ENABLED, Boolean.toString(rule.enabled));
        out.attribute(null, RULE_ATT_SNOOZING, Boolean.toString(rule.snoozing));
        if (rule.name != null) {
            out.attribute(null, RULE_ATT_NAME, rule.name);
        }
        out.attribute(null, RULE_ATT_ZEN, Integer.toString(rule.zenMode));
        if (rule.component != null) {
            out.attribute(null, RULE_ATT_COMPONENT, rule.component.flattenToString());
        }
        if (rule.conditionId != null) {
            out.attribute(null, RULE_ATT_CONDITION_ID, rule.conditionId.toString());
        }
        out.attribute(null, RULE_ATT_CREATION_TIME, Long.toString(rule.creationTime));
        if (rule.enabler != null) {
            out.attribute(null, RULE_ATT_ENABLER, rule.enabler);
        }
        if (rule.condition != null) {
            writeConditionXml(rule.condition, out);
        }
    }

    public static Condition readConditionXml(XmlPullParser parser) {
        final Uri id = safeUri(parser, CONDITION_ATT_ID);
        if (id == null) return null;
        final String summary = parser.getAttributeValue(null, CONDITION_ATT_SUMMARY);
        final String line1 = parser.getAttributeValue(null, CONDITION_ATT_LINE1);
        final String line2 = parser.getAttributeValue(null, CONDITION_ATT_LINE2);
        final int icon = safeInt(parser, CONDITION_ATT_ICON, -1);
        final int state = safeInt(parser, CONDITION_ATT_STATE, -1);
        final int flags = safeInt(parser, CONDITION_ATT_FLAGS, -1);
        try {
            return new Condition(id, summary, line1, line2, icon, state, flags);
        } catch (IllegalArgumentException e) {
            Slog.w(TAG, "Unable to read condition xml", e);
            return null;
        }
    }

    public static void writeConditionXml(Condition c, XmlSerializer out) throws IOException {
        out.attribute(null, CONDITION_ATT_ID, c.id.toString());
        out.attribute(null, CONDITION_ATT_SUMMARY, c.summary);
        out.attribute(null, CONDITION_ATT_LINE1, c.line1);
        out.attribute(null, CONDITION_ATT_LINE2, c.line2);
        out.attribute(null, CONDITION_ATT_ICON, Integer.toString(c.icon));
        out.attribute(null, CONDITION_ATT_STATE, Integer.toString(c.state));
        out.attribute(null, CONDITION_ATT_FLAGS, Integer.toString(c.flags));
    }

    public static boolean isValidHour(int val) {
        return val >= 0 && val < 24;
    }

    public static boolean isValidMinute(int val) {
        return val >= 0 && val < 60;
    }

    private static boolean isValidSource(int source) {
        return source >= SOURCE_ANYONE && source <= MAX_SOURCE;
    }

    private static boolean safeBoolean(XmlPullParser parser, String att, boolean defValue) {
        final String val = parser.getAttributeValue(null, att);
        return safeBoolean(val, defValue);
    }

    private static boolean safeBoolean(String val, boolean defValue) {
        if (TextUtils.isEmpty(val)) return defValue;
        return Boolean.parseBoolean(val);
    }

    private static int safeInt(XmlPullParser parser, String att, int defValue) {
        final String val = parser.getAttributeValue(null, att);
        return tryParseInt(val, defValue);
    }

    private static ComponentName safeComponentName(XmlPullParser parser, String att) {
        final String val = parser.getAttributeValue(null, att);
        if (TextUtils.isEmpty(val)) return null;
        return ComponentName.unflattenFromString(val);
    }

    private static Uri safeUri(XmlPullParser parser, String att) {
        final String val = parser.getAttributeValue(null, att);
        if (TextUtils.isEmpty(val)) return null;
        return Uri.parse(val);
    }

    private static long safeLong(XmlPullParser parser, String att, long defValue) {
        final String val = parser.getAttributeValue(null, att);
        return tryParseLong(val, defValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public ZenModeConfig copy() {
        final Parcel parcel = Parcel.obtain();
        try {
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new ZenModeConfig(parcel);
        } finally {
            parcel.recycle();
        }
    }

    public static final Parcelable.Creator<ZenModeConfig> CREATOR
            = new Parcelable.Creator<ZenModeConfig>() {
        @Override
        public ZenModeConfig createFromParcel(Parcel source) {
            return new ZenModeConfig(source);
        }

        @Override
        public ZenModeConfig[] newArray(int size) {
            return new ZenModeConfig[size];
        }
    };

    public Policy toNotificationPolicy() {
        int priorityCategories = 0;
        int priorityCallSenders = Policy.PRIORITY_SENDERS_CONTACTS;
        int priorityMessageSenders = Policy.PRIORITY_SENDERS_CONTACTS;
        if (allowCalls) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_CALLS;
        }
        if (allowMessages) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_MESSAGES;
        }
        if (allowEvents) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_EVENTS;
        }
        if (allowReminders) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_REMINDERS;
        }
        if (allowRepeatCallers) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
        }
        int suppressedVisualEffects = 0;
        if (!allowWhenScreenOff) {
            suppressedVisualEffects |= Policy.SUPPRESSED_EFFECT_SCREEN_OFF;
        }
        if (!allowWhenScreenOn) {
            suppressedVisualEffects |= Policy.SUPPRESSED_EFFECT_SCREEN_ON;
        }
        priorityCallSenders = sourceToPrioritySenders(allowCallsFrom, priorityCallSenders);
        priorityMessageSenders = sourceToPrioritySenders(allowMessagesFrom, priorityMessageSenders);
        return new Policy(priorityCategories, priorityCallSenders, priorityMessageSenders,
                suppressedVisualEffects);
    }

    private static int sourceToPrioritySenders(int source, int def) {
        switch (source) {
            case SOURCE_ANYONE: return Policy.PRIORITY_SENDERS_ANY;
            case SOURCE_CONTACT: return Policy.PRIORITY_SENDERS_CONTACTS;
            case SOURCE_STAR: return Policy.PRIORITY_SENDERS_STARRED;
            default: return def;
        }
    }

    private static int prioritySendersToSource(int prioritySenders, int def) {
        switch (prioritySenders) {
            case Policy.PRIORITY_SENDERS_CONTACTS: return SOURCE_CONTACT;
            case Policy.PRIORITY_SENDERS_STARRED: return SOURCE_STAR;
            case Policy.PRIORITY_SENDERS_ANY: return SOURCE_ANYONE;
            default: return def;
        }
    }

    public void applyNotificationPolicy(Policy policy) {
        if (policy == null) return;
        allowCalls = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_CALLS) != 0;
        allowMessages = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_MESSAGES) != 0;
        allowEvents = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_EVENTS) != 0;
        allowReminders = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_REMINDERS) != 0;
        allowRepeatCallers = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_REPEAT_CALLERS)
                != 0;
        allowCallsFrom = prioritySendersToSource(policy.priorityCallSenders, allowCallsFrom);
        allowMessagesFrom = prioritySendersToSource(policy.priorityMessageSenders,
                allowMessagesFrom);
        if (policy.suppressedVisualEffects != Policy.SUPPRESSED_EFFECTS_UNSET) {
            allowWhenScreenOff =
                    (policy.suppressedVisualEffects & Policy.SUPPRESSED_EFFECT_SCREEN_OFF) == 0;
            allowWhenScreenOn =
                    (policy.suppressedVisualEffects & Policy.SUPPRESSED_EFFECT_SCREEN_ON) == 0;
        }
    }

    public static Condition toTimeCondition(Context context, int minutesFromNow, int userHandle) {
        return toTimeCondition(context, minutesFromNow, userHandle, false /*shortVersion*/);
    }

    public static Condition toTimeCondition(Context context, int minutesFromNow, int userHandle,
            boolean shortVersion) {
        final long now = System.currentTimeMillis();
        final long millis = minutesFromNow == 0 ? ZERO_VALUE_MS : minutesFromNow * MINUTES_MS;
        return toTimeCondition(context, now + millis, minutesFromNow, userHandle, shortVersion);
    }

    public static Condition toTimeCondition(Context context, long time, int minutes,
            int userHandle, boolean shortVersion) {
        final int num;
        String summary, line1, line2;
        final CharSequence formattedTime =
                getFormattedTime(context, time, isToday(time), userHandle);
        final Resources res = context.getResources();
        if (minutes < 60) {
            // display as minutes
            num = minutes;
            int summaryResId = shortVersion ? R.plurals.zen_mode_duration_minutes_summary_short
                    : R.plurals.zen_mode_duration_minutes_summary;
            summary = res.getQuantityString(summaryResId, num, num, formattedTime);
            int line1ResId = shortVersion ? R.plurals.zen_mode_duration_minutes_short
                    : R.plurals.zen_mode_duration_minutes;
            line1 = res.getQuantityString(line1ResId, num, num, formattedTime);
            line2 = res.getString(R.string.zen_mode_until, formattedTime);
        } else if (minutes < DAY_MINUTES) {
            // display as hours
            num =  Math.round(minutes / 60f);
            int summaryResId = shortVersion ? R.plurals.zen_mode_duration_hours_summary_short
                    : R.plurals.zen_mode_duration_hours_summary;
            summary = res.getQuantityString(summaryResId, num, num, formattedTime);
            int line1ResId = shortVersion ? R.plurals.zen_mode_duration_hours_short
                    : R.plurals.zen_mode_duration_hours;
            line1 = res.getQuantityString(line1ResId, num, num, formattedTime);
            line2 = res.getString(R.string.zen_mode_until, formattedTime);
        } else {
            // display as day/time
            summary = line1 = line2 = res.getString(R.string.zen_mode_until, formattedTime);
        }
        final Uri id = toCountdownConditionId(time, false);
        return new Condition(id, summary, line1, line2, 0, Condition.STATE_TRUE,
                Condition.FLAG_RELEVANT_NOW);
    }

    /**
     * Converts countdown to alarm parameters into a condition with user facing summary
     */
    public static Condition toNextAlarmCondition(Context context, long alarm,
            int userHandle) {
        boolean isSameDay = isToday(alarm);
        final CharSequence formattedTime = getFormattedTime(context, alarm, isSameDay, userHandle);
        final Resources res = context.getResources();
        final String line1 = res.getString(R.string.zen_mode_until, formattedTime);
        final Uri id = toCountdownConditionId(alarm, true);
        return new Condition(id, "", line1, "", 0, Condition.STATE_TRUE,
                Condition.FLAG_RELEVANT_NOW);
    }

    private static CharSequence getFormattedTime(Context context, long time, boolean isSameDay,
            int userHandle) {
        String skeleton = (!isSameDay ? "EEE " : "")
                + (DateFormat.is24HourFormat(context, userHandle) ? "Hm" : "hma");
        final String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return DateFormat.format(pattern, time);
    }

    private static boolean isToday(long time) {
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar endTime = new GregorianCalendar();
        endTime.setTimeInMillis(time);
        if (now.get(Calendar.YEAR) == endTime.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == endTime.get(Calendar.MONTH)
                && now.get(Calendar.DATE) == endTime.get(Calendar.DATE)) {
            return true;
        }
        return false;
    }

    // ==== Built-in system conditions ====

    public static final String SYSTEM_AUTHORITY = "android";

    // ==== Built-in system condition: countdown ====

    public static final String COUNTDOWN_PATH = "countdown";

    public static final String IS_ALARM_PATH = "alarm";

    /**
     * Converts countdown condition parameters into a condition id.
     */
    public static Uri toCountdownConditionId(long time, boolean alarm) {
        return new Uri.Builder().scheme(Condition.SCHEME)
                .authority(SYSTEM_AUTHORITY)
                .appendPath(COUNTDOWN_PATH)
                .appendPath(Long.toString(time))
                .appendPath(IS_ALARM_PATH)
                .appendPath(Boolean.toString(alarm))
                .build();
    }

    public static long tryParseCountdownConditionId(Uri conditionId) {
        if (!Condition.isValidId(conditionId, SYSTEM_AUTHORITY)) return 0;
        if (conditionId.getPathSegments().size() < 2
                || !COUNTDOWN_PATH.equals(conditionId.getPathSegments().get(0))) return 0;
        try {
            return Long.parseLong(conditionId.getPathSegments().get(1));
        } catch (RuntimeException e) {
            Slog.w(TAG, "Error parsing countdown condition: " + conditionId, e);
            return 0;
        }
    }

    /**
     * Returns whether this condition is a countdown condition.
     */
    public static boolean isValidCountdownConditionId(Uri conditionId) {
        return tryParseCountdownConditionId(conditionId) != 0;
    }

    /**
     * Returns whether this condition is a countdown to an alarm.
     */
    public static boolean isValidCountdownToAlarmConditionId(Uri conditionId) {
        if (tryParseCountdownConditionId(conditionId) != 0) {
            if (conditionId.getPathSegments().size() < 4
                    || !IS_ALARM_PATH.equals(conditionId.getPathSegments().get(2))) {
                return false;
            }
            try {
                return Boolean.parseBoolean(conditionId.getPathSegments().get(3));
            } catch (RuntimeException e) {
                Slog.w(TAG, "Error parsing countdown alarm condition: " + conditionId, e);
                return false;
            }
        }
        return false;
    }

    // ==== Built-in system condition: schedule ====

    public static final String SCHEDULE_PATH = "schedule";

    public static Uri toScheduleConditionId(ScheduleInfo schedule) {
        return new Uri.Builder().scheme(Condition.SCHEME)
                .authority(SYSTEM_AUTHORITY)
                .appendPath(SCHEDULE_PATH)
                .appendQueryParameter("days", toDayList(schedule.days))
                .appendQueryParameter("start", schedule.startHour + "." + schedule.startMinute)
                .appendQueryParameter("end", schedule.endHour + "." + schedule.endMinute)
                .appendQueryParameter("exitAtAlarm", String.valueOf(schedule.exitAtAlarm))
                .build();
    }

    public static boolean isValidScheduleConditionId(Uri conditionId) {
        return tryParseScheduleConditionId(conditionId) != null;
    }

    public static ScheduleInfo tryParseScheduleConditionId(Uri conditionId) {
        final boolean isSchedule =  conditionId != null
                && conditionId.getScheme().equals(Condition.SCHEME)
                && conditionId.getAuthority().equals(ZenModeConfig.SYSTEM_AUTHORITY)
                && conditionId.getPathSegments().size() == 1
                && conditionId.getPathSegments().get(0).equals(ZenModeConfig.SCHEDULE_PATH);
        if (!isSchedule) return null;
        final int[] start = tryParseHourAndMinute(conditionId.getQueryParameter("start"));
        final int[] end = tryParseHourAndMinute(conditionId.getQueryParameter("end"));
        if (start == null || end == null) return null;
        final ScheduleInfo rt = new ScheduleInfo();
        rt.days = tryParseDayList(conditionId.getQueryParameter("days"), "\\.");
        rt.startHour = start[0];
        rt.startMinute = start[1];
        rt.endHour = end[0];
        rt.endMinute = end[1];
        rt.exitAtAlarm = safeBoolean(conditionId.getQueryParameter("exitAtAlarm"), false);
        return rt;
    }

    public static ComponentName getScheduleConditionProvider() {
        return new ComponentName(SYSTEM_AUTHORITY, "ScheduleConditionProvider");
    }

    public static class ScheduleInfo {
        public int[] days;
        public int startHour;
        public int startMinute;
        public int endHour;
        public int endMinute;
        public boolean exitAtAlarm;
        public long nextAlarm;

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ScheduleInfo)) return false;
            final ScheduleInfo other = (ScheduleInfo) o;
            return toDayList(days).equals(toDayList(other.days))
                    && startHour == other.startHour
                    && startMinute == other.startMinute
                    && endHour == other.endHour
                    && endMinute == other.endMinute
                    && exitAtAlarm == other.exitAtAlarm;
        }

        public ScheduleInfo copy() {
            final ScheduleInfo rt = new ScheduleInfo();
            if (days != null) {
                rt.days = new int[days.length];
                System.arraycopy(days, 0, rt.days, 0, days.length);
            }
            rt.startHour = startHour;
            rt.startMinute = startMinute;
            rt.endHour = endHour;
            rt.endMinute = endMinute;
            rt.exitAtAlarm = exitAtAlarm;
            rt.nextAlarm = nextAlarm;
            return rt;
        }

        @Override
        public String toString() {
            return "ScheduleInfo{" +
                    "days=" + Arrays.toString(days) +
                    ", startHour=" + startHour +
                    ", startMinute=" + startMinute +
                    ", endHour=" + endHour +
                    ", endMinute=" + endMinute +
                    ", exitAtAlarm=" + exitAtAlarm +
                    ", nextAlarm=" + ts(nextAlarm) +
                    '}';
        }

        protected static String ts(long time) {
            return new Date(time) + " (" + time + ")";
        }
    }

    // ==== Built-in system condition: event ====

    public static final String EVENT_PATH = "event";

    public static Uri toEventConditionId(EventInfo event) {
        return new Uri.Builder().scheme(Condition.SCHEME)
                .authority(SYSTEM_AUTHORITY)
                .appendPath(EVENT_PATH)
                .appendQueryParameter("userId", Long.toString(event.userId))
                .appendQueryParameter("calendar", event.calendar != null ? event.calendar : "")
                .appendQueryParameter("reply", Integer.toString(event.reply))
                .build();
    }

    public static boolean isValidEventConditionId(Uri conditionId) {
        return tryParseEventConditionId(conditionId) != null;
    }

    public static EventInfo tryParseEventConditionId(Uri conditionId) {
        final boolean isEvent = conditionId != null
                && conditionId.getScheme().equals(Condition.SCHEME)
                && conditionId.getAuthority().equals(ZenModeConfig.SYSTEM_AUTHORITY)
                && conditionId.getPathSegments().size() == 1
                && conditionId.getPathSegments().get(0).equals(EVENT_PATH);
        if (!isEvent) return null;
        final EventInfo rt = new EventInfo();
        rt.userId = tryParseInt(conditionId.getQueryParameter("userId"), UserHandle.USER_NULL);
        rt.calendar = conditionId.getQueryParameter("calendar");
        if (TextUtils.isEmpty(rt.calendar) || tryParseLong(rt.calendar, -1L) != -1L) {
            rt.calendar = null;
        }
        rt.reply = tryParseInt(conditionId.getQueryParameter("reply"), 0);
        return rt;
    }

    public static ComponentName getEventConditionProvider() {
        return new ComponentName(SYSTEM_AUTHORITY, "EventConditionProvider");
    }

    public static class EventInfo {
        public static final int REPLY_ANY_EXCEPT_NO = 0;
        public static final int REPLY_YES_OR_MAYBE = 1;
        public static final int REPLY_YES = 2;

        public int userId = UserHandle.USER_NULL;  // USER_NULL = unspecified - use current user
        public String calendar;  // CalendarContract.Calendars.OWNER_ACCOUNT, or null for any
        public int reply;

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EventInfo)) return false;
            final EventInfo other = (EventInfo) o;
            return userId == other.userId
                    && Objects.equals(calendar, other.calendar)
                    && reply == other.reply;
        }

        public EventInfo copy() {
            final EventInfo rt = new EventInfo();
            rt.userId = userId;
            rt.calendar = calendar;
            rt.reply = reply;
            return rt;
        }

        public static int resolveUserId(int userId) {
            return userId == UserHandle.USER_NULL ? ActivityManager.getCurrentUser() : userId;
        }
    }

    // ==== End built-in system conditions ====

    private static int[] tryParseHourAndMinute(String value) {
        if (TextUtils.isEmpty(value)) return null;
        final int i = value.indexOf('.');
        if (i < 1 || i >= value.length() - 1) return null;
        final int hour = tryParseInt(value.substring(0, i), -1);
        final int minute = tryParseInt(value.substring(i + 1), -1);
        return isValidHour(hour) && isValidMinute(minute) ? new int[] { hour, minute } : null;
    }

    private static int tryParseZenMode(String value, int defValue) {
        final int rt = tryParseInt(value, defValue);
        return Global.isValidZenMode(rt) ? rt : defValue;
    }

    public static String newRuleId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String getOwnerCaption(Context context, String owner) {
        final PackageManager pm = context.getPackageManager();
        try {
            final ApplicationInfo info = pm.getApplicationInfo(owner, 0);
            if (info != null) {
                final CharSequence seq = info.loadLabel(pm);
                if (seq != null) {
                    final String str = seq.toString().trim();
                    if (str.length() > 0) {
                        return str;
                    }
                }
            }
        } catch (Throwable e) {
            Slog.w(TAG, "Error loading owner caption", e);
        }
        return "";
    }

    public static String getConditionSummary(Context context, ZenModeConfig config,
            int userHandle, boolean shortVersion) {
        return getConditionLine(context, config, userHandle, false /*useLine1*/, shortVersion);
    }

    private static String getConditionLine(Context context, ZenModeConfig config,
            int userHandle, boolean useLine1, boolean shortVersion) {
        if (config == null) return "";
        String summary = "";
        if (config.manualRule != null) {
            final Uri id = config.manualRule.conditionId;
            if (config.manualRule.enabler != null) {
                summary = getOwnerCaption(context, config.manualRule.enabler);
            } else {
                if (id == null) {
                    summary = context.getString(com.android.internal.R.string.zen_mode_forever);
                } else {
                    final long time = tryParseCountdownConditionId(id);
                    Condition c = config.manualRule.condition;
                    if (time > 0) {
                        final long now = System.currentTimeMillis();
                        final long span = time - now;
                        c = toTimeCondition(context, time, Math.round(span / (float) MINUTES_MS),
                                userHandle, shortVersion);
                    }
                    final String rt = c == null ? "" : useLine1 ? c.line1 : c.summary;
                    summary = TextUtils.isEmpty(rt) ? "" : rt;
                }
            }
        }
        for (ZenRule automaticRule : config.automaticRules.values()) {
            if (automaticRule.isAutomaticActive()) {
                if (summary.isEmpty()) {
                    summary = automaticRule.name;
                } else {
                    summary = context.getResources()
                            .getString(R.string.zen_mode_rule_name_combination, summary,
                                    automaticRule.name);
                }

            }
        }
        return summary;
    }

    public static class ZenRule implements Parcelable {
        public boolean enabled;
        public boolean snoozing;         // user manually disabled this instance
        public String name;              // required for automatic
        public int zenMode;
        public Uri conditionId;          // required for automatic
        public Condition condition;      // optional
        public ComponentName component;  // optional
        public String id;                // required for automatic (unique)
        public long creationTime;        // required for automatic
        public String enabler;          // package name, only used for manual rules.

        public ZenRule() { }

        public ZenRule(Parcel source) {
            enabled = source.readInt() == 1;
            snoozing = source.readInt() == 1;
            if (source.readInt() == 1) {
                name = source.readString();
            }
            zenMode = source.readInt();
            conditionId = source.readParcelable(null);
            condition = source.readParcelable(null);
            component = source.readParcelable(null);
            if (source.readInt() == 1) {
                id = source.readString();
            }
            creationTime = source.readLong();
            if (source.readInt() == 1) {
                enabler = source.readString();
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(enabled ? 1 : 0);
            dest.writeInt(snoozing ? 1 : 0);
            if (name != null) {
                dest.writeInt(1);
                dest.writeString(name);
            } else {
                dest.writeInt(0);
            }
            dest.writeInt(zenMode);
            dest.writeParcelable(conditionId, 0);
            dest.writeParcelable(condition, 0);
            dest.writeParcelable(component, 0);
            if (id != null) {
                dest.writeInt(1);
                dest.writeString(id);
            } else {
                dest.writeInt(0);
            }
            dest.writeLong(creationTime);
            if (enabler != null) {
                dest.writeInt(1);
                dest.writeString(enabler);
            } else {
                dest.writeInt(0);
            }
        }

        @Override
        public String toString() {
            return new StringBuilder(ZenRule.class.getSimpleName()).append('[')
                    .append("enabled=").append(enabled)
                    .append(",snoozing=").append(snoozing)
                    .append(",name=").append(name)
                    .append(",zenMode=").append(Global.zenModeToString(zenMode))
                    .append(",conditionId=").append(conditionId)
                    .append(",condition=").append(condition)
                    .append(",component=").append(component)
                    .append(",id=").append(id)
                    .append(",creationTime=").append(creationTime)
                    .append(",enabler=").append(enabler)
                    .append(']').toString();
        }

        private static void appendDiff(Diff d, String item, ZenRule from, ZenRule to) {
            if (d == null) return;
            if (from == null) {
                if (to != null) {
                    d.addLine(item, "insert");
                }
                return;
            }
            from.appendDiff(d, item, to);
        }

        private void appendDiff(Diff d, String item, ZenRule to) {
            if (to == null) {
                d.addLine(item, "delete");
                return;
            }
            if (enabled != to.enabled) {
                d.addLine(item, "enabled", enabled, to.enabled);
            }
            if (snoozing != to.snoozing) {
                d.addLine(item, "snoozing", snoozing, to.snoozing);
            }
            if (!Objects.equals(name, to.name)) {
                d.addLine(item, "name", name, to.name);
            }
            if (zenMode != to.zenMode) {
                d.addLine(item, "zenMode", zenMode, to.zenMode);
            }
            if (!Objects.equals(conditionId, to.conditionId)) {
                d.addLine(item, "conditionId", conditionId, to.conditionId);
            }
            if (!Objects.equals(condition, to.condition)) {
                d.addLine(item, "condition", condition, to.condition);
            }
            if (!Objects.equals(component, to.component)) {
                d.addLine(item, "component", component, to.component);
            }
            if (!Objects.equals(id, to.id)) {
                d.addLine(item, "id", id, to.id);
            }
            if (creationTime != to.creationTime) {
                d.addLine(item, "creationTime", creationTime, to.creationTime);
            }
            if (enabler != to.enabler) {
                d.addLine(item, "enabler", enabler, to.enabler);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ZenRule)) return false;
            if (o == this) return true;
            final ZenRule other = (ZenRule) o;
            return other.enabled == enabled
                    && other.snoozing == snoozing
                    && Objects.equals(other.name, name)
                    && other.zenMode == zenMode
                    && Objects.equals(other.conditionId, conditionId)
                    && Objects.equals(other.condition, condition)
                    && Objects.equals(other.component, component)
                    && Objects.equals(other.id, id)
                    && other.creationTime == creationTime
                    && Objects.equals(other.enabler, enabler);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, snoozing, name, zenMode, conditionId, condition,
                    component, id, creationTime, enabler);
        }

        public boolean isAutomaticActive() {
            return enabled && !snoozing && component != null && isTrueOrUnknown();
        }

        public boolean isTrueOrUnknown() {
            return condition != null && (condition.state == Condition.STATE_TRUE
                    || condition.state == Condition.STATE_UNKNOWN);
        }

        public static final Parcelable.Creator<ZenRule> CREATOR
                = new Parcelable.Creator<ZenRule>() {
            @Override
            public ZenRule createFromParcel(Parcel source) {
                return new ZenRule(source);
            }
            @Override
            public ZenRule[] newArray(int size) {
                return new ZenRule[size];
            }
        };
    }

    public static class Diff {
        private final ArrayList<String> lines = new ArrayList<>();

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Diff[");
            final int N = lines.size();
            for (int i = 0; i < N; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(lines.get(i));
            }
            return sb.append(']').toString();
        }

        private Diff addLine(String item, String action) {
            lines.add(item + ":" + action);
            return this;
        }

        public Diff addLine(String item, String subitem, Object from, Object to) {
            return addLine(item + "." + subitem, from, to);
        }

        public Diff addLine(String item, Object from, Object to) {
            return addLine(item, from + "->" + to);
        }
    }

}
