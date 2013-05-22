
package android.media;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

public class VibrationPattern {
    public static final String URI = "content://com.aokp.romcontrol.Vibrations/vibrations";
    public static final String FALLBACK_NAME = "FALLBACK";
    public static final String FALLBACK_PATTERN = "500,1000,1000,1000,1000";

    private static final String TAG = "VibrationPattern";
    private Context mContext = null;
    private String mName;
    private Uri mUri;
    private long[] mPattern;
    private Vibrator mVibrator = null;

    public VibrationPattern(String name, ArrayList<Long> pattern, Context context) {
        mContext = context;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mName = name;
        mPattern = new long[pattern.size()];
        for (int i = 0; i < pattern.size() - 1; i++) {
            mPattern[i] = (pattern.get(i + 1) - pattern.get(i));
        }
    }

    public VibrationPattern(String name, String patternString, Context context) {
        mContext = context;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mName = name;
        setPatternFromString(patternString);
    }

    public VibrationPattern(Uri uri, Context context) {
        if (uri != null) {
            mUri = uri;
            mContext = context;
            mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            try {
                Cursor vibCursor = context.getContentResolver()
                        .query(mUri, null, null, null, null);
                vibCursor.moveToFirst();
                mName = vibCursor.getString(1);
                setPatternFromString(vibCursor.getString(2));
                vibCursor.close();
            } catch (Exception e) {
                Log.d(TAG, "No vibration matching, cloning default vibration");
                VibrationPattern def = new VibrationPattern(Uri.parse(VibrationPattern
                        .getPhoneVibration(context)), context);
                mUri = def.getUri();
                mName = def.getName();
                mPattern = def.getPattern();
            }
        }
    }

    public void setName(String newName) {
        mName = newName;
    }

    public String getName() {
        return mName;
    }

    public long[] getPattern() {
        return mPattern;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    public Uri getUri() {
        return mUri;
    }

    public int getLength() {
        int sum = 0;
        for (int i = 0; i < mPattern.length; i++) {
            sum += (int) mPattern[i];
        }
        return sum;
    }

    public String getPatternString() {
        String result = "";
        for (int i = 0; i < mPattern.length; i++) {
            result = result.concat(Long.toString(mPattern[i]) + ",");
        }
        return result.substring(0, result.length() - 1);
    }

    public void setPatternFromString(String patternString) {
        String[] split = patternString.split(",");
        Log.d(TAG, patternString);
        mPattern = new long[split.length];
        for (int i = 0; i < split.length; i++) {
            mPattern[i] = Long.parseLong(split[i]);
        }
    }

    public void play() {
        if (mVibrator != null) {
            mVibrator.vibrate(mPattern, -1);
        }
    }

    public void stop() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    public static String getPhoneVibration(Context context) {
        ContentResolver cr = context.getContentResolver();
        String defVibration = Settings.System.getString(cr, Settings.System.PHONE_VIBRATION);
        if (defVibration == null) {
            // first time setup
            Settings.System.putString(cr, Settings.System.PHONE_VIBRATION,
                    Settings.System.DEFAULT_VIBRATION_URI.toString());
            return Settings.System.DEFAULT_VIBRATION_URI.toString();
        }
        return defVibration;
    }

    // If something goes wrong with the provider.
    // This is used only by the Phone app's Ringer.java, if the loaded pattern
    // is corrupt.
    public static VibrationPattern getFallbackVibration(Context context) {
        return new VibrationPattern(FALLBACK_NAME, FALLBACK_PATTERN, context);
    }
}
