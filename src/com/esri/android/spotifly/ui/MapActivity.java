/* Copyright 2013 ESRI
 *
 * All rights reserved under the copyright laws of the United States
 * and applicable international laws, treaties, and conventions.
 *
 * You may freely redistribute and use this sample code, with or
 * without modification, provided you include the original copyright
 * notice and use restrictions.
 *
 * See the Sample code usage restrictions document for further information.
 *
 */

package com.esri.android.spotifly.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.esri.android.map.Callout;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.android.spotifly.R;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Point;
import com.esri.core.map.FeatureSet;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.tasks.ags.query.Query;
import com.esri.core.tasks.ags.query.QueryTask;

public class MapActivity extends Activity {

	MapView map;

	ArcGISFeatureLayer featureLayer;

	ArcGISTiledMapServiceLayer tileLayer;

	GraphicsLayer graphicsLayer;

	private int m_calloutStyle;

	private Callout m_callout;

	private ViewGroup calloutContent;

	private boolean m_isMapLoaded;

	private Graphic m_identifiedGraphic;

	private String featureServiceURL;

	ProgressDialog progress;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		map = (MapView) findViewById(R.id.map);

		map.enableWrapAround(true);

		// Get the feature service URL from values->strings.xml
		featureServiceURL = this.getResources().getString(
				R.string.featureServiceURL);

		// Add Tile layer to the MapView
		tileLayer = new ArcGISTiledMapServiceLayer(this.getResources()
				.getString(R.string.tileServiceURL));
		map.addLayer(tileLayer);
		// Add Feature layer to the MapView
		featureLayer = new ArcGISFeatureLayer(featureServiceURL,
				ArcGISFeatureLayer.MODE.ONDEMAND);
		map.addLayer(featureLayer);
		// Add Graphics layer to the MapView
		graphicsLayer = new GraphicsLayer();
		map.addLayer(graphicsLayer);

		// Set the initial extent of the map
		final Envelope initExtent = new Envelope(-1557669.6939985836,
				-4115210.743119574, 9205833.473803047, 1.3524975004110876E7);
		map.setExtent(initExtent);

		// Get the MapView's callout from xml->identify_calloutstyle.xml
		m_calloutStyle = R.xml.identify_calloutstyle;
		LayoutInflater inflater = getLayoutInflater();
		m_callout = map.getCallout();
		// Get the layout for the Callout from
		// layout->identify_callout_content.xml
		calloutContent = (ViewGroup) inflater.inflate(
				R.layout.identify_callout_content, null);
		m_callout.setContent(calloutContent);

		map.setOnStatusChangedListener(new OnStatusChangedListener() {

			private static final long serialVersionUID = 1L;

			@Override
			public void onStatusChanged(Object source, STATUS status) {
				// Check to see if map has successfully loaded
				if ((source == map) && (status == STATUS.INITIALIZED)) {
					// Set the flag to true
					m_isMapLoaded = true;
				}
			}
		});

