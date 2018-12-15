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

package com.android.server;

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IFontService;
import android.content.FontInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.graphics.FontListParser;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SELinux;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.FontConfig;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import android.widget.Toast;

public class FontService extends IFontService.Stub {
    private static final String TAG = "FontService";
    private static final File SYSTEM_THEME_DIR = new File(Environment.getDataSystemDirectory(),
            "theme");
    private static final File SYSTEM_THEME_FONT_DIR = new File(SYSTEM_THEME_DIR, "fonts");
    private static final File SYSTEM_THEME_CACHE_DIR = new File(SYSTEM_THEME_DIR, "cache");
    private static final File SYSTEM_THEME_PREVIEW_CACHE_DIR = new File(SYSTEM_THEME_DIR,
            "font_previews");
    private static final String FONTS_XML = "fonts.xml";
    private static final String FONT_IDENTIFIER = "custom_rom_font_provider";
    private static final String SUBSTRATUM_INTENT = "projekt.substratum.THEME";

    private Context mContext;
    private FontHandler mFontHandler;
    private HandlerThread mFontWorker;
    private final Map<String, List<FontInfo>> mFontMap = new HashMap<>();
    private final FontInfo mFontInfo = new FontInfo();

    public static class Lifecycle extends SystemService {
        FontService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new FontService(getContext());
            publishBinderService("dufont", mService);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_SYSTEM_SERVICES_READY) {
                String cryptState = SystemProperties.get("vold.decrypt");
                // wait until decrypted if we use FDE or just go one if not (cryptState will be empty then)
                if (TextUtils.isEmpty(cryptState) || cryptState.equals("trigger_restart_framework")) {
                    if (makeDir(SYSTEM_THEME_DIR)) {
                        makeDir(SYSTEM_THEME_PREVIEW_CACHE_DIR);
                        restoreconThemeDir();
                    }
                    mService.sendInitializeFontMapMessage();
                }
            }
        }
    }

    private class FontHandler extends Handler {
        private static final int MESSAGE_INITIALIZE_MAP = 1;
        private static final int MESSAGE_CHANGE_FONT = 2;
        private static final int MESSAGE_PACKAGE_ADDED_OR_UPDATED = 3;
        private static final int MESSAGE_PACKAGE_REMOVED = 4;

        public FontHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            String packageName;
            switch (msg.what) {
                case MESSAGE_INITIALIZE_MAP:
                    initializeFontMap();
                    break;
                case MESSAGE_CHANGE_FONT:
                    final FontInfo info = (FontInfo) msg.obj;
                    applyFontsPriv(info);
                    break;
                case MESSAGE_PACKAGE_ADDED_OR_UPDATED:
                    packageName = (String) msg.obj;
                    boolean isFontProvider = isPackageFontProvider(packageName);
                    if (isFontProvider) {
                        Log.e(TAG, packageName + " was added or updated. Adding or updating fonts");
                        synchronized (mFontMap) {
                            processFontPackage(packageName);
                        }
                    }
                    break;
                case MESSAGE_PACKAGE_REMOVED:
                    packageName = (String) msg.obj;
                    boolean hadFonts = mFontMap.containsKey(packageName);
                    if (hadFonts) {
                        synchronized (mFontMap) {
                            Log.e(TAG,
                                    packageName + " was removed. Clearing fonts from provider map");
                            removeFontPackage(packageName);
                        }
                        // if removed package provided current font, reset to system
                        if (TextUtils.equals(packageName, mFontInfo.packageName)) {
                            Log.e(TAG, packageName
                                    + " provided the current font. Restoring to system font");
                            applyFontsPriv(FontInfo.getDefaultFontInfo());
                        }
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown message " + msg.what);
                    break;
            }
        }
    }

    private class PackageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull
        final Context context, @NonNull
        final Intent intent) {
            final Uri data = intent.getData();
            if (data == null) {
                Slog.e(TAG, "Cannot handle package broadcast with null data");
                return;
            }
            final String packageName = data.getSchemeSpecificPart();

            Message msg;
            switch (intent.getAction()) {
                case ACTION_PACKAGE_ADDED:
                case ACTION_PACKAGE_CHANGED:
                    msg = mFontHandler.obtainMessage(
                            FontHandler.MESSAGE_PACKAGE_ADDED_OR_UPDATED);
                    msg.obj = packageName;
                    mFontHandler.sendMessage(msg);
                    break;
                case ACTION_PACKAGE_REMOVED:
                    msg = mFontHandler.obtainMessage(
                            FontHandler.MESSAGE_PACKAGE_REMOVED);
                    msg.obj = packageName;
                    mFontHandler.sendMessage(msg);
                    break;
                default:
                    break;
            }
        }
    }

    public FontService(Context context) {
        mContext = context;
        mFontWorker = new HandlerThread("FontServiceWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mFontWorker.start();
        mFontHandler = new FontHandler(mFontWorker.getLooper());
        mFontInfo.updateFrom(getCurrentFontInfoFromProvider());
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(ACTION_PACKAGE_ADDED);
        packageFilter.addAction(ACTION_PACKAGE_CHANGED);
        packageFilter.addAction(ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        mContext.registerReceiverAsUser(new PackageReceiver(), UserHandle.ALL,
                packageFilter, null, null);
    }

    @Override
    public void applyFont(FontInfo info) {
        enforceFontService();
        if (info.packageName == null
                || info.fontName == null
                || info.previewPath == null) {
            info.updateFrom(FontInfo.getDefaultFontInfo());
        }
        Log.e(TAG, "applyFonts() packageName = " + info.toString());
        Message msg = mFontHandler.obtainMessage(
                FontHandler.MESSAGE_CHANGE_FONT);
        msg.obj = info;
        mFontHandler.sendMessage(msg);
    }

    @Override
    public FontInfo getFontInfo() {
        enforceFontService();
        FontInfo info = new FontInfo(mFontInfo);
        return info;
    }

    @Override
    public Map<String, List<FontInfo>> getAllFonts() {
        enforceFontService();
        return mFontMap;
    }

    private void sendInitializeFontMapMessage() {
        Message msg = mFontHandler.obtainMessage(
                FontHandler.MESSAGE_INITIALIZE_MAP);
        mFontHandler.sendMessage(msg);
    }

    private void initializeFontMap() {
        List<String> packageList = getInstalledFontPackagesFromProvider();
        for (String pkg : packageList) {
            processFontPackage(pkg);
        }
        Log.e(TAG, " Font map initialized- " + mFontMap.toString());
    }

    private void processFontPackage(String packageName) {
        List<FontInfo> infoList = new ArrayList<FontInfo>();
        Context appContext = getAppContext(packageName);
        AssetManager am = appContext.getAssets();
        List<String> fontZips = getFontsFromPackage(packageName);
        File packageFontPreviewDir = new File(SYSTEM_THEME_PREVIEW_CACHE_DIR, packageName);
        if (packageFontPreviewDir.exists() && packageFontPreviewDir.isDirectory()) {
            FileUtils.deleteContentsAndDir(packageFontPreviewDir);
        }
        makeDir(packageFontPreviewDir);
        // iterate list of fonts package provides
        for (String fontZip : fontZips) {
            // create preview directory for this font
            // for now, just delete and do it again
            // TODO: clean this up
            String sanitizedZipName = sanitizeZipName(fontZip);
            File currentFontPreviewDir = new File(packageFontPreviewDir, sanitizedZipName);
            makeDir(currentFontPreviewDir);

            Log.e(TAG, "CurrentFontPreviewDir absolute path = "
                    + currentFontPreviewDir.getAbsolutePath());

            // copy zip to preview cache
            File fontZipFile = new File(currentFontPreviewDir, fontZip);
            try (InputStream inputStream = am.open("fonts/" + fontZip)) {
                FileUtils.copyToFileOrThrow(inputStream, fontZipFile);
            } catch (IOException e) {
                Log.e(TAG, "There is an exception when trying to copy themed fonts", e);
            }

            // get fonts.xml from zip
            File fontXmlFile = new File(currentFontPreviewDir, FONTS_XML);
            unzipFile(fontZipFile, fontXmlFile, FONTS_XML);
            // TODO: find a appropiate method to use a fallback xml and avoid this
            if (!fontXmlFile.exists()) {
                 Toast.makeText(mContext,mContext.getResources()
                    .getString(com.android.internal.R.string.fontservice_incompatible_font),Toast.LENGTH_LONG).show();
                 return;
            }

            // parse fonts.xml for name of preview typeface
            String fontFileName = getPreviewFontNameFromXml(fontXmlFile,
                    currentFontPreviewDir.getAbsolutePath());

            // extract tff file from zip
            File fontFile = new File(fontFileName);
            unzipFile(fontZipFile, fontFile, fontFile.getName());

            // clean up workspace
            if (fontXmlFile.exists()) {
                fontXmlFile.delete();
            }
            if (fontZipFile.exists()) {
                fontZipFile.delete();
            }

            // create FontInfo and add to list
            FontInfo fontInfo = new FontInfo();
            fontInfo.fontName = sanitizedZipName;
            fontInfo.packageName = packageName;
            fontInfo.previewPath = fontFile.getAbsolutePath();
            infoList.add(fontInfo);
        }
        // add or replace font list
        if (mFontMap.containsKey(packageName)) {
            mFontMap.replace(packageName, infoList);
        } else {
            mFontMap.put(packageName, infoList);
        }

        // update package list in provider
        List<String> packageList = getInstalledFontPackagesFromProvider();
        if (!packageList.contains(packageName)) {
            packageList.add(packageName);
            putFontPackagesIntoProvider(packageList);
        }
        Log.e(TAG, "The new FontInfo map: " + mFontMap.toString());
    }

    private void removeFontPackage(String packageName) {
        if (!mFontMap.containsKey(packageName)) {
            return;
        }
        File packageFontPreviewDir = new File(SYSTEM_THEME_PREVIEW_CACHE_DIR, packageName);
        if (packageFontPreviewDir.exists() && packageFontPreviewDir.isDirectory()) {
            FileUtils.deleteContentsAndDir(packageFontPreviewDir);
        }
        mFontMap.remove(packageName, mFontMap.get(packageName));

        // update package list in provider
        List<String> packageList = getInstalledFontPackagesFromProvider();
        if (packageList.contains(packageName)) {
            packageList.remove(packageName);
            putFontPackagesIntoProvider(packageList);
        }
    }

    private static String getPreviewFontNameFromXml(File xmlFile, String path) {
        FontConfig fontConfig = null;
        try {
            fontConfig = FontListParser.parse(xmlFile, path);
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown parsing fonts.xml! " + e.toString());
            return null;
        }
        if (fontConfig != null) {
            List<FontConfig.Family> families = fontConfig.getFamilies();
            if (families != null) {
                FontConfig.Family family = families.get(0);
                if (family != null) {
                    FontConfig.Font[] fonts = family.getFonts();
                    if (fonts != null && fonts.length > 0) {
                        FontConfig.Font font = fonts[0];
                        if (font != null) {
                            Log.e(TAG, "Font found from parsing fonts.xml! " + font.getFontName());
                            return font.getFontName();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isPackageFontProvider(String packageName) {
        // check if the package res bool is set first
        Context appContext = getAppContext(packageName);
        int id = appContext.getResources().getIdentifier(FONT_IDENTIFIER,
                "bool",
                appContext.getPackageName());
        if (id != 0) {
            return true;
        }

        // now check for Substratum package
        // TODO: why resolve for ALL packages? Just analyze this package
        List<ResolveInfo> subsPackages = new ArrayList<ResolveInfo>();
        PackageManager pm = mContext.getPackageManager();
        Intent i = new Intent(SUBSTRATUM_INTENT);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        subsPackages.addAll(pm.queryIntentActivities(i,
                PackageManager.GET_META_DATA));
        for (ResolveInfo info : subsPackages) {
            if (TextUtils.equals(info.activityInfo.packageName, packageName)) {
                return true;
            }
        }
        // bail out
        return false;
    }

    private List<String> getFontsFromPackage(String packageName) {
        Context appContext = getAppContext(packageName);
        AssetManager am = appContext.getAssets();
        List<String> list = new ArrayList<String>();
        try {
            list.addAll(Arrays.asList(am.list("fonts")));
        } catch (Exception e) {
            Log.e(TAG, appContext.getPackageName() + "did not have a fonts folder!");
        }

        // remove Substratum preview files, only grap zips
        List<String> previews = new ArrayList<String>();
        for (String font : list) {
            if (font.contains("preview") || !font.endsWith(".zip")) {
                previews.add(font);
            }
        }
        list.removeAll(previews);

        Log.e(TAG, packageName + " has the following fonts - " + list.toString());
        return list;
    }

    private void putFontPackagesIntoProvider(List<String> packages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            builder.append(packages.get(i));
            builder.append("|");
        }
        Settings.System.putStringForUser(mContext.getContentResolver(),
                Settings.System.FONT_PACKAGES,
                builder.toString(), UserHandle.USER_CURRENT);
    }

    private List<String> getInstalledFontPackagesFromProvider() {
        String packages = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.FONT_PACKAGES, UserHandle.USER_CURRENT);
        List<String> packageList = new ArrayList<>();
        if (TextUtils.isEmpty(packages)) {
            packageList.addAll(Arrays.asList(mContext.getResources()
                    .getStringArray(com.android.internal.R.array.config_fontPackages)));
        } else {
            packageList.addAll(Arrays.asList(packages.split("\\|")));
        }
        return packageList;
    }

    private void putCurrentFontInfoInProvider(FontInfo fontInfo) {
        Settings.System.putStringForUser(mContext.getContentResolver(), Settings.System.FONT_INFO,
                fontInfo.toDelimitedString(), UserHandle.USER_CURRENT);
    }

    // index 0 is package name, index 1 is font name, index 2 is previewPath
    private FontInfo getCurrentFontInfoFromProvider() {
        String info = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.FONT_INFO, UserHandle.USER_CURRENT);
        FontInfo fontInfo = new FontInfo();
        if (TextUtils.isEmpty(info)) {
            fontInfo.updateFrom(FontInfo.getDefaultFontInfo());
        } else {
            List<String> infoList = Arrays.asList(info.split("\\|"));
            fontInfo.packageName = infoList.get(0);
            fontInfo.fontName = infoList.get(1);
            fontInfo.previewPath = infoList.get(2);
        }
        return fontInfo;
    }

    private Context getAppContext(String packageName) {
        Context ctx = null;
        try {
            ctx = mContext.createPackageContext(packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Failed to get " + packageName + " context");
        }
        return ctx;
    }

    private void applyFontsPriv(FontInfo info) {
        Log.e(TAG, "applyFontsPriv() packageName = " + info.toString());
        final long ident = Binder.clearCallingIdentity();
        try {
            if (info.equals(FontInfo.getDefaultFontInfo())) {
                clearFonts();
            } else {
                copyFonts(info);
            }
            Intent intent = new Intent("com.android.server.ACTION_FONT_CHANGED");
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND);
            mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void copyFonts(FontInfo info) {
        // Prepare local cache dir for font package assembly

        File cacheDir = new File(SYSTEM_THEME_CACHE_DIR, "FontCache");
        if (cacheDir.exists()) {
            FileUtils.deleteContentsAndDir(cacheDir);
        }

        boolean created = cacheDir.mkdirs();
        if (!created) {
            Log.e(TAG, "Could not create cache directory...");
        }

        // Append zip to filename since it is probably removed
        // for list presentation
        String zipFileName = info.fontName;
        if (!zipFileName.endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        // Copy target themed fonts zip to our cache dir
        Context themeContext = getAppContext(info.packageName);
        AssetManager am = themeContext.getAssets();
        File fontZip = new File(cacheDir, zipFileName);
        try (InputStream inputStream = am.open("fonts/" + zipFileName)) {
            FileUtils.copyToFileOrThrow(inputStream, fontZip);
        } catch (IOException e) {
            Log.e(TAG, "There is an exception when trying to copy themed fonts", e);
        }

        // Unzip new fonts and delete zip file, overwriting any system fonts
        unzip(fontZip.getAbsolutePath(), cacheDir.getAbsolutePath());

        boolean deleted = fontZip.delete();
        if (!deleted) {
            Log.e(TAG, "Could not delete ZIP file");
        }

        // Prepare system theme fonts folder and copy new fonts folder from our cache
        FileUtils.deleteContentsAndDir(SYSTEM_THEME_FONT_DIR);
        makeDir(SYSTEM_THEME_FONT_DIR);
        copyDir(cacheDir.getAbsolutePath(), SYSTEM_THEME_FONT_DIR.getAbsolutePath());

        // Let system know it's time for a font change
        FileUtils.deleteContentsAndDir(cacheDir);
        refreshFonts();
        mFontInfo.updateFrom(info);
        putCurrentFontInfoInProvider(mFontInfo);
    }

    private static String sanitizeZipName(String zipFile) {
        return zipFile.substring(0, zipFile.length() - 4);
    }

    private void refreshFonts() {
        // Set permissions on font files and config xml
        if (SYSTEM_THEME_FONT_DIR.exists()) {
            // Set permissions
            setPermissionsRecursive(SYSTEM_THEME_FONT_DIR,
                    FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IRWXO,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH);
            restoreconThemeDir();
        }
        // Notify zygote that themes need a refresh
        SystemProperties.set("sys.refresh_theme", "1");
        float fontSize = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, 1.0f, UserHandle.USER_CURRENT);
        Settings.System.putFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, (fontSize + 0.0000001f), UserHandle.USER_CURRENT);
    }

    private void clearFonts() {
        FileUtils.deleteContentsAndDir(SYSTEM_THEME_FONT_DIR);
        refreshFonts();
        mFontInfo.updateFrom(FontInfo.getDefaultFontInfo());
        putCurrentFontInfoInProvider(mFontInfo);
    }

    private void enforceFontService() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_FONT_MANAGER,
                "FontService");
    }

    private static void setPermissions(File path, int permissions) {
        FileUtils.setPermissions(path, permissions, -1, -1);
    }

    private static void setPermissionsRecursive(File dir, int file, int folder) {
        if (!dir.isDirectory()) {
            setPermissions(dir, file);
            return;
        }

        for (File child : dir.listFiles()) {
            if (child.isDirectory()) {
                setPermissionsRecursive(child, file, folder);
                setPermissions(child, folder);
            } else {
                setPermissions(child, file);
            }
        }

        setPermissions(dir, folder);
    }

    private static boolean restoreconThemeDir() {
        return SELinux.restoreconRecursive(SYSTEM_THEME_DIR);
    }

    private static boolean makeDir(File dir) {
        if (dir.exists()) {
            return dir.isDirectory();
        }
        if (dir.mkdirs()) {
            int permissions = FileUtils.S_IRWXU | FileUtils.S_IRWXG |
                    FileUtils.S_IRWXO;
            SELinux.restorecon(dir);
            return FileUtils.setPermissions(dir, permissions, -1, -1) == 0;
        }
        return false;
    }

    private static boolean copyDir(String src, String dst) {
        File[] files = new File(src).listFiles();
        boolean success = true;

        if (files != null) {
            for (File file : files) {
                File newFile = new File(dst + File.separator +
                        file.getName());
                if (file.isDirectory()) {
                    success &= copyDir(file.getAbsolutePath(),
                            newFile.getAbsolutePath());
                } else {
                    success &= FileUtils.copyFile(file, newFile);
                }
            }
        } else {
            // not a directory
            success = false;
        }
        return success;
    }

    private static void unzipFile(File zipFile, File destFile, String fileName) {
        try {
            ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(zipFile)));
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            boolean isDone = false;
            while (!isDone && (ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory() || !ze.getName().equals(fileName)) {
                    continue;
                }
                if (ze.getName().equals(fileName)) {
                    Log.e(TAG, "iterating " + zipFile.getName() + "Found " + fileName
                            + ", trying to extract");
                    FileOutputStream fout = new FileOutputStream(destFile);
                    try {
                        while ((count = zis.read(buffer)) != -1)
                            fout.write(buffer, 0, count);
                    } finally {
                        fout.close();
                    }
                    isDone = true;
                }
            }
            zis.close();
        } catch (IOException e) {
            Log.e(TAG, "There is an exception when trying to unzip", e);
        }
    }

    private static void unzip(String source, String destination) {
        try (ZipInputStream inputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(source)))) {
            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];

            while ((zipEntry = inputStream.getNextEntry()) != null) {
                File file = new File(destination, zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                }

                if (zipEntry.isDirectory()) {
                    continue;
                }

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "There is an exception when trying to unzip", e);
        }
    }
}
