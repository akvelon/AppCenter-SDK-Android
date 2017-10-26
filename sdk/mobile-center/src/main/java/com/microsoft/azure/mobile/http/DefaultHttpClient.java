package com.microsoft.azure.mobile.http;

import android.net.TrafficStats;
import android.os.AsyncTask;
import android.support.annotation.VisibleForTesting;

import com.microsoft.azure.mobile.utils.HandlerUtils;
import com.microsoft.azure.mobile.utils.MobileCenterLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static com.microsoft.azure.mobile.MobileCenter.LOG_TAG;
import static java.lang.Math.max;

public class DefaultHttpClient implements HttpClient {

    public static final String METHOD_GET = "GET";

    public static final String METHOD_POST = "POST";

    /**
     * Thread stats tag for Mobile Center HTTP calls.
     */
    private static final int THREAD_STATS_TAG = 0xD83DDC19;

    /**
     * Content type header value.
     */
    private static final String CONTENT_TYPE_VALUE = "application/json";

    /**
     * Default string builder capacity.
     */
    private static final int DEFAULT_STRING_BUILDER_CAPACITY = 16;

    /**
     * Content type header key.
     */
    private static final String CONTENT_TYPE_KEY = "Content-Type";

    /**
     * Character encoding.
     */
    private static final String CHARSET_NAME = "UTF-8";

    /**
     * Read buffer size.
     */
    private static final int READ_BUFFER_SIZE = 1024;

    /**
     * HTTP connection timeout.
     */
    private static final int CONNECT_TIMEOUT = 60000;

    /**
     * HTTP read timeout.
     */
    private static final int READ_TIMEOUT = 20000;

    /**
     * Dump stream to string.
     *
     * @param urlConnection URL connection.
     * @return dumped string.
     * @throws IOException if an error occurred.
     */
    private static String dump(HttpURLConnection urlConnection) throws IOException {

        /*
         * Though content length header value is less than actual payload length (gzip), we want to init
         * buffer with a reasonable start size to optimize (default is 16 and is way too low for this
         * use case).
         */
        StringBuilder builder = new StringBuilder(max(urlConnection.getContentLength(), DEFAULT_STRING_BUILDER_CAPACITY));
        InputStream stream;
        int status = urlConnection.getResponseCode();
        if (status >= 200 && status < 400) {
            stream = urlConnection.getInputStream();
        } else {
            stream = urlConnection.getErrorStream();
        }
        try {
            InputStreamReader in = new InputStreamReader(stream, CHARSET_NAME);
            char[] buffer = new char[READ_BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                builder.append(buffer, 0, len);
            }
            return builder.toString();
        } finally {
            stream.close();
        }
    }

    /**
     * Do call and tag socket to avoid strict mode issue.
     */
    private static String doCall(String urlString, String method, Map<String, String> headers, CallTemplate callTemplate) throws Exception {
        TrafficStats.setThreadStatsTag(THREAD_STATS_TAG);
        try {
            return doHttpCall(urlString, method, headers, callTemplate);
        } finally {
            TrafficStats.clearThreadStatsTag();
        }
    }

    /**
     * Do http call.
     */
    private static String doHttpCall(String urlString, String method, Map<String, String> headers, CallTemplate callTemplate) throws Exception {

        /* HTTP session. */
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {

            /* Configure connection timeouts. */
            urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
            urlConnection.setReadTimeout(READ_TIMEOUT);
            urlConnection.setRequestMethod(method);

            /* Set headers. */
            urlConnection.setRequestProperty(CONTENT_TYPE_KEY, CONTENT_TYPE_VALUE);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }

            /* Before send. */
            if (callTemplate != null) {
                callTemplate.onBeforeCalling(url, headers);
            }

            /* Build payload. */
            if (method.equals(METHOD_POST) && callTemplate != null) {
                String payload = callTemplate.buildRequestBody();
                MobileCenterLog.verbose(LOG_TAG, payload);

                /* Send payload through the wire. */
                byte[] binaryPayload = payload.getBytes(CHARSET_NAME);
                urlConnection.setDoOutput(true);
                urlConnection.setFixedLengthStreamingMode(binaryPayload.length);
                OutputStream out = urlConnection.getOutputStream();
                out.write(binaryPayload);
                out.close();
            }

            /* Read response. */
            int status = urlConnection.getResponseCode();
            String response = dump(urlConnection);
            String contentType = urlConnection.getHeaderField("Content-Type");
            String logPayload;
            if (contentType == null || contentType.startsWith("text/") || contentType.startsWith("application/")) {
                logPayload = response;
            } else {
                logPayload = "<binary>";
            }
            MobileCenterLog.verbose(LOG_TAG, "HTTP response status=" + status + " payload=" + logPayload);

            /* Accept all 2xx codes. */
            if (status >= 200 && status < 300) {
                return response;
            }

            /* Generate exception on failure. */
            throw new HttpException(status, response);
        } finally {

            /* Release connection. */
            urlConnection.disconnect();
        }
    }

    @Override
    public ServiceCall callAsync(String url, String method, Map<String, String> headers, CallTemplate callTemplate, final ServiceCallback serviceCallback) {
        final Call call = new Call(url, method, headers, callTemplate, serviceCallback);
        try {
            call.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (final RejectedExecutionException e) {

            /*
             * When executor saturated (shared with app), we should use the retry mechanism
             * rather than creating more threads to avoid putting too much pressure on the hosting app.
             * Also we need to return the method before calling the listener,
             * so we post the callback on handler to make sure of that.
             */
            HandlerUtils.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    serviceCallback.onCallFailed(e);
                }
            });
        }
        return new ServiceCall() {

            @Override
            public void cancel() {
                if (!call.isCancelled()) {
                    call.cancel(true);
                }
            }
        };
    }

    @Override
    public void close() throws IOException {

        /* No-op. A decorator can take care of tracking calls to cancel. */
    }

    @VisibleForTesting
    static class Call extends AsyncTask<Void, Void, Object> {

        private final String mUrl;

        private final String mMethod;

        private final Map<String, String> mHeaders;

        private final CallTemplate mCallTemplate;

        private final ServiceCallback mServiceCallback;

        public Call(String url, String method, Map<String, String> headers, CallTemplate callTemplate, ServiceCallback serviceCallback) {
            mUrl = url;
            mMethod = method;
            mHeaders = headers;
            mCallTemplate = callTemplate;
            mServiceCallback = serviceCallback;
        }

        @Override
        protected Object doInBackground(Void... params) {
            try {
                return doCall(mUrl, mMethod, mHeaders, mCallTemplate);
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Exception) {
                mServiceCallback.onCallFailed((Exception) result);
            } else {
                mServiceCallback.onCallSucceeded(result.toString());
            }
        }
    }
}
