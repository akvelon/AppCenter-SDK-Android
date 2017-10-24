package com.microsoft.azure.mobile.http;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.IOException;

/**
 * HTTP exception.
 */
public class HttpException extends IOException {

    /**
     * HTTP status code.
     */
    private final int statusCode;

    /**
     * HTTP payload.
     */
    private final String payload;

    /**
     * Init with empty response body.
     *
     * @param status HTTP status code.
     */
    public HttpException(int status) {
        this(status, "");
    }

    /**
     * Init.
     *
     * @param status  HTTP status code.
     * @param payload HTTP payload.
     */
    @SuppressWarnings("WeakerAccess")
    public HttpException(int status, @NonNull String payload) {
        super(getDetailMessage(status, payload));
        this.payload = payload;
        this.statusCode = status;
    }

    @NonNull
    private static String getDetailMessage(int status, @NonNull String payload) {
        if (TextUtils.isEmpty(payload)) {
            return String.valueOf(status);
        }
        return status + " - " + payload;
    }

    /**
     * Get the HTTP status code.
     *
     * @return HTTP status code.
     */
    @SuppressWarnings("WeakerAccess")
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Get the HTTP payload (response body).
     *
     * @return HTTP payload. Can be empty string.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public String getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpException that = (HttpException) o;
        return statusCode == that.statusCode && payload.equals(that.payload);
    }

    @Override
    public int hashCode() {
        int result = statusCode;
        result = 31 * result + payload.hashCode();
        return result;
    }
}