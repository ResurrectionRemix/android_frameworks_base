package android.preference;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.CheckedTextView;
import android.widget.ListView;

/**
 * Workaround for bug in MultiSelectListPreference
 * StackOverflow    http://goo.gl/Zk5j9p
 * Google Code      https://goo.gl/0KYJc6
 * @hide
 */

public class MultiSelectListPreferenceFix extends MultiSelectListPreference {
    public MultiSelectListPreferenceFix(Context context) {
        super(context);
    }

    public MultiSelectListPreferenceFix(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MultiSelectListPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MultiSelectListPreferenceFix(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog == null)
            return;

        if (Build.VERSION.SDK_INT >= 23) {
            ListView listView = dialog.getListView();

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    int size = view.getChildCount();
                    for (int i = 0; i < size; i++) {
                        View v = view.getChildAt(i);
                        if (v instanceof CheckedTextView)
                            ((CheckedTextView)v).refreshDrawableState();
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    int size = view.getChildCount();
                    for (int i = 0; i < size; i++) {
                        View v = view.getChildAt(i);
                        if (v instanceof CheckedTextView)
                            ((CheckedTextView)v).refreshDrawableState();
                    }
                }
            });
        }
    }
}
