package com.esri.android.spotifly.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import com.esri.android.map.Callout;
import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.android.spotifly.R;
import com.esri.android.spotifly.util.Constants;
import com.esri.android.spotifly.util.NetUtils;
import com.esri.android.spotifly.util.SpotiflyUtils;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.gdb.GdbFeatureTable;
import com.esri.core.gdb.Geodatabase;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.tasks.gdb.GenerateGeodatabaseParameters;
import com.esri.core.tasks.gdb.GeodatabaseStatusCallback;
import com.esri.core.tasks.gdb.GeodatabaseStatusInfo;
import com.esri.core.tasks.gdb.GeodatabaseTask;
import com.esri.core.tasks.gdb.SyncModel;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class MapActivity extends Activity {
    private static final String TAG = "MapActivity";
    private static final String TILE_SERVICE_URL = "http://services.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer";
    private static final String FEATURE_SERVICE_URL = "http://services.arcgis.com/rOo16HdIMeOBI4Mb/arcgis/rest/services/Spotifly/FeatureServer/0";
    private static final String FEATURE_SERVICE_SYNC_URL = "http://services.arcgis.com/rOo16HdIMeOBI4Mb/arcgis/rest/services/Spotifly/FeatureServer/0";
    private static final String FLIGHT_STATUS_URL = "https://api.flightstats.com/flex/flightstatus/rest/v2/json/flight/status/";

    private static final SpatialReference WGS84 = SpatialReference.create(SpatialReference.WKID_WGS84);
    private static final SpatialReference WEB_MERCATOR = SpatialReference.create(SpatialReference.WKID_WGS84_WEB_MERCATOR);

    private MapView mMapView;
    private Button mSetupButton;
    private ArcGISFeatureLayer featureLayer;
    private ArcGISTiledMapServiceLayer tileLayer;
    private ProgressDialog mProgress;
    private ArrayList<Point> mPortPoints = new ArrayList<Point>();
    private int mFlightDurationMinutes;
    private Date mDepartureDate;
    private Date mArrivalDate;

    private GraphicsLayer graphicsLayer;
    private ProgressDialog progress;
    private int m_calloutStyle;
    private Callout m_callout;
    private ViewGroup calloutContent;
    private boolean m_isMapLoaded;
    private Graphic m_identifiedGraphic;
    private GeodatabaseTask gdbTask;
    private String gdbFileName = "geodatabase.db";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_view);

        mMapView = (MapView) findViewById(R.id.map);
        mSetupButton = (Button) findViewById(R.id.back_button);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mProgress == null) {
            mProgress = new ProgressDialog(this);
            mProgress.setTitle("Progress");
            mProgress.setMessage("Looking up flight...");
            mProgress.setCanceledOnTouchOutside(false);
        }
        mProgress.show();

        String carrierName = SpotiflyUtils.getCarrierName(this);
        String flightNumber = SpotiflyUtils.getFlightNumber(this);
        int[] dateFields = SpotiflyUtils.getFlightDate(this);
        int year = -1;
        int month = -1;
        int day = -1;
        if (dateFields != null) {
            year = dateFields[2];
            month = dateFields[1];
            day = dateFields[0];
        }
        String airlineCode = carrierName.split(" - ")[1];

        String urlTail = String.format("%s/%s/dep/%d/%d/%d", airlineCode, flightNumber, year, month + 1, day);

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("appId", Constants.FLIGHT_STATS_APP_ID);
        params.put("appKey", Constants.FLIGHT_STATS_APP_KEY);
        params.put("utc", "false");
        String finalUrl = FLIGHT_STATUS_URL + urlTail;
        NetUtils.getJson(this, finalUrl, params, null, new NetUtils.JsonRequestListener() {
            @Override
            public void onSuccess(JSONObject json) {
                if (json != null) {
                    JSONArray flightStatuses = json.optJSONArray("flightStatuses");
                    JSONObject appendix = json.optJSONObject("appendix");

                    if (appendix != null) {
                        JSONArray airports = appendix.optJSONArray("airports");

                        if (airports != null && airports.length() > 0) {
                            for (int i = 0; i < airports.length(); i++) {
                                JSONObject port = airports.optJSONObject(i);

                                if (port != null) {
                                    double latitude = port.optDouble("latitude");
                                    double longitude = port.optDouble("longitude");
                                    mPortPoints.add(new Point(longitude, latitude));
                                }
                            }
                        } else {
                            Log.d(TAG, "No airports returned.");
                        }
                    } else {
                        Log.d(TAG, "Appendix was null.");
                    }

                    if (flightStatuses != null && flightStatuses.length() > 0) {
                        JSONObject flightStatus = flightStatuses.optJSONObject(0);

                        if (flightStatus != null) {
                            int flightId = flightStatus.optInt("flightId");
                            JSONObject departureDate = flightStatus.optJSONObject("departureDate");
                            JSONObject arrivalDate = flightStatus.optJSONObject("arrivalDate");
                            JSONObject flightDurations = flightStatus.optJSONObject("flightDurations");

                            if (flightId != 0) {
                                SpotiflyUtils.setFlightId(MapActivity.this, flightId);
                            } else {
                                Log.d(TAG, "No flight ID.");
                            }

                            if (departureDate != null) {
                                String depDate = departureDate.optString("dateUtc");

                                if (!TextUtils.isEmpty(depDate)) {
                                    Log.d(TAG, depDate);
                                    String s = depDate.replace("Z", "+00:00");
                                    try {
                                        s = s.substring(0, 22) + s.substring(23);
                                        mDepartureDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSZ").parse(s);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Error unpacking departure date. ", e);
                                    }
                                } else {
                                    Log.d(TAG, "No departure date string.");
                                }
                            } else {
                                Log.d(TAG, "No departure date object.");
                            }

                            if (arrivalDate != null) {
                                String arrDate = departureDate.optString("dateUtc");

                                if (!TextUtils.isEmpty(arrDate)) {
                                    Log.d(TAG, arrDate);
                                    String s = arrDate.replace("Z", "+00:00");
                                    try {
                                        s = s.substring(0, 22) + s.substring(23);
                                        mArrivalDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSZ").parse(s);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Error unpacking arrival date. ", e);
                                    }
                                } else {
                                    Log.d(TAG, "No arrival date string.");
                                }
                            } else {
                                Log.d(TAG, "No arrival date object.");
                            }

                            if (flightDurations != null) {
                                int scheduledBlockMinutes = flightDurations.optInt("scheduledBlockMinutes");

                                if (scheduledBlockMinutes > 0) {
                                    mFlightDurationMinutes = scheduledBlockMinutes;
                                } else {
                                    Log.d(TAG, "No scheduled block minutes (flight duration).");
                                }
                            }  else {
                                Log.d(TAG, "No flight durations object");
                            }

                            drawFlightPath();
                            animateFlightPath();

                            //TODO: zoom to extent

                            cancelDialog();
                            return;
                        }
                    } else {
                        Log.d(TAG, "No flight statuses returned.");
                    }
                } else {
                    Log.d(TAG, "Return json was null/empty.");
                }

                Log.w(TAG, "Could not get flight ID from status request.");
                cancelDialog();
                setupToast("No matching flight found!");
            }

            @Override
            public void onError(JSONObject json, StatusLine status) {
                Log.w(TAG, "Error getting flight status: " + status.getReasonPhrase());
                setupToast("Error while looking up flight, please try again.");
                cancelDialog();
            }

            @Override
            public void onFailure(Throwable error) {
                Log.w(TAG, "Error getting flight status: ", error);
                setupToast("Error while looking up flight, please try again.");
                cancelDialog();
            }
        });

        if (mSetupButton != null) {
            mSetupButton.getBackground().setAlpha(170);
            mSetupButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MapActivity.this, SetupActivity.class);
                    startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
            });
        }

        if (mMapView != null) {
            mMapView.enableWrapAround(true);

            // Add Tile layer to the MapView
            tileLayer = new ArcGISTiledMapServiceLayer(TILE_SERVICE_URL);
            mMapView.addLayer(tileLayer);
            // Add Feature layer to the MapView
            featureLayer = new ArcGISFeatureLayer(FEATURE_SERVICE_URL, ArcGISFeatureLayer.MODE.ONDEMAND);
            mMapView.addLayer(featureLayer);
            // Add Graphics layer to the MapView
            graphicsLayer = new GraphicsLayer();
            mMapView.addLayer(graphicsLayer);

            // Set the initial extent of the map
            final Envelope initExtent =
                    new Envelope(-1557669.6939985836, -4115210.743119574, 9205833.473803047, 1.3524975004110876E7);
            mMapView.setExtent(initExtent);

            mMapView.centerAndZoom(39.707186656826565, -121.11328124999999, 8);

            // Get the MapView's callout from xml->identify_calloutstyle.xml
            m_calloutStyle = R.xml.identify_calloutstyle;
            LayoutInflater inflater = getLayoutInflater();
            m_callout = mMapView.getCallout();
            // Get the layout for the Callout from
            // layout->identify_callout_content.xml
            calloutContent = (ViewGroup) inflater.inflate(R.layout.identify_callout_content, null);
            m_callout.setContent(calloutContent);

            mMapView.setOnStatusChangedListener(new OnStatusChangedListener() {
                private static final long serialVersionUID = 1L;

                @Override
                public void onStatusChanged(Object source, STATUS status) {
                    // Check to see if map has successfully loaded
                    if ((source == mMapView) && (status == STATUS.INITIALIZED)) {
                        // Set the flag to true
                        m_isMapLoaded = true;
                    }
                }
            });

            mMapView.setOnSingleTapListener(new OnSingleTapListener() {
                private static final long serialVersionUID = 1L;

                @Override
                public void onSingleTap(float x, float y) {

                    if (m_isMapLoaded) {
                        // If map is initialized and Single tap is registered on
                        // screen
                        // identify the location selected
                        identifyLocation(x, y);
                    }
                }
            });

            gdbTask = new GeodatabaseTask(FEATURE_SERVICE_SYNC_URL, null, new CallbackListener<FeatureServiceInfo>() {
                @Override
                public void onError(Throwable e) {
                    Log.e(TAG, "Geodatabase Task Error", e);
                }

                @Override
                public void onCallback(FeatureServiceInfo info) {
                    Log.d(TAG, "Geodatabase Task Callback!");

                    Log.d(TAG, info.isSyncEnabled() ? "Sync enabled" : "Sync disabled");

                    if (info.isSyncEnabled()) {
                        int[] layerIds = {0};
                        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(
                                layerIds, mMapView.getExtent(), mMapView.getSpatialReference(), true, SyncModel.LAYER,
                                mMapView.getSpatialReference());

                        gdbTask.submitGenerateGeodatabaseJobAndDownload(params, gdbFileName,
                                new GeodatabaseStatusCallback() {
                                    @Override
                                    public void statusUpdated(GeodatabaseStatusInfo status) {
                                        Log.d(TAG, status.getStatus().toString());
                                    }
                                }, new CallbackListener<Geodatabase>() {
                                    @Override
                                    public void onCallback(Geodatabase obj) {
                                        // update UI
                                        Log.d(TAG, "Geodatabase downloaded!");
                                        Log.i(TAG, "geodatabase is: " + obj.getPath());

                                        // Remove all the feature layers from map and add a feature
                                        // Layer from the downloaded geodatabase
                                        for (Layer layer : mMapView.getLayers()) {
                                            if (layer instanceof ArcGISFeatureLayer) {
                                                mMapView.removeLayer(layer);
                                            }
                                        }
                                        for (GdbFeatureTable gdbFeatureTable : obj.getGdbTables()) {
                                            if (gdbFeatureTable.hasGeometry()) {
                                                mMapView.addLayer(new FeatureLayer(gdbFeatureTable));
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        Log.e(TAG, "", e);
                                    }

                                });
                        Log.e(TAG, "Submitting gdb job...");
                    }
                }
            });
        }

        cancelDialog();
    }

    private void cancelDialog() {
        if (mProgress != null && mProgress.isShowing()) {
            mProgress.cancel();
        }
    }

    private void setupToast(String text) {
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 160);
        toast.show();
    }


    public ArrayList<Point> getCoordinates(int count) {
        Point end = new Point(-122.592901, 45.588995); // mPortPoints.get(0);
        Point start = new Point(-117.597651, 34.060681); //mPortPoints.get(mPortPoints.size() - 1);

        ArrayList <Point> points = new ArrayList<Point>();

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();

        for (int i = 0; i < count; i++) {
            double x = (start.getX() + (dx / (float) count) * i);
            double y = (start.getY() + (dy / (float) count) * i);
            points.add(new Point(x, y));
        }

        return points;
    }

    public void drawFlightPath() {
        SimpleLineSymbol sls = new SimpleLineSymbol(Color.DKGRAY, 1f, SimpleLineSymbol.STYLE.DASH);
        Polyline pl = new Polyline();
        pl.startPath(-122.592901, 45.588995);
        pl.lineTo(-117.597651, 34.060681);

        Polyline plProj = (Polyline) GeometryEngine.project(pl, WGS84, WEB_MERCATOR);
        Graphic g = new Graphic(plProj, sls);
        graphicsLayer.addGraphic(g);
    }


    private int mPlaneGraphic = -Integer.MAX_VALUE;
    public void animateFlightPath() {
        final PictureMarkerSymbol pms = new PictureMarkerSymbol(getResources().getDrawable(R.drawable.airplane));
        final Handler handler = new Handler();
        final ArrayList<Point> points = getCoordinates(100);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < points.size(); i++) {
                    Point p = (Point) GeometryEngine.project(points.get(i), WGS84, WEB_MERCATOR);
                    final Graphic g = new Graphic(p, pms);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mPlaneGraphic != -Integer.MAX_VALUE) {
                                graphicsLayer.removeGraphic(mPlaneGraphic);
                            }
                            mPlaneGraphic = graphicsLayer.addGraphic(g);
                        }
                    });

                    Log.d(TAG, "derp!");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Pass
                    }
                }
            }
        }).start();
    }

    public Point createWgs84Point(double lat, double lng) {
        Point p = new Point(lng, lat);
        return (Point) GeometryEngine.project(p, WGS84, WEB_MERCATOR);
    }


    /**
     * Takes in the screen location of the point to identify the feature on map.
     *
     * @param x x co-ordinate of point
     * @param y y co-ordinate of point
     */
    private void identifyLocation(float x, float y) {
        // Hide the callout, if the callout from previous tap is still showing on map
        if (m_callout.isShowing()) {
            m_callout.hide();
        }

        // Find out if the user tapped on a feature
        SearchForFeature(x, y);

        // If the user tapped on a feature, then display information regarding the feature in the callout
        if (m_identifiedGraphic != null) {
            Point mapPoint = mMapView.toMapPoint(x, y);
            // Show Callout
            ShowCallout(m_callout, m_identifiedGraphic, mapPoint);
        }
    }

    /**
     * Sets the value of m_identifiedGraphic to the Graphic present on the location of screen tap
     *
     * @param x x co-ordinate of point
     * @param y y co-ordinate of point
     */
    private void SearchForFeature(float x, float y) {

        Point mapPoint = mMapView.toMapPoint(x, y);

        if (mapPoint != null) {
            for (Layer layer : mMapView.getLayers()) {
                if (layer != null && layer instanceof ArcGISFeatureLayer) {
                    ArcGISFeatureLayer fLayer = (ArcGISFeatureLayer) layer;
                    // Get the Graphic at location x,y
                    m_identifiedGraphic = GetFeature(fLayer, x, y);
                }
            }
        }
    }

    /**
     * Returns the Graphic present the location of screen tap
     *
     * @param fLayer
     * @param x x co-ordinate of point
     * @param y y co-ordinate of point
     *
     * @return Graphic at location x,y
     */
    private Graphic GetFeature(ArcGISFeatureLayer fLayer, float x, float y) {
        // Get the graphics near the Point.
        int[] ids = fLayer.getGraphicIDs(x, y, 10, 1);
        if (ids == null || ids.length == 0) {
            return null;
        }
        return fLayer.getGraphic(ids[0]);
    }

    /**
     * Shows the Attribute values for the Graphic in the Callout
     *
     * @param calloutView
     * @param graphic
     * @param mapPoint
     */
    private void ShowCallout(Callout calloutView, Graphic graphic, Point mapPoint) {
        // Fields: name, type, wiki_link

//        // Get the values of attributes for the Graphic
//        String cityName = (String) graphic.getAttributeValue("NAME");
//        String countryName = (String) graphic.getAttributeValue("COUNTRY");
//        String cityPopulationValue = (graphic.getAttributeValue("POPULATION")).toString();
//
//        // Set callout properties
//        calloutView.setCoordinates(mapPoint);
//        calloutView.setStyle(m_calloutStyle);
//        calloutView.setMaxWidth(325);
//
//        // Compose the string to display the results
//        StringBuilder cityCountryName = new StringBuilder();
//        cityCountryName.append(cityName);
//        cityCountryName.append(", ");
//        cityCountryName.append(countryName);
//
//        TextView calloutTextLine1 = (TextView) findViewById(R.id.citycountry);
//        calloutTextLine1.setText(cityCountryName);
//
//        // Compose the string to display the results
//        StringBuilder cityPopulation = new StringBuilder();
//        cityPopulation.append("Population: ");
//        cityPopulation.append(cityPopulationValue);
//
//        TextView calloutTextLine2 = (TextView) findViewById(R.id.population);
//        calloutTextLine2.setText(cityPopulation);
//        calloutView.setContent(calloutContent);
//        calloutView.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.unpause();
    }
}