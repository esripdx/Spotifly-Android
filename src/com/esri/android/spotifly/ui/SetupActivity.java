package com.esri.android.spotifly.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.esri.android.spotifly.R;
import com.esri.android.spotifly.util.SpotiflyUtils;
import java.util.Calendar;
import java.util.Locale;

public class SetupActivity extends FragmentActivity {
    private static final String TAG = "SetupActivity";

    private EditText mFlightNumber;
    private Spinner mCarrierName;
    private TextView mDatePicker;
    private boolean mGotDate = false;
    private ProgressDialog mProgress;

    private int day = -1;
    private int month = -1;
    private int year = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup);
    }

    @Override
    public void onStart() {
        super.onStart();
        Button goButton = (Button) findViewById(R.id.go_button);
        mFlightNumber = (EditText) findViewById(R.id.flight_number);
        mCarrierName = (Spinner) findViewById(R.id.airline_carrier);
        mDatePicker = (TextView) findViewById(R.id.flight_date);

        String carrierName = SpotiflyUtils.getCarrierName(this);
        if (mCarrierName != null) {
            // Create an ArrayAdapter using the string array and a default spinner layout
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.carriers_array, android.R.layout.simple_spinner_item);
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            mCarrierName.setAdapter(adapter);

            if (!TextUtils.isEmpty(carrierName)) {
                mCarrierName.setSelection(adapter.getPosition(carrierName));
            }
        }

        String flightNumber = SpotiflyUtils.getFlightNumber(this);
        if (!TextUtils.isEmpty(flightNumber) && mFlightNumber != null) {
            mFlightNumber.setText(flightNumber);
        }

        if (mDatePicker != null) {
            int[] dateFields = SpotiflyUtils.getFlightDate(this);
            if (dateFields != null) {
                mGotDate = true;
                year = dateFields[2];
                month = dateFields[1];
                day = dateFields[0];
            } else {
                mGotDate = false;
                // Use the current date as the default date in the picker
                final Calendar c = Calendar.getInstance();
                year = c.get(Calendar.YEAR);
                month = c.get(Calendar.MONTH);
                day = c.get(Calendar.DAY_OF_MONTH);
            }

            setUpDateView(day, month, year);

            mDatePicker.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DialogFragment newFragment = new DatePickerFragment();
                    newFragment.show(getSupportFragmentManager(), "datePicker");
                }
            });
        } else {
            Log.w(TAG, "date picker text field was null?");
        }

        if (goButton != null) {
            goButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String carrierName = null;
                    String flightNum = null;

                    if (mProgress == null) {
                        mProgress = new ProgressDialog(SetupActivity.this);
                        mProgress.setTitle("Progress");
                        mProgress.setMessage("Looking up flight...");
                        mProgress.setCanceledOnTouchOutside(false);
                    }
                    mProgress.show();

                    if (mFlightNumber != null) {
                        String pattern = "^[a-zA-Z0-9_]*$";
                        final Editable text = mFlightNumber.getText();

                        if (text != null && !TextUtils.isEmpty(text.toString()) && text.toString().matches(pattern)) {
                            flightNum = text.toString();
                            SpotiflyUtils.setFlightNumber(SetupActivity.this, flightNum);
                        } else {
                            Log.w(TAG, "Bad flight number encountered");
                            mFlightNumber.requestFocus();
                            setupToast("Please enter an alpha-numeric flight number.");
                            return;
                        }
                    } else {
                        Log.w(TAG, "Could not get flight number, view was null.");
                    }

                    if (mCarrierName != null) {
                        carrierName = (String) mCarrierName.getSelectedItem();

                        if (carrierName != null && !TextUtils.isEmpty(carrierName)) {
                            SpotiflyUtils.setCarrierName(SetupActivity.this, carrierName);
                        } else {
                            Log.w(TAG, "Bad carrier name encountered");
                            mCarrierName.requestFocus();
                            setupToast("Please enter an alpha-numeric carrier name.");
                            return;
                        }
                    } else {
                        Log.w(TAG, "Could not get carrier name, view was null.");
                    }

                    if (day > 0 && month > 0 && year > 0 && !TextUtils.isEmpty(carrierName) && !TextUtils.isEmpty(flightNum)) {
                        Intent intent = new Intent(SetupActivity.this, MapActivity.class);
                        startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    } else {
                        Log.w(TAG, String.format("Could not get flight date, date was invalid: %d/%d/%d", month + 1, day, year));
                    }

                    if (mProgress != null && mProgress.isShowing()) {
                        mProgress.cancel();
                    }
                }
            });
        } else {
            Log.w(TAG, "go button was null?");
        }
    }

    private void setUpDateView(int day, int month, int year) {
        Log.d(TAG, "set up date: " + month + "/" + day + "/" + year);
        if (mDatePicker != null) {
            String longMonth = Calendar.getInstance().getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US);
            mDatePicker.setText(String.format("%s, %d %d", longMonth, day, year));
            mDatePicker.setTextColor(R.color.black);
        }
    }

    private void setupToast(String text) {
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 160);
        toast.show();
    }

    private class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int year;
            int month;
            int day;
            if (mGotDate) {
                year = SetupActivity.this.year;
                month = SetupActivity.this.month;
                day = SetupActivity.this.day;
            } else {
                // Use the current date as the default date in the picker
                final Calendar c = Calendar.getInstance();
                year = c.get(Calendar.YEAR);
                month = c.get(Calendar.MONTH);
                day = c.get(Calendar.DAY_OF_MONTH);
            }

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int day) {
            SpotiflyUtils.setFlightDate(SetupActivity.this, day, month, year);
            SetupActivity.this.day = day;
            SetupActivity.this.month = month;
            SetupActivity.this.year = year;
            mGotDate = true;
            setUpDateView(day, month, year);
        }
    }
}
