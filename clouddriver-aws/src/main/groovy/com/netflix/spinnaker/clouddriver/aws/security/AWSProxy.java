package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AWS Proxy Configuration
 */
@ConfigurationProperties(prefix = "aws.proxy")
@Component
public class AWSProxy {


    private String proxyHost;
    private String proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyDomain;
    private String proxyWorkstation;
    private String protocol;

    public AWSProxy() {
        this(null, null, null, null, null, null, null);
    }

    public AWSProxy(String proxyHost, String proxyPort, String proxyUsername, String proxyPassword, String protocol) {
        this(proxyHost, proxyPort, proxyUsername, proxyPassword, null, null, protocol);
    }

    public AWSProxy(String proxyHost,
                    String proxyPort,
                    String proxyUsername,
                    String proxyPassword,
                    String proxyDomain,
                    String proxyWorkstation,
                    String protocol
    ) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyDomain = proxyDomain;
        this.proxyWorkstation = proxyWorkstation;
        this.proxyPassword = proxyPassword;
        this.protocol = protocol;


    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getProxyDomain() {
        return proxyDomain;
    }

    public void setProxyDomain(String proxyDomain) {
        this.proxyDomain = proxyDomain;
    }

    public String getProxyWorkstation() {
        return proxyWorkstation;
    }

    public void setProxyWorkstation(String proxyWorkstation) {
        this.proxyWorkstation = proxyWorkstation;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProxyProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void apply(ClientConfiguration clientConfiguration) {

        clientConfiguration.setProxyHost(proxyHost);
        clientConfiguration.setProxyPort(Integer.parseInt(proxyPort));
        clientConfiguration.setProxyUsername(proxyUsername);
        clientConfiguration.setProxyPassword(proxyPassword);

        Protocol awsProtocol = Protocol.HTTP;

        if ("HTTPS".equalsIgnoreCase(protocol)) {
            awsProtocol = Protocol.HTTPS;
        }

        clientConfiguration.setProtocol(awsProtocol);

        if (isNTLMProxy()) {
            clientConfiguration.setProxyDomain(proxyDomain);
            clientConfiguration.setProxyWorkstation(proxyWorkstation);
        }
    }

    public boolean isNTLMProxy() {

        boolean isNTLMProxy = false;

        if (getProxyHost() != null && getProxyPort() != null && getProxyDomain() != null && getProxyWorkstation() != null) {
            isNTLMProxy = true;
        }


        return isNTLMProxy;
    }

    public boolean isProxyConfigMode() {

        boolean isProxy = false;

        if (getProxyHost() != null && getProxyPort() != null) {
            isProxy = true;

            try {
                Integer.parseInt(getProxyPort());
            } catch (NumberFormatException nfe) {
                isProxy = false;
            }
        }

        return isProxy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AWSProxy awsProxy = (AWSProxy) o;

        return proxyHost.equals(awsProxy.proxyHost) &&
                proxyPort.equals(awsProxy.proxyPort) &&
                protocol.equals(awsProxy.protocol);
    }

    @Override
    public int hashCode() {
        int result = proxyHost.hashCode();
        result = 31
                * result
                + proxyPort.hashCode()
                + protocol.hashCode();

        return result;
    }

}
