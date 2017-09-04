package com.microsoft.azure.mobile.ingestion.models;

import com.microsoft.azure.mobile.ingestion.models.json.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.util.List;

/**
 * Describe a MobileCenter.start call from the SDK.
 */
public class StartServiceLog extends AbstractLog {

    /**
     * Log type.
     */
    public static final String TYPE = "start_service";

    private static final String SERVICES = "services";

    /**
     * The list of services of the MobileCenter start call.
     */
    private List<String> services;

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Get the services value.
     *
     * @return the services value
     */
    public List<String> getServices() {
        return this.services;
    }

    /**
     * Set the services value.
     *
     * @param services the services value to set
     */
    public void setServices(List<String> services) {
        this.services = services;
    }

    @Override
    public void read(JSONObject object) throws JSONException {
        super.read(object);
        setServices(JSONUtils.readStringArray(object, SERVICES));
    }

    @Override
    public void write(JSONStringer writer) throws JSONException {
        super.write(writer);
        JSONUtils.writeStringArray(writer, SERVICES, getServices());
    }

    @Override
    @SuppressWarnings("SimplifiableIfStatement")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        StartServiceLog that = (StartServiceLog) o;
        return services != null ? services.equals(that.services) : that.services == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (services != null ? services.hashCode() : 0);
        return result;
    }
}
