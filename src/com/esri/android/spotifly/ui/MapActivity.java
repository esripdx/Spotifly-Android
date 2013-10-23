package com.esri.android.spotifly.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
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
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.gdb.GdbFeatureTable;
import com.esri.core.gdb.Geodatabase;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Point;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Graphic;
import com.esri.core.tasks.gdb.GenerateGeodatabaseParameters;
import com.esri.core.tasks.gdb.GeodatabaseStatusCallback;
import com.esri.core.tasks.gdb.GeodatabaseStatusInfo;
import com.esri.core.tasks.gdb.GeodatabaseTask;
import com.esri.core.tasks.gdb.SyncModel;

public class MapActivity extends Activity {
    public String TAG = "MapActivity";

    MapView mMapView;
    ArcGISFeatureLayer featureLayer;
    ArcGISTiledMapServiceLayer tileLayer;

    GraphicsLayer graphicsLayer;
    ProgressDialog progress;
    private int m_calloutStyle;
    private Callout m_callout;
    private ViewGroup calloutContent;
    private boolean m_isMapLoaded;
    private Graphic m_identifiedGraphic;
    private String featureServiceURL;
    private GeodatabaseTask gdbTask;
    private String gdbFileName = "geodatabase.db";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mMapView = (MapView) findViewById(R.id.map);

        mMapView.enableWrapAround(true);

        // Get the feature service URL from values->strings.xml
        featureServiceURL = "http://services.arcgis.com/rOo16HdIMeOBI4Mb/arcgis/rest/services/Spotifly/FeatureServer/0";

        // Add Tile layer to the MapView
        tileLayer = new ArcGISTiledMapServiceLayer(this.getResources().getString(R.string.tileServiceURL));
        mMapView.addLayer(tileLayer);
        // Add Feature layer to the MapView
        featureLayer = new ArcGISFeatureLayer(featureServiceURL, ArcGISFeatureLayer.MODE.ONDEMAND);
        mMapView.addLayer(featureLayer);
        // Add Graphics layer to the MapView
        graphicsLayer = new GraphicsLayer();
        mMapView.addLayer(graphicsLayer);

        // Set the initial extent of the map
        final Envelope initExtent =
                new Envelope(-1557669.6939985836, -4115210.743119574, 9205833.473803047, 1.3524975004110876E7);
        mMapView.setExtent(initExtent);

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

        gdbTask = new GeodatabaseTask(featureServiceURL, null, new CallbackListener<FeatureServiceInfo>() {
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

                    gdbTask.submitGenerateGeodatabaseJobAndDownload(params, gdbFileName, new GeodatabaseStatusCallback() {
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