package com.esri.android.spotifly.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A pack of helpful JSON/Http utility methods.
 *
 * <p>Use this to POST JSON (or Form-Encoded data), or make a GET to a server that you expect to return JSON.
 */
public final class NetUtils {
    private static final String TAG = "NetUtils";
    private static final String HTTP_CLIENT_USER_AGENT = "ArcGISAndroidSDK";
    private static final int HTTP_CLIENT_CONNECTION_TIMEOUT = 5000;
    private static final int HTTP_CLIENT_SOCKET_TIMEOUT = 7000;
    private static ExecutorService sThreadExecutor = Executors.newCachedThreadPool();
    private static ArrayList<UdpThread> sUdpThreads = new ArrayList<UdpThread>();

    private NetUtils() {}

    /**
     * Provide your own ExecutorService if you don't want this class to maintain its own cached thread pool.
     *
     * @param threadExecutor
     */
    public static void setThreadExecutor(ExecutorService threadExecutor) {
        if (sThreadExecutor != null) {
            sThreadExecutor.shutdown();
        }

        sThreadExecutor = threadExecutor;
    }

    public static void openUdpConnection(final Context context, final String host, final int port,
                                         final UdpListener listener, final boolean shouldListen) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null.");
        }

        if (TextUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Host cannot be empty.");
        }

        if (port < 0) {
            throw new IllegalArgumentException("Port appears to be invalid.");
        }

        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (!isConnected(context)) {
            listener.onError(new Exception("No connection available."));
            return;
        }

        final Handler handler = new Handler(context.getMainLooper());
        sThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final DatagramSocket socket = new DatagramSocket();
                    socket.connect(new InetSocketAddress(host, port));

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onConnect(socket);
                        }
                    });

                    if (shouldListen) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listenToUdpSocket(socket, listener);
                            }
                        });
                    }
                } catch (final SocketException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(e);
                        }
                    });
                }
            }
        });
    }

    public static void listenToUdpSocket(final DatagramSocket socket, final UdpListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null.");
        }

        UdpThread udpThread = new UdpThread(socket, listener);

        sUdpThreads.add(udpThread);

        udpThread.start();
    }

    public static void haltAllUdpReceiverThreads() {
        if (sUdpThreads != null) {
            for (UdpThread udpThread : sUdpThreads) {
                udpThread.halt();
            }
        }
    }

    public static void fetchDrawable(final Context context, final String url,
                                     final SimpleCompletionListener<BitmapDrawable> listener) {
        final Handler handler = new Handler(context.getMainLooper());
        sThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                InputStream input;

                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.connect();
                    input = connection.getInputStream();
                } catch (final Exception e) {

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(e);
                        }
                    });

                    return;
                }

                final Bitmap image = BitmapFactory.decodeStream(input);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onComplete(new BitmapDrawable(context.getResources(), image));
                    }
                });
            }
        });
    }

    /**
     * Send a GET request.
     *
     * @param args optional map of query arguments to be encoded and appended to the path.
     * @param headers optional headers to send with the request
     * @param listener a {@link JsonRequestListener}.
     */
    public static void getJson(Context context, String url, Map<String, String> args, Header[] headers,
                               JsonRequestListener listener) {
        HttpGet request = new HttpGet();
        try {
            String qs = urlencode(args);
            if (!TextUtils.isEmpty(qs)) {
                url += "?" + qs;
            }
            request.setURI(new URI(url));
            request.setHeaders(headers);
        } catch (URISyntaxException e) {
            listener.onFailure(new Exception(e));
            return;
        }
        runHttpRequest(context, request, listener);
    }

    /**
     * Send a POST request with a "Content-Type" value of "application/json".
     *
     * @param json json POST body
     * @param headers optional headers to send with the request
     * @param listener a {@link JsonRequestListener}.
     */
    public static void jsonPost(Context context, String url, JSONObject json, Header[] headers,
                                JsonRequestListener listener) {
        StringEntity entity;
        try {
            if (json == null) {
                entity = new StringEntity("{}", HTTP.UTF_8);
            } else {
                entity = new StringEntity(json.toString(), HTTP.UTF_8);
            }
        } catch (UnsupportedEncodingException e) {
            listener.onFailure(new Exception(e));
            return;
        }
        runPostRequest(context, url, entity, headers, "application/json", listener);
    }

    /**
     * Send a POST request with a "Content-Type" value of "application/json".
     *
     * @param json json POST body
     * @param headers optional headers to send with the request
     * @param listener a {@link JsonRequestListener}.
     */
    public static void jsonPost(Context context, String url, JSONArray json, Header[] headers,
                                JsonRequestListener listener) {
        StringEntity entity;
        try {
            if (json == null) {
                entity = new StringEntity("{}", HTTP.UTF_8);
            } else {
                entity = new StringEntity(json.toString(), HTTP.UTF_8);
            }
        } catch (UnsupportedEncodingException e) {
            listener.onFailure(new Exception(e));
            return;
        }
        runPostRequest(context, url, entity, headers, "application/json", listener);
    }

    /**
     * Send a POST request with a "Content-Type" value of "application/x-www-form-urlencoded".
     *
     * @param fields fields to be form encoded
     * @param headers optional headers to send with the request
     * @param listener a {@link JsonRequestListener}.
     */
    public static void formEncodedPost(Context context, String url, Map<String, String> fields, Header[] headers,
                                       JsonRequestListener listener) {
        StringEntity entity;
        try {
            entity = new StringEntity(NetUtils.urlencode(fields), HTTP.UTF_8);
        } catch (UnsupportedEncodingException e) {
            listener.onFailure(new Exception(e));
            return;
        }
        runPostRequest(context, url, entity, headers, "application/x-www-form-urlencoded", listener);
    }

    /**
     * Send a POST request with a "Content-Type" value of "application/json".
     *
     * <p>This method adds the Access Token header to the request.
     *
     * @param entity a StringEntity containing a serialized JSONObject or JSONArray.
     * @param headers optional headers to send with the request
     * @param listener a {@link JsonRequestListener}.
     */
    private static void runPostRequest(Context context, String url, StringEntity entity, Header[] headers,
                                       String contentType, JsonRequestListener listener) {
        HttpPost request = new HttpPost();
        try {
            request.setURI(new URI(url));
            request.setEntity(entity);
            request.setHeaders(headers);
            // Ensure the Content-Type is set as expected.
            request.setHeader(HTTP.CONTENT_TYPE, contentType);

        } catch (URISyntaxException e) {
            listener.onFailure(new Exception(e));
            return;
        }
        runHttpRequest(context, request, listener);
    }

    /**
     * Run a raw HttpRequest synchronously on the calling thread.
     *
     * @param request an {@link HttpRequestBase} object.
     * @param listener a {@link JsonRequestListener} object.
     */
    private static void runHttpRequest(Context context, final HttpRequestBase request,
                                       final JsonRequestListener listener) {
        // Generate a unique ID for this request.
        final String requestId = RandomString.getString(5);

        // Initialize the request
        Log.v(TAG, String.format("[%s] Executing new API request.", requestId));

        // Check for a valid request URI
        if (request.getURI() == null) {
            throw new IllegalArgumentException("Cannot execute request with null URI!");
        }

        // Check for an active network connection
        if (!isConnected(context)) {
            // TODO: Pass a more appropriate exception type to the listener.
            Log.v(TAG, String.format("[%s] Request failed! No active network connection.", requestId));
            listener.onFailure(new Exception("No active network connection!"));
            return;
        }

        final JsonRequestListener threadSafeListener = getThreadSafeListener(context, listener);

        sThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DefaultHttpClient client = getHttpClient();
                    Log.v(TAG, String.format("[%s] Sending request to '%s'.", requestId, request.getURI()));

                    if (request instanceof HttpPost) {
                        Log.v(TAG, String.format("[%s] Request post body: %s", requestId,
                                EntityUtils.toString(((HttpPost) request).getEntity())));
                    }
                    // Execute the request
                    HttpResponse response = client.execute(request);
                    StatusLine status = response.getStatusLine();
                    String entity = EntityUtils.toString(response.getEntity());

                    Log.v(TAG, String.format("[%s] Response received with status '%s'.", requestId, status));
                    Log.v(TAG, String.format("[%s] Response entity: '%s'.", requestId, entity));

                    // Consume the response content
                    JSONObject json = new JSONObject(entity);

                    if (status.getStatusCode() == HttpStatus.SC_OK) {
                        Log.v(TAG, String.format("[%s] Request was successful!", requestId));
                        threadSafeListener.onSuccess(json);
                    } else {
                        Log.v(TAG, String.format("[%s] Request completed with status '%s'!", requestId, status));
                        threadSafeListener.onError(json, status);
                    }
                } catch (Exception e) {
                    Log.d(TAG, String.format("[%s] Request failed with error '%s'!", requestId, e.getMessage()));
                    threadSafeListener.onFailure(new Exception(e));
                }
            }
        });
    }

    /**
     * Encode a {@link Map} of query arguments for use in a GET request.
     *
     * @see java.net.URLEncoder#encode
     *
     * @param args a Map of query arguments
     * @return the URL encoded String.
     */
    public static String urlencode(Map<String, String> args) {
        String qs = "";
        if (args != null && !args.isEmpty()) {
            for (Map.Entry<String, String> arg : args.entrySet()) {
                qs += String.format("&%s=%s", URLEncoder.encode(arg.getKey()),
                        URLEncoder.encode(arg.getValue()));
            }

            // Remove the leading ampersand
            qs = qs.substring(1);
        }
        return qs;
    }

    /**
     * Determine if the device has an active network connection.
     *
     * @return true if the network is connected, false if otherwise.
     */
    public static boolean isConnected(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null.");
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                return activeNetwork.isConnected();
            }
        }
        return false;
    }

    private static JsonRequestListener getThreadSafeListener(Context context, final JsonRequestListener listener) {
        final Handler handler = new Handler(context.getMainLooper());
        return new JsonRequestListener() {
            @Override
            public void onSuccess(final JSONObject json) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSuccess(json);
                    }
                });
            }

            @Override
            public void onFailure(final Throwable e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFailure(e);
                    }
                });
            }

            @Override
            public void onError(final JSONObject json, final StatusLine status) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(json, status);
                    }
                });
            }
        };
    }

    /**
     * This is a helper method that creates an instance of {@link DefaultHttpClient} with some default parameters.
     *
     * <p>Make sure you call consumeContent on the returned {@link org.apache.http.HttpEntity} if you want to reuse a
     * connection.
     *
     * @return an instance of DefaultHttpClient.
     */
    private static DefaultHttpClient getHttpClient() {
        DefaultHttpClient client = new DefaultHttpClient();

        // Set default client parameters
        HttpParams params = new BasicHttpParams();
        params.setParameter(CoreProtocolPNames.USER_AGENT, HTTP_CLIENT_USER_AGENT);
        HttpConnectionParams.setConnectionTimeout(params, HTTP_CLIENT_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, HTTP_CLIENT_SOCKET_TIMEOUT);
        client.setParams(params);

        return client;
    }

    /**
     * The interface that defines possible outcomes of an HTTP request so that custom behavior can be implemented when those
     * events occur.
     */
    public interface JsonRequestListener {
        /**
         * The server returned an {@link org.apache.http.HttpStatus#SC_OK} response.
         *
         * @param json the {@link org.json.JSONObject} parsed from the raw HttpResponse.
         */
        public void onSuccess(JSONObject json);

        /**
         * The server did return a response, but the response code was not {@link org.apache.http.HttpStatus#SC_OK} and
         * indicates some error condition.
         *
         * @param json the {@link JSONObject} parsed from the raw HttpResponse.
         * @param status the StatusLine returned from the raw HttpResponse.
         */
        public void onError(JSONObject json, StatusLine status);

        /**
         * The request failed to be sent due to some local error condition or the response could not be parsed.
         *
         * @param error the caught exception; otherwise null;
         */
        public void onFailure(Throwable error);
    }

    public interface UdpListener {
        public void onConnect(DatagramSocket socket);
        public void onError(Throwable error);
        public void onReceive(DatagramPacket packet);
    }

    public static interface SimpleCompletionListener<T> {
        public void onComplete(T result);
        public void onError(Throwable error);
    }

    public static class RandomString {
        private static final char[] symbols = new char[36];
        private static final Random random = new Random();

        static {
            for (int idx = 0; idx < 10; ++idx) {
                symbols[idx] = (char) ('0' + idx);
            }
            for (int idx = 10; idx < 36; ++idx) {
                symbols[idx] = (char) ('a' + idx - 10);
            }
        }

        private RandomString() {}

        /** Generate an insecure random alpha-numeric String of the length given in the constructor. */
        public static String getString(int length) {
            char[] buf = new char[length];
            for (int idx = 0; idx < buf.length; ++idx) {
                buf[idx] = symbols[random.nextInt(symbols.length)];
            }
            return new String(buf);
        }
    }

    private static class UdpThread extends Thread {
        private DatagramSocket mSocket;
        private UdpListener mListener;
        private boolean mCancelled = false;

        public UdpThread(DatagramSocket socket, UdpListener listener) {
            mSocket = socket;
            mListener = listener;
        }

        public void halt() {
            mCancelled = true;
        }

        @Override
        public void run() {
            while (!mCancelled) {
                byte[] bytes = new byte[1024];
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);

                try {
                    mSocket.receive(packet);
                } catch (IOException e) {
                    mListener.onError(e);
                }

                mListener.onReceive(packet);
            }

            mSocket.close();
        }
    }
}
