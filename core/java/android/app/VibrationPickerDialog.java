
package android.app;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.VibrationPattern;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.io.Serializable;

import com.android.internal.R;

public class VibrationPickerDialog extends DialogFragment {

    private static final String TAG = "VibrationPickerDialog";

    private final int VIB_OK = 10;
    private final int VIB_CANCEL = 11;
    private final int VIB_DEL = 12;

    private boolean mIsDel;
    private Context mContext;
    private Handler mHandler;
    private Vibrator mVibrator;
    private AlertDialog.Builder mBuilder;
    private VibrationPattern mPattern;

    public static VibrationPickerDialog newInstance(Handler handler, boolean isDel,
            String selectedUri) {
        VibrationPickerDialog vpd = new VibrationPickerDialog();

        Bundle args = new Bundle();
        args.putBoolean("isdel", isDel);
        if (selectedUri == null) {
            args.putString("uri", "");
        } else {
            args.putString("uri", selectedUri);
        }
        args.putSerializable("handler", new HandlerHolder(handler));
        vpd.setArguments(args);
        return vpd;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mIsDel = getArguments().getBoolean("isdel");
        String mUriString = getArguments().getString("uri");
        mHandler = ((HandlerHolder) getArguments().getSerializable("handler")).getHandler();
        mContext = getActivity();
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        final Uri allVibrations = Uri.parse(VibrationPattern.URI);
        final Cursor vibrations = mContext.getContentResolver().
                query(allVibrations, null, null, null, null);

        vibrations.moveToFirst();
        int ID = -1;
        if (!mUriString.isEmpty()) {
            Uri mUri = Uri.parse(mUriString);
            do {
                try {
                    if (Integer.parseInt(mUri.getLastPathSegment()) == vibrations.getInt(0)) {
                        ID = vibrations.getPosition();
                    }
                } catch (Exception ex) {
                    // nothing to do here
                }
            } while (vibrations.moveToNext());
        }
        final int selectedID = ID;
        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(mContext,
                android.R.layout.simple_list_item_single_choice,
                vibrations,
                new String[] {
                        "name"
                },
                new int[] {
                        android.R.id.text1
                }, 0) {

            @Override
            public Object getItem(int pos) {
                vibrations.moveToPosition(pos);
                int id = vibrations.getInt(0);
                setSelectedVibration(new VibrationPattern(
                        Uri.parse(VibrationPattern.URI + "/" + id), mContext));
                return getSelectedVibration();
            }
        };

        LayoutInflater factory = LayoutInflater.from(mContext);
        final View vibListView = factory.inflate(R.layout.vibration_picker_dialog, null);

        return new AlertDialog.Builder(mContext)
                .setTitle(
                        mIsDel ? R.string.vibration_picker_del_title
                                : R.string.vibration_picker_title)
                .setIcon(
                        mIsDel ? R.drawable.ic_dialog_alert
                                : 0)
                .setView(vibListView)
                .setSingleChoiceItems(adapter, selectedID,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ((VibrationPattern) adapter.getItem(which)).play();
                            }
                        })
                .setPositiveButton(mIsDel ? R.string.delete : R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (mIsDel) {
                                    delVib(getSelectedVibration());
                                    adapter.notifyDataSetChanged();
                                } else {
                                    selectVib(getSelectedVibration());
                                }
                                stopAllVibrations();
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                stopAllVibrations();
                                sendCancel();
                            }
                        })
                .create();
    }

    private void selectVib(VibrationPattern vib) {
        final Message m = new Message();
        m.obj = vib;
        m.what = VIB_OK;
        mHandler.sendMessage(m);
    }

    private void sendCancel() {
        final Message m = new Message();
        m.what = VIB_CANCEL;
        mHandler.sendMessage(m);
    }

    private void delVib(VibrationPattern vib) {
        final Message m = new Message();
        m.obj = vib;
        m.what = VIB_DEL;
        mHandler.sendMessage(m);
    }

    public void stopAllVibrations() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    private void setSelectedVibration(VibrationPattern pattern) {
        mPattern = pattern;
    }

    private VibrationPattern getSelectedVibration() {
        return mPattern;
    }

    static class HandlerHolder implements Serializable {
        Handler tHandler;

        public HandlerHolder(Handler handler) {
            tHandler = handler;
        }

        public Handler getHandler() {
            return tHandler;
        }
    }
}
