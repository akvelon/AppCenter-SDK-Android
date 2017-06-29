package com.microsoft.azure.mobile.push.ingestion.models;

import com.microsoft.azure.mobile.ingestion.models.AbstractLog;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

/**
 * Push installation log.
 */

public class PushInstallationLog extends AbstractLog {

    public static final String TYPE = "push_installation";

    private static final String PUSH_TOKEN = "push_token";

    /**
     * The PNS handle for this installation.
     */
    private String pushToken;

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Get the push token value.
     *
     * @return the push token value
     */
    public String getPushToken() {
        return this.pushToken;
    }

    /**
     * Set the push token value.
     *
     * @param pushToken push token value to set
     */
    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    @Override
    public void read(JSONObject object) throws JSONException {
        super.read(object);
        setPushToken(object.getString(PUSH_TOKEN));
    }

    @Override
    public void write(JSONStringer writer) throws JSONException {
        super.write(writer);
        writer.key(PUSH_TOKEN).value(getPushToken());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PushInstallationLog pushInstallationLog = (PushInstallationLog) o;

        return pushToken != null ? pushToken.equals(pushInstallationLog.pushToken) : pushInstallationLog.pushToken == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (pushToken != null ? pushToken.hashCode() : 0);
        return result;
    }
}
