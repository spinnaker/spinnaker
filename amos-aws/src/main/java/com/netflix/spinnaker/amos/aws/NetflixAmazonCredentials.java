package com.netflix.spinnaker.amos.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * An implementation of {@link com.netflix.spinnaker.amos.aws.AmazonCredentials} that is decorated with Netflix concepts like Edda, Discovery, Front50,
 */
public class NetflixAmazonCredentials extends AmazonCredentials {
    private final String edda;
    private final boolean eddaEnabled;
    private final String discovery;
    private final boolean discoveryEnabled;
    private final String front50;
    private final boolean front50Enabled;

    public NetflixAmazonCredentials(@JsonProperty("name") String name,
                                    @JsonProperty("accountId") String accountId,
                                    @JsonProperty("defaultKeyPair") String defaultKeyPair,
                                    @JsonProperty("regions") List<AWSRegion> regions,
                                    @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership,
                                    @JsonProperty("edda") String edda,
                                    @JsonProperty("eddaEnabled") Boolean eddaEnabled,
                                    @JsonProperty("discovery") String discovery,
                                    @JsonProperty("discoveryEnabled") Boolean discoveryEnabled,
                                    @JsonProperty("front50") String front50,
                                    @JsonProperty("front50Enabled") Boolean front50Enabled) {
        this(name, accountId, defaultKeyPair, regions, requiredGroupMembership, null, edda, eddaEnabled, discovery, discoveryEnabled, front50, front50Enabled);
    }

    private static boolean flagValue(String serviceUrl, Boolean flag) {
        return (!(serviceUrl == null || serviceUrl.trim().length() == 0) && (flag != null ? flag : true));
    }

    public NetflixAmazonCredentials(NetflixAmazonCredentials copy, AWSCredentialsProvider credentialsProvider) {
        this(copy.getName(), copy.getAccountId(), copy.getDefaultKeyPair(), copy.getRegions(), copy.getRequiredGroupMembership(), credentialsProvider, copy.getEdda(), copy.getEddaEnabled(), copy.getDiscovery(), copy.getDiscoveryEnabled(), copy.getFront50(), copy.getFront50Enabled());
    }

    NetflixAmazonCredentials(String name, String accountId, String defaultKeyPair, List<AWSRegion> regions, List<String> requiredGroupMembership, AWSCredentialsProvider credentialsProvider, String edda, Boolean eddaEnabled, String discovery, Boolean discoveryEnabled, String front50, Boolean front50Enabled) {
        super(name, accountId, defaultKeyPair, regions, requiredGroupMembership, credentialsProvider);
        this.edda = edda;
        this.eddaEnabled = flagValue(edda, eddaEnabled);
        this.discovery = discovery;
        this.discoveryEnabled = flagValue(discovery, discoveryEnabled);
        this.front50 = front50;
        this.front50Enabled = flagValue(front50, front50Enabled);
    }

    public String getEdda() {
        return edda;
    }

    public String getDiscovery() {
        return discovery;
    }

    public String getFront50() {
        return front50;
    }

    public boolean getEddaEnabled() {
        return eddaEnabled;
    }

    public boolean getDiscoveryEnabled() {
        return discoveryEnabled;
    }

    public boolean getFront50Enabled() {
        return front50Enabled;
    }
}
