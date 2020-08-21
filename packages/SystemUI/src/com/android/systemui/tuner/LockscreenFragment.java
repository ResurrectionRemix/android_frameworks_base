/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.IntentButtonProvider.IntentButton;
import com.android.systemui.statusbar.ScalingDrawableWrapper;
import com.android.systemui.statusbar.phone.ExpandableIndicator;
import com.android.systemui.statusbar.policy.ExtensionController.TunerFactory;
import com.android.systemui.tuner.ShortcutParser.Shortcut;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class LockscreenFragment extends PreferenceFragment {

    private static final String KEY_LEFT = "left";
    private static final String KEY_RIGHT = "right";
    private static final String KEY_CUSTOMIZE = "customize";
    private static final String KEY_SHORTCUT = "shortcut";

    public static final String LOCKSCREEN_LEFT_BUTTON = "sysui_keyguard_left";
    public static final String LOCKSCREEN_LEFT_UNLOCK = "sysui_keyguard_left_unlock";
    public static final String LOCKSCREEN_RIGHT_BUTTON = "sysui_keyguard_right";
    public static final String LOCKSCREEN_RIGHT_UNLOCK = "sysui_keyguard_right_unlock";

    private final ArrayList<Tunable> mTunables = new ArrayList<>();
    private TunerService mTunerService;
    private Handler mHandler;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mTunerService = Dependency.get(TunerService.class);
        mHandler = new Handler();
        addPreferencesFromResource(R.xml.lockscreen_settings);
        setupGroup(LOCKSCREEN_LEFT_BUTTON, LOCKSCREEN_LEFT_UNLOCK);
        setupGroup(LOCKSCREEN_RIGHT_BUTTON, LOCKSCREEN_RIGHT_UNLOCK);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTunables.forEach(t -> mTunerService.removeTunable(t));
    }

    private void setupGroup(String buttonSetting, String unlockKey) {
        Preference shortcut = findPreference(buttonSetting);
        SwitchPreference unlock = (SwitchPreference) findPreference(unlockKey);
        addTunable((k, v) -> {
            boolean visible = !TextUtils.isEmpty(v) && !v.equals("none");
            unlock.setVisible(visible);
            setSummary(shortcut, v);
        }, buttonSetting);
    }

    private void showSelectDialog(String buttonSetting) {
        RecyclerView v = (RecyclerView) LayoutInflater.from(getContext())
                .inflate(R.layout.tuner_shortcut_list, null);
        v.setLayoutManager(new LinearLayoutManager(getContext()));
        AlertDialog dialog = new Builder(getContext())
                .setView(v)
                .show();
        Adapter adapter = new Adapter(getContext(), item -> {
            mTunerService.setValue(buttonSetting, item.getSettingValue());
            dialog.dismiss();
        });

        v.setAdapter(adapter);
    }

    private void setSummary(Preference shortcut, String value) {
        if (value == null) {
            shortcut.setSummary(R.string.lockscreen_default);
            return;
        }
        if (value.contains("::")) {
            Shortcut info = getShortcutInfo(getContext(), value);
            shortcut.setSummary(info != null ? info.label : null);
        } else if (value.contains("/")) {
            ActivityInfo info = getActivityinfo(getContext(), value);
            shortcut.setSummary(info != null ? info.loadLabel(getContext().getPackageManager())
                    : null);
        } else if (value.equals("none")) {
            shortcut.setSummary(R.string.lockscreen_none);
        } else {
            shortcut.setSummary(R.string.lockscreen_default);
        }
    }

    private void addTunable(Tunable t, String... keys) {
        mTunables.add(t);
        mTunerService.addTunable(t, keys);
    }

    public static ActivityInfo getActivityinfo(Context context, String value) {
        ComponentName component = ComponentName.unflattenFromString(value);
        try {
            return context.getPackageManager().getActivityInfo(component, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static Shortcut getShortcutInfo(Context context, String value) {
        return Shortcut.create(context, value);
    }

    public static class Holder extends ViewHolder {
        public final ImageView icon;
        public final TextView title;
        public final ExpandableIndicator expand;

        public Holder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(android.R.id.icon);
            title = (TextView) itemView.findViewById(android.R.id.title);
            expand = (ExpandableIndicator) itemView.findViewById(R.id.expand);
        }
    }

    private static class StaticShortcut extends Item {

        private final Context mContext;
        private final Shortcut mShortcut;


        public StaticShortcut(Context context, Shortcut shortcut) {
            mContext = context;
            mShortcut = shortcut;
        }

        @Override
        public Drawable getDrawable() {
            return mShortcut.icon.loadDrawable(mContext);
        }

        @Override
        public String getLabel() {
            return mShortcut.label;
        }

        @Override
        public String getSettingValue() {
            return mShortcut.toString();
        }

        @Override
        public Boolean getExpando() {
            return null;
        }
    }

    private static class App extends Item {

        private final Context mContext;
        private final LauncherActivityInfo mInfo;
        private final ArrayList<Item> mChildren = new ArrayList<>();
        private boolean mExpanded;

        public App(Context context, LauncherActivityInfo info) {
            mContext = context;
            mInfo = info;
            mExpanded = false;
        }

        public void addChild(Item child) {
            mChildren.add(child);
        }

        @Override
        public Drawable getDrawable() {
            return mInfo.getBadgedIcon(mContext.getResources().getConfiguration().densityDpi);
        }

        @Override
        public String getLabel() {
            return mInfo.getLabel().toString();
        }

        @Override
        public String getSettingValue() {
            return mInfo.getComponentName().flattenToString();
        }

        @Override
        public Boolean getExpando() {
            return mChildren.size() != 0 ? mExpanded : null;
        }

        @Override
        public void toggleExpando(Adapter adapter) {
            mExpanded = !mExpanded;
            if (mExpanded) {
                mChildren.forEach(child -> adapter.addItem(this, child));
            } else {
                mChildren.forEach(child -> adapter.remItem(child));
            }
        }
    }

    private abstract static class Item {
        public abstract Drawable getDrawable();

        public abstract String getLabel();

        public abstract String getSettingValue();

        public abstract Boolean getExpando();

        public void toggleExpando(Adapter adapter) {
        }
    }

    public static class Adapter extends RecyclerView.Adapter<Holder> {
        private ArrayList<Item> mItems = new ArrayList<>();
        private final Context mContext;
        private final Consumer<Item> mCallback;

        public Adapter(Context context, Consumer<Item> callback) {
            mContext = context;
            mCallback = callback;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tuner_shortcut_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Item item = mItems.get(position);
            holder.icon.setImageDrawable(item.getDrawable());
            holder.title.setText(item.getLabel());
            holder.itemView.setOnClickListener(
                    v -> mCallback.accept(mItems.get(holder.getAdapterPosition())));
            Boolean expando = item.getExpando();
            if (expando != null) {
                holder.expand.setVisibility(View.VISIBLE);
                holder.expand.setExpanded(expando);
                holder.expand.setOnClickListener(
                        v -> mItems.get(holder.getAdapterPosition()).toggleExpando(Adapter.this));
            } else {
                holder.expand.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        public void addItem(Item item) {
            mItems.add(item);
            notifyDataSetChanged();
        }

        public void remItem(Item item) {
            int index = mItems.indexOf(item);
            mItems.remove(item);
            notifyItemRemoved(index);
        }

        public void addItem(Item parent, Item child) {
            int index = mItems.indexOf(parent);
            mItems.add(index + 1, child);
            notifyItemInserted(index + 1);
        }
    }

    public static class LockButtonFactory implements TunerFactory<IntentButton> {

        private final String mKey;
        private final Context mContext;

        public LockButtonFactory(Context context, String key) {
            mContext = context;
            mKey = key;
        }

        @Override
        public String[] keys() {
            return new String[]{mKey};
        }

        @Override
        public IntentButton create(Map<String, String> settings) {
            String buttonStr = settings.get(mKey);
            if (!TextUtils.isEmpty(buttonStr)) {
                if (buttonStr.contains("::")) {
                    return new ShortcutButton(mContext, buttonStr);
                } else if (buttonStr.contains("/")) {
                    return new ActivityButton(mContext, buttonStr);
                } else if (buttonStr.equals("none")) {
                    return new HiddenButton();
                }
            }
            return null;
        }
    }

    private static class ShortcutButton implements IntentButton {
        private Shortcut mShortcut;
        private IconState mIconState;
        private Context mContext;
        private boolean mInitDone;
        private String mShortcutString;
        private int mSize;

        public ShortcutButton(Context context, String shortcutString) {
            mContext = context;
            mShortcutString = shortcutString;
            mIconState = new IconState();
            mIconState.isVisible = true;
            mIconState.drawable = mContext.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            mSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32,
                    mContext.getResources().getDisplayMetrics());
            mIconState.drawable = new ScalingDrawableWrapper(mIconState.drawable,
                    mSize / (float) mIconState.drawable.getIntrinsicWidth());
            mIconState.tint = false;
            init();
        }

        private void init() {
            mShortcut = getShortcutInfo(mContext, mShortcutString);
            if (mShortcut != null) {
                // we need to flatten AdaptiveIconDrawable layers to a single drawable
                mIconState.drawable = getBitmapDrawable(
                        mContext.getResources(), mShortcut.icon.loadDrawable(mContext)).mutate();
                mIconState.contentDescription = mShortcut.label;
                mIconState.drawable = new ScalingDrawableWrapper(mIconState.drawable,
                        mSize / (float) mIconState.drawable.getIntrinsicWidth());
                mInitDone = true;
            }
        }

        @Override
        public IconState getIcon() {
            if (!mInitDone) {
                init();
            }
            return mIconState;
        }

        @Override
        public Intent getIntent() {
            if (!mInitDone) {
                init();
            }
            if (mShortcut != null) {
                return mShortcut.intent;
            }
            return null;
        }
    }

    private static class ActivityButton implements IntentButton {
        private Intent mIntent;
        private IconState mIconState;
        private ComponentName mComponentName;
        private Context mContext;
        private boolean mInitDone;
        private int mSize;

        public ActivityButton(Context context, String componentName) {
            mContext = context;
            mComponentName = ComponentName.unflattenFromString(componentName);
            mIconState = new IconState();
            mIconState.isVisible = true;
            mIconState.drawable = mContext.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            mSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32,
                    mContext.getResources().getDisplayMetrics());
            mIconState.drawable = new ScalingDrawableWrapper(mIconState.drawable,
                    mSize / (float) mIconState.drawable.getIntrinsicWidth());
            mIconState.tint = false;
            init();
        }

        private void init() {
            try {
                ActivityInfo info = mContext.getPackageManager().getActivityInfo(mComponentName, 0);
                // we need to flatten AdaptiveIconDrawable layers to a single drawable
                mIconState.drawable = getBitmapDrawable(
                        mContext.getResources(), info.loadIcon(mContext.getPackageManager())).mutate();
                mIconState.contentDescription = info.loadLabel(mContext.getPackageManager());
                mIconState.drawable = new ScalingDrawableWrapper(mIconState.drawable,
                        mSize / (float) mIconState.drawable.getIntrinsicWidth());
                mIntent = new Intent().setComponent(mComponentName);
                mInitDone = true;
            } catch (NameNotFoundException e) {
            }
        }

        @Override
        public IconState getIcon() {
            if (!mInitDone) {
                init();
            }
            return mIconState;
        }

        @Override
        public Intent getIntent() {
            if (!mInitDone) {
                init();
            }
            return mIntent;
        }
    }


    private static BitmapDrawable getBitmapDrawable(Resources resources, Drawable image) {
        if (image instanceof BitmapDrawable) {
            return (BitmapDrawable) image;
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Bitmap bmResult = Bitmap.createBitmap(image.getIntrinsicWidth(), image.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        image.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        image.draw(canvas);
        return new BitmapDrawable(resources, bmResult);
    }

    private static class HiddenButton implements IntentButton {
        private final IconState mIconState;

        public HiddenButton() {
            mIconState = new IconState();
            mIconState.isVisible = false;
        }

        @Override
        public IconState getIcon() {
            return mIconState;
        }

        @Override
        public Intent getIntent() {
            return null;
        }
    }
}
