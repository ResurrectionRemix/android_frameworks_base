package com.android.systemui.recents.misc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class IconPackHelper {
    static final String ICON_MASK_TAG = "iconmask";
    static final String ICON_BACK_TAG = "iconback";
    static final String ICON_UPON_TAG = "iconupon";
    static final String ICON_SCALE_TAG = "scale";

    // Holds package/class -> drawable
    private Map<String, String> mIconPackResources;
    private Context mContext;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;
    private Drawable mIconUpon, mIconMask;
    private List<Drawable> mIconBackList;
    private List<String> mIconBackStrings;
    private float mIconScale;
    private String mCurrentIconPack = "";
    private boolean mLoading;

    private static IconPackHelper sInstance;

    public static IconPackHelper getInstance(Context context) {
        if (sInstance == null){
            sInstance = new IconPackHelper();
        }
        sInstance.setContext(context);
        return sInstance;
    }

    private IconPackHelper() {
        mIconPackResources = new HashMap<String, String>();
        mIconBackList = new ArrayList<Drawable>();
        mIconBackStrings = new ArrayList<String>();
    }

    private void setContext(Context context) {
        mContext = context;
    }

    private Drawable getDrawableForName(String name) {
        if (isIconPackLoaded()) {
            String item = mIconPackResources.get(name);
            if (!TextUtils.isEmpty(item)) {
                int id = getResourceIdForDrawable(item);
                if (id != 0) {
                    return mLoadedIconPackResource.getDrawable(id);
                }
            }
        }
        return null;
    }

    private Drawable getDrawableWithName(String name) {
        if (isIconPackLoaded()) {
            int id = getResourceIdForDrawable(name);
            if (id != 0) {
                return mLoadedIconPackResource.getDrawable(id);
            }
        }
        return null;
    }

    private void loadResourcesFromXmlParser(XmlPullParser parser,
            Map<String, String> iconPackResources) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_BACK_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        mIconBackStrings.add(parser.getAttributeValue(i));
                    }
                }
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_MASK_TAG) ||
                    parser.getName().equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() > 0) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() > 0) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }

            if (!parser.getName().equalsIgnoreCase("item")) {
                continue;
            }

            String component = parser.getAttributeValue(null, "component");
            String drawable = parser.getAttributeValue(null, "drawable");

            // Validate component/drawable exist
            if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                continue;
            }

            // Validate format/length of component
            if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")
                    || component.length() < 16) {
                continue;
            }

            // Sanitize stored value
            component = component.substring(14, component.length() - 1).toLowerCase();

            ComponentName name = null;
            if (!component.contains("/")) {
                // Package icon reference
                iconPackResources.put(component, drawable);
            } else {
                name = ComponentName.unflattenFromString(component);
                if (name != null) {
                    iconPackResources.put(name.getPackageName(), drawable);
                    iconPackResources.put(name.getPackageName() + "." + name.getClassName(), drawable);
                }
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
    }

    private void loadApplicationResources(Context context,
            Map<String, String> iconPackResources, String packageName) {
        Field[] drawableItems = null;
        try {
            Context appContext = context.createPackageContext(packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            drawableItems = Class.forName(packageName+".R$drawable",
                    true, appContext.getClassLoader()).getFields();
        } catch (Exception e){
            return;
        }

        for (Field f : drawableItems) {
            String name = f.getName();

            String icon = name.toLowerCase();
            name = name.replaceAll("_", ".");

            iconPackResources.put(name, icon);

            int activityIndex = name.lastIndexOf(".");
            if (activityIndex <= 0 || activityIndex == name.length() - 1) {
                continue;
            }

            String iconPackage = name.substring(0, activityIndex);
            if (TextUtils.isEmpty(iconPackage)) {
                continue;
            }
            iconPackResources.put(iconPackage, icon);

            String iconActivity = name.substring(activityIndex + 1);
            if (TextUtils.isEmpty(iconActivity)) {
                continue;
            }
            iconPackResources.put(iconPackage + "." + iconActivity, icon);
        }
    }

    private boolean loadIconPack() {
        String packageName = mCurrentIconPack;
        mIconBackList.clear();
        mIconBackStrings.clear();
        if (TextUtils.isEmpty(packageName)){
            return false;
        }
        mLoading = true;
        mIconPackResources = getIconPackResources(mContext, packageName);
        Resources res = null;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            mLoading = false;
            return false;
        }
        mLoadedIconPackResource = res;
        mLoadedIconPackName = packageName;
        mIconMask = getDrawableForName(ICON_MASK_TAG);
        mIconUpon = getDrawableForName(ICON_UPON_TAG);
        for (int i = 0; i < mIconBackStrings.size(); i++) {
            String backIconString = mIconBackStrings.get(i);
            Drawable backIcon = getDrawableWithName(backIconString);
            if (backIcon != null) {
                mIconBackList.add(backIcon);
            }
        }
        String scale = mIconPackResources.get(ICON_SCALE_TAG);
        if (scale != null) {
            try {
                mIconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
            }
        }
        mLoading = false;
        return true;
    }

    private Map<String, String> getIconPackResources(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        Resources res = null;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        XmlPullParser parser = null;
        InputStream inputStream = null;
        Map<String, String> iconPackResources = new HashMap<String, String>();

        try {
            inputStream = res.getAssets().open("appfilter.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
        } catch (Exception e) {
            // Catch any exception since we want to fall back to parsing the xml/
            // resource in all cases
            int resId = res.getIdentifier("appfilter", "xml", packageName);
            if (resId != 0) {
                parser = res.getXml(resId);
            }
        }

        if (parser != null) {
            try {
                  loadResourcesFromXmlParser(parser, iconPackResources);
                  return iconPackResources;
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Cleanup resources
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        // Application uses a different theme format (most likely launcher pro)
        int arrayId = res.getIdentifier("theme_iconpack", "array", packageName);
        if (arrayId == 0) {
            arrayId = res.getIdentifier("icon_pack", "array", packageName);
        }

        if (arrayId != 0) {
            String[] iconPack = res.getStringArray(arrayId);
            for (String entry : iconPack) {

                if (TextUtils.isEmpty(entry)) {
                    continue;
                }

                String icon = entry.toLowerCase();
                entry = entry.replaceAll("_", ".");

                iconPackResources.put(entry, icon);

                int activityIndex = entry.lastIndexOf(".");
                if (activityIndex <= 0 || activityIndex == entry.length() - 1) {
                    continue;
                }

                String iconPackage = entry.substring(0, activityIndex);
                if (TextUtils.isEmpty(iconPackage)) {
                    continue;
                }
                iconPackResources.put(iconPackage, icon);

                String iconActivity = entry.substring(activityIndex + 1);
                if (TextUtils.isEmpty(iconActivity)) {
                    continue;
                }
                iconPackResources.put(iconPackage + "." + iconActivity, icon);
            }
        } else {
            loadApplicationResources(context, iconPackResources, packageName);
        }
        return iconPackResources;
    }

    public void unloadIconPack() {
        mLoadedIconPackResource = null;
        mLoadedIconPackName = null;
        mIconPackResources = null;
        mIconMask = null;
        mIconBackList.clear();
        mIconBackStrings.clear();
        mIconUpon = null;
        mIconScale = 1f;
    }

    public boolean isIconPackLoaded() {
        return mLoadedIconPackResource != null &&
                mLoadedIconPackName != null &&
                mIconPackResources != null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", mLoadedIconPackName);
        return resId;
    }

    public Resources getIconPackResources() {
        return mLoadedIconPackResource;
    }

    public int getResourceIdForActivityIcon(ActivityInfo info) {
        // TODO since we are loading in background block access until load ready
        if (!isIconPackLoaded() || mLoading){
            return 0;
        }
        String drawable = mIconPackResources.get(info.packageName.toLowerCase()
                + "." + info.name.toLowerCase());
        if (drawable == null) {
            // Icon pack doesn't have an icon for the activity, fallback to package icon
            drawable = mIconPackResources.get(info.packageName.toLowerCase());
            if (drawable == null) {
                return 0;
            }
        }
        return getResourceIdForDrawable(drawable);
    }

    public void updatePrefs(String iconPack) {
        if (iconPack == null || iconPack.equals(mCurrentIconPack)) {
            return;
        }
        mCurrentIconPack = iconPack;
        if (!TextUtils.isEmpty(iconPack) || TextUtils.isEmpty(mCurrentIconPack)){
            unloadIconPack();
        }
        if (!TextUtils.isEmpty(mCurrentIconPack)){
            loadIconPack();
        }
    }
}
