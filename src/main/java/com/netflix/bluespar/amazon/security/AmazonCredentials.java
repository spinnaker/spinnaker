package com.netflix.bluespar.amazon.security;

import com.amazonaws.auth.AWSCredentials;

/**
 * Provides a correlation between an AWSCredentials object and a named environment.
 */
public class AmazonCredentials {
    private final AWSCredentials credentials;
    private final String environment;

    public AmazonCredentials(AWSCredentials credentials, String environment) {
        this.credentials = credentials;
        this.environment = environment;
    }

    public AWSCredentials getCredentials() {
        return credentials;
    }

    public String getEnvironment() {
        return environment;
    }
}
