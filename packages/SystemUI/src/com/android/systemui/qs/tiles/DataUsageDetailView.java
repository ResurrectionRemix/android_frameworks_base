/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.annotation.ColorInt;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.internal.telephony.ISub;
import com.android.settingslib.Utils;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.DataUsageGraph;

import org.codeaurora.internal.IExtTelephony;

import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;

/**
 * Layout for the data usage detail in quick settings.
 */
public class DataUsageDetailView extends LinearLayout {

    private Boolean mIsSpinnerFirstCall;
    private Boolean isSameSIM;
    private String mCarrier;
    private static final double KB = 1024;
    private static final double MB = 1024 * KB;
    private static final double GB = 1024 * MB;

    // These are the list of  possible values that
    // IExtTelephony.getCurrentUiccCardProvisioningStatus() can return

    private static final int PROVISIONED = 1;
    private static final int INVALID_STATE = -1;

    private final DecimalFormat FORMAT = new DecimalFormat("#.##");

    private IExtTelephony mExtTelephony;
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private int[] mUiccProvisionStatus = new int[mPhoneCount];

    public DataUsageDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(this, android.R.id.title, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_text, R.dimen.qs_data_usage_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_carrier_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_top_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_period_text, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_bottom_text,
                R.dimen.qs_data_usage_text_size);
    }

    private boolean isAirplaneModeOn() {
        return (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0);
    }

    public void bind(DataUsageController.DataUsageInfo info) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
        List<SubscriptionInfo> sil = subscriptionManager.getActiveSubscriptionInfoList();
        List<String> list = new ArrayList<String>();
        List<String> newlist = new ArrayList<String>();
        final Spinner spinner = (Spinner) findViewById(R.id.sim_list);
        final Resources res = mContext.getResources();
        final int titleId;
        final long bytes;
        @ColorInt int usageColor = 0;
        final String top;
        String bottom = null;

        mIsSpinnerFirstCall = true;
        isSameSIM = false;
        mCarrier = (info.carrier).toString();

        if (info.usageLevel < info.warningLevel || info.limitLevel <= 0) {
            // under warning, or no limit
            titleId = R.string.quick_settings_cellular_detail_data_usage;
            bytes = info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_warning,
                    formatBytes(info.warningLevel));
        } else if (info.usageLevel <= info.limitLevel) {
            // over warning, under limit
            titleId = R.string.quick_settings_cellular_detail_remaining_data;
            bytes = info.limitLevel - info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
        } else {
            // over limit
            titleId = R.string.quick_settings_cellular_detail_over_limit;
            bytes = info.usageLevel - info.limitLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
            usageColor = mContext.getColor(R.color.system_warning_color);
        }

        if (usageColor == 0) {
            usageColor = Utils.getColorAccent(mContext);
        }


        for(int i=0; i<sil.size();i++)
        {
            SubscriptionInfo lsuSubscriptionInfo = sil.get(i);
            CharSequence displayName = lsuSubscriptionInfo.getDisplayName();
            if (displayName == null) {
                displayName = "";
            }
            list.add(displayName.toString());
        }

        if (list.size()>1) {
            String name1 = list.get(0);
            String name2 = list.get(1);
            if (name1.equals(name2)) { //because == doesn't work on strings
                isSameSIM = true;
                name1 = name1 + " 1";
                name2 = name2 + " 2";
            }

            newlist.add(name1);
            newlist.add(name2);
         }

        spinner.setEnabled((list.size() == 2 && !isAirplaneModeOn() && isProvisioned()));

        ArrayAdapter<String> mSimList = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, newlist) {
//            public View getView(int position, View convertView, ViewGroup parent) {
//                View v = super.getView(position, convertView, parent);
//                ((TextView) v).setTextSize(16);
//                return v;
//            }

            public View getDropDownView(int position, View convertView,ViewGroup parent) {
                View v = super.getDropDownView(position, convertView,parent);
                ((TextView) v).setGravity(Gravity.CENTER);
                return v;
            }
        };

        mSimList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(mSimList);

        int defaultData = subscriptionManager.getDefaultDataSubscriptionId();

        if (isSameSIM) {
            if (defaultData == PROVISIONED) {
                mCarrier = mCarrier + " 1";
            } else if (defaultData > PROVISIONED) {
                mCarrier = mCarrier + " 2";
            }
        }


        spinner.post(new Runnable() {
            public void run() {
                spinner.setSelection(newlist.indexOf(mCarrier));
                spinner.setOnItemSelectedListener(mDataChange);
            }
        });

        final TextView sim_text = (TextView) findViewById(R.id.sim_data_text);
        sim_text.setText(R.string.accessibility_data_sim);
        final TextView title = (TextView) findViewById(android.R.id.title);
        title.setText(titleId);
        final TextView usage = (TextView) findViewById(R.id.usage_text);
        usage.setText(formatBytes(bytes));
        usage.setTextColor(usageColor);
        final DataUsageGraph graph = (DataUsageGraph) findViewById(R.id.usage_graph);
        graph.setLevels(info.limitLevel, info.warningLevel, info.usageLevel);
        final TextView carrier = (TextView) findViewById(R.id.usage_carrier_text);
        carrier.setText(mCarrier);
        final TextView period = (TextView) findViewById(R.id.usage_period_text);
        period.setText(info.period);
        final TextView infoTop = (TextView) findViewById(R.id.usage_info_top_text);
        infoTop.setVisibility(top != null ? View.VISIBLE : View.GONE);
        infoTop.setText(top);
        final TextView infoBottom = (TextView) findViewById(R.id.usage_info_bottom_text);
        infoBottom.setVisibility(bottom != null ? View.VISIBLE : View.GONE);
        infoBottom.setText(bottom);
        boolean showLevel = info.warningLevel > 0 || info.limitLevel > 0;
        graph.setVisibility(showLevel ? View.VISIBLE : View.GONE);
        if (!showLevel) {
            infoTop.setVisibility(View.GONE);
        }
   }

    private String formatBytes(long bytes) {
        final long b = Math.abs(bytes);
        double val;
        String suffix;
        if (b > 100 * MB) {
            val = b / GB;
            suffix = "GB";
        } else if (b > 100 * KB) {
            val = b / MB;
            suffix = "MB";
        } else {
            val = b / KB;
            suffix = "KB";
        }
        return FORMAT.format(val * (bytes < 0 ? -1 : 1)) + " " + suffix;
    }

    private boolean isProvisioned() {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
        List<SubscriptionInfo> sil = subscriptionManager.getActiveSubscriptionInfoList();
        mExtTelephony = IExtTelephony.Stub.asInterface(ServiceManager.getService("extphone"));
        if (mExtTelephony == null) {
            return false;
        } else {
                int i=0;
                try {
                    //set current provision state of the SIM in an array.
                    for(i=(sil.size()-1); i>=0; i--) {
                    mUiccProvisionStatus[i] = mExtTelephony.getCurrentUiccCardProvisioningStatus(i);
                          if (mUiccProvisionStatus[i] != PROVISIONED) {
                              return false;
                        }
                    }
                } catch (RemoteException ex) {
                    mUiccProvisionStatus[i] = INVALID_STATE;
                } catch (NullPointerException ex) {
                    mUiccProvisionStatus[i] = INVALID_STATE;
                }
        }
        return true;
    }

    private static void setDefaultDataSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        subscriptionManager.setDefaultDataSubId(subId);
        context.sendBroadcast(closeIntent);
    }

    private Spinner.OnItemSelectedListener mDataChange
                                    = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView av, View v,
                                    int pos, long id) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
            final List<SubscriptionInfo> subInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            final SubscriptionInfo sir;

            final Spinner spinner = (Spinner) findViewById(R.id.sim_list);
            ArrayAdapter<String> adapter = (ArrayAdapter<String>)spinner.getAdapter();
            int itemPosition = adapter.getPosition(mCarrier);

            if (!mIsSpinnerFirstCall) {
                if (v != null) {
                    switch(av.getSelectedItemPosition()) {
                        case(0):
                            if (itemPosition == 1 && isProvisioned()) {
                                sir = subInfoList.get(0);
                                setDefaultDataSubId(mContext, sir.getSubscriptionId());
                                Toast.makeText(mContext, R.string.data_switch_started, Toast.LENGTH_LONG).show();
                                break;
                            }
                        case(1):
                            if (itemPosition == 0 && isProvisioned()) {
                                sir = subInfoList.get(1);
                                setDefaultDataSubId(mContext, sir.getSubscriptionId());                    
                                Toast.makeText(mContext, R.string.data_switch_started, Toast.LENGTH_LONG).show();
                                break;
                            }
                        case(Spinner.INVALID_POSITION):
                            break;
                        default:
                            break;
                    }
                }
            }
            mIsSpinnerFirstCall = false;
        }

        public void onNothingSelected(android.widget.AdapterView av) {
        }
    };
}
