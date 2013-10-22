package com.esri.android.spotifly.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
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
import com.esri.android.spotifly.util.NetUtils;
import com.esri.android.spotifly.util.SpotiflyUtils;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class SetupActivity extends FragmentActivity {
    private static final String TAG = "SetupActivity";
    private static final String FLIGHT_STATUS_URL = "https://api.flightstats.com/flex/flightstatus/rest/v2/json/flight/status/";

    private Button mGoButton;
    private EditText mFlightNumber;
    private Spinner mCarrierName;
    private TextView mDatePicker;
    private boolean mGotDate = false;

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
        mGoButton = (Button) findViewById(R.id.go_button);
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

        if (mGoButton != null) {
            mGoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String airlineCode = null;
                    String flightNum = null;

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
                        String text = (String) mCarrierName.getSelectedItem();

                        if (text != null && !TextUtils.isEmpty(text)) {
                            airlineCode = text.split(" - ")[1];
                            SpotiflyUtils.setCarrierName(SetupActivity.this, text);
                        } else {
                            Log.w(TAG, "Bad carrier name encountered");
                            mCarrierName.requestFocus();
                            setupToast("Please enter an alpha-numeric carrier name.");
                            return;
                        }
                    } else {
                        Log.w(TAG, "Could not get carrier name, view was null.");
                    }

                    if (day > 0 && month > 0 && year > 0 && !TextUtils.isEmpty(airlineCode) && !TextUtils.isEmpty(flightNum)) {
                        String urlTail = String.format("%s/%s/dep/%d/%d/%d", airlineCode, flightNum, year, month + 1, day);

                        HashMap<String, String> params = new HashMap<String, String>();
                        params.put("appId", getString(R.string.flight_stats_app_id));
                        params.put("appKey", getString(R.string.flight_stats_app_key));
                        params.put("utc", "false");
                        String finalUrl = FLIGHT_STATUS_URL + urlTail;
                        NetUtils.getJson(SetupActivity.this, finalUrl, params, null, new NetUtils.JsonRequestListener() {
                            @Override
                            public void onSuccess(JSONObject json) {
                                if (json != null) {
                                    JSONArray flightStatuses = json.optJSONArray("flightStatuses");

                                    if (flightStatuses != null && flightStatuses.length() > 0) {
                                        JSONObject flightStatus = flightStatuses.optJSONObject(0);

                                        if (flightStatus != null) {
                                            int flightId = flightStatus.optInt("flightId");

                                            if (flightId != 0) {
                                                SpotiflyUtils.setFlightId(SetupActivity.this, flightId);
                                                Intent intent = new Intent(SetupActivity.this, MapActivity.class);
                                                startActivity(intent);
                                                return;
                                            }
                                        }
                                    }
                                }

                                Log.w(TAG, "Could not get flight ID from status request.");
                                setupToast("No matching flight found!");
                            }

                            @Override
                            public void onError(JSONObject json, StatusLine status) {
                                Log.w(TAG, "Error getting flight status: " + status.getReasonPhrase());
                            }

                            @Override
                            public void onFailure(Throwable error) {
                                Log.w(TAG, "Error getting flight status: ", error);
                            }
                        });
                    } else {
                        Log.w(TAG, String.format("Could not get flight date, date was invalid: %d/%d/%d", month + 1, day, year));
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

    private class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {
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
