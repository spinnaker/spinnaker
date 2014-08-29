package com.netflix.spinnaker.amos.aws;

/**
 * An implementation of {@link com.netflix.spinnaker.amos.aws.AmazonCredentials} that is decorated with Netflix concepts like Edda, Discovery, Front50,
 */
public class NetflixAmazonCredentials extends AmazonCredentials {
    private String edda;
    private Boolean eddaEnabled;
    private String discovery;
    private Boolean discoveryEnabled;
    private String front50;
    private Boolean front50Enabled;

    public String getEdda() {
        return edda;
    }

    public String getDiscovery() {
        return discovery;
    }

    public String getFront50() {
        return front50;
    }

    public void setEdda(String edda) {
        this.edda = edda;
    }

    public void setDiscovery(String discovery) {
        this.discovery = discovery;
    }

    public void setFront50(String front50) {
        this.front50 = front50;
    }

    public Boolean getEddaEnabled() {
        if (eddaEnabled != null && edda != null) {
            return eddaEnabled;
        } else {
            return eddaEnabled == null && edda != null && edda.length() > 0;
        }
    }

    public void setEddaEnabled(Boolean eddaEnabled) {
        this.eddaEnabled = eddaEnabled;
    }

    public Boolean getDiscoveryEnabled() {
        if (discoveryEnabled != null && discovery != null) {
            return discoveryEnabled;
        } else {
            return discoveryEnabled == null && discovery != null && discovery.length() > 0;
        }
    }

    public void setDiscoveryEnabled(Boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    public Boolean getFront50Enabled() {
        if (front50Enabled != null && front50 != null) {
            return front50Enabled;
        } else {
            return front50Enabled == null && front50 != null && front50.length() > 0;
        }
    }

    public void setFront50Enabled(Boolean front50Enabled) {
        this.front50Enabled = front50Enabled;
    }
}