		map.setOnSingleTapListener(new OnSingleTapListener() {

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
	}

	/**
	 * Takes in the screen location of the point to identify the feature on map.
	 * 
	 * @param x
	 *            x co-ordinate of point
	 * @param y
	 *            y co-ordinate of point
	 */
	private void identifyLocation(float x, float y) {

		// Hide the callout, if the callout from previous tap is still showing
		// on map
		if (m_callout.isShowing()) {
			m_callout.hide();
		}

		// Find out if the user tapped on a feature
		SearchForFeature(x, y);

		// If the user tapped on a feature, then display information regarding
		// the feature in the callout
		if (m_identifiedGraphic != null) {
			Point mapPoint = map.toMapPoint(x, y);
			// Show Callout
			ShowCallout(m_callout, m_identifiedGraphic, mapPoint);
		}
	}

	/**
	 * Sets the value of m_identifiedGraphic to the Graphic present on the
	 * location of screen tap
	 * 
	 * @param x
	 *            x co-ordinate of point
	 * @param y
	 *            y co-ordinate of point
	 */
	private void SearchForFeature(float x, float y) {

		Point mapPoint = map.toMapPoint(x, y);

		if (mapPoint != null) {

			for (Layer layer : map.getLayers()) {
				if (layer == null)
					continue;

				if (layer instanceof ArcGISFeatureLayer) {
					ArcGISFeatureLayer fLayer = (ArcGISFeatureLayer) layer;
					// Get the Graphic at location x,y
					m_identifiedGraphic = GetFeature(fLayer, x, y);
				} else
					continue;
			}
		}
	}

	/**
	 * Returns the Graphic present the location of screen tap
	 * 
	 * @param fLayer
	 * @param x
	 *            x co-ordinate of point
	 * @param y
	 *            y co-ordinate of point
	 * @return Graphic at location x,y
	 */
	private Graphic GetFeature(ArcGISFeatureLayer fLayer, float x, float y) {

		// Get the graphics near the Point.
		int[] ids = fLayer.getGraphicIDs(x, y, 10, 1);
		if (ids == null || ids.length == 0) {
			return null;
		}
		Graphic g = fLayer.getGraphic(ids[0]);
		return g;
	}

	/**
	 * Shows the Attribute values for the Graphic in the Callout
	 * 
	 * @param calloutView
	 * @param graphic
	 * @param mapPoint
	 */
	private void ShowCallout(Callout calloutView, Graphic graphic,
			Point mapPoint) {

		// Get the values of attributes for the Graphic
		String cityName = (String) graphic.getAttributeValue("NAME");
		String countryName = (String) graphic.getAttributeValue("COUNTRY");
		String cityPopulationValue = ((Double) graphic
				.getAttributeValue("POPULATION")).toString();

		// Set callout properties
		calloutView.setCoordinates(mapPoint);
		calloutView.setStyle(m_calloutStyle);
		calloutView.setMaxWidth(325);

		// Compose the string to display the results
		StringBuilder cityCountryName = new StringBuilder();
		cityCountryName.append(cityName);
		cityCountryName.append(", ");
		cityCountryName.append(countryName);

		TextView calloutTextLine1 = (TextView) findViewById(R.id.citycountry);
		calloutTextLine1.setText(cityCountryName);

		// Compose the string to display the results
		StringBuilder cityPopulation = new StringBuilder();
		cityPopulation.append("Population: ");
		cityPopulation.append(cityPopulationValue);

		TextView calloutTextLine2 = (TextView) findViewById(R.id.population);
		calloutTextLine2.setText(cityPopulation);
		calloutView.setContent(calloutContent);
		calloutView.show();
	}

	/**
	 * Run the query task on the feature layer and put the result on the map.
	 */
	private class RunQueryFeatureLayerTask extends
			AsyncTask<String, Void, Graphic[]> {

		@Override
		protected void onPreExecute() {
			progress = ProgressDialog.show(MapActivity.this, "",
					"Please wait....query task is executing");
		}

		@Override
		protected Graphic[] doInBackground(String... params) {

			String whereClause = "COUNTRY='" + params[0] + "'";

			// Define a new query and set parameters
			Query query = new Query();
			query.setWhere(whereClause);
			query.setReturnGeometry(true);
            query.setGeometry(new Envelope());

			// Define the new instance of QueryTask
			QueryTask qTask = new QueryTask(featureServiceURL);
			FeatureSet fs = null;

			try {
				// run the querytask
				fs = qTask.execute(query);
				// Get the graphics from the result feature set
				Graphic[] grs = fs.getGraphics();
				return grs;

			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Graphic[] graphics) {

			// Remove the result from previously run query task
			graphicsLayer.removeAll();
			// Define a new marker symbol for the result graphics
			SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.BLUE, 6,
					SimpleMarkerSymbol.STYLE.CIRCLE);
			Graphic[] resultGraphics = new Graphic[graphics.length];
			int count = 0;
			// Envelope to focus on the map extent on the results
			Envelope extent = new Envelope();

			for (Graphic gr : graphics) {
				Graphic g = new Graphic(gr.getGeometry(), sms);
				resultGraphics[count] = g;
				count++;
				Point p = (Point) gr.getGeometry();
				extent.merge(p);
			}
			// Add result graphics on the map
			graphicsLayer.addGraphics(resultGraphics);
			// Set the map extent to the envelope containing the result graphics
			map.setExtent(extent, 100);
			// Disable the progress dialog
			progress.dismiss();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		map.pause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		map.unpause();
	}

}