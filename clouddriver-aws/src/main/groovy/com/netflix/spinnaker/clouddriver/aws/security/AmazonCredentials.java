/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Basic set of Amazon credentials that will a provided {@link com.amazonaws.auth.AWSCredentialsProvider} to resolve account credentials.
 * If none provided, the {@link com.amazonaws.auth.DefaultAWSCredentialsProviderChain} will be used. The account's active
 * regions and availability zones can be specified as well.
 *
 *
 */
public class AmazonCredentials implements AccountCredentials<AWSCredentials> {
    private static final String CLOUD_PROVIDER = "aws";

    private final String name;
    private final String environment;
    private final String accountType;
    private final String accountId;
    private final String defaultKeyPair;
    private final List<String> requiredGroupMembership;
    private final List<AWSRegion> regions;
    private final AWSCredentialsProvider credentialsProvider;

    public static AmazonCredentials fromAWSCredentials(String name, String environment, String accountType, AWSCredentialsProvider credentialsProvider) {
        return fromAWSCredentials(name, environment, accountType, null, credentialsProvider);
    }

    public static AmazonCredentials fromAWSCredentials(String name, String environment, String accountType, String defaultKeyPair, AWSCredentialsProvider credentialsProvider) {
        AWSAccountInfoLookup lookup = new DefaultAWSAccountInfoLookup(credentialsProvider);
        final String accountId = lookup.findAccountId();
        final List<AWSRegion> regions = lookup.listRegions();
        return new AmazonCredentials(name, environment, accountType, accountId, defaultKeyPair, regions, null, credentialsProvider);
    }

    public AmazonCredentials(@JsonProperty("name") String name,
                             @JsonProperty("environment") String environment,
                             @JsonProperty("accountType") String accountType,
                             @JsonProperty("accountId") String accountId,
                             @JsonProperty("defaultKeyPair") String defaultKeyPair,
                             @JsonProperty("regions") List<AWSRegion> regions,
                             @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership) {
        this(name, environment, accountType, accountId, defaultKeyPair, regions, requiredGroupMembership, null);
    }

    public AmazonCredentials(AmazonCredentials source, AWSCredentialsProvider credentialsProvider) {
        this(
            source.getName(),
            source.getEnvironment(),
            source.getAccountType(),
            source.getAccountId(),
            source.getDefaultKeyPair(),
            source.getRegions(),
            source.getRequiredGroupMembership(),
            credentialsProvider
        );
    }

    AmazonCredentials(String name,
                      String environment,
                      String accountType,
                      String accountId,
                      String defaultKeyPair,
                      List<AWSRegion> regions,
                      List<String> requiredGroupMembership,
                      AWSCredentialsProvider credentialsProvider) {
        this.name = notNull(name, "name");
        this.environment = notNull(environment, "environment");
        this.accountType = notNull(accountType, "accountType");
        this.accountId = notNull(accountId, "accountId");
        this.defaultKeyPair = defaultKeyPair;
        this.regions = regions == null ? Collections.<AWSRegion>emptyList() : Collections.unmodifiableList(regions);
        this.requiredGroupMembership = requiredGroupMembership == null ? Collections.<String>emptyList() : Collections.unmodifiableList(requiredGroupMembership);
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEnvironment() {
        return environment;
    }

    @Override
    public String getAccountType() {
        return accountType;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDefaultKeyPair() {
        return defaultKeyPair;
    }

    public List<AWSRegion> getRegions() {
        return regions;
    }

    public static class AWSRegion {

        private final String name;
        private final List<String> availabilityZones;
        private final List<String> preferredZones;

        public AWSRegion(@JsonProperty("name") String name,
                         @JsonProperty("availabilityZones") List<String> availabilityZones,
                         @JsonProperty("preferredZones") List<String> preferredZones) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.name = name;
            this.availabilityZones = availabilityZones == null ? Collections.<String>emptyList() : Collections.unmodifiableList(availabilityZones);
            List<String> preferred = (preferredZones == null || preferredZones.isEmpty()) ? new ArrayList<>(this.availabilityZones) : new ArrayList<>(preferredZones);
            preferred.retainAll(this.availabilityZones);
            this.preferredZones = Collections.unmodifiableList(preferred);
        }

        public AWSRegion(String name, List<String> availabilityZones) {
            this(name, availabilityZones, Collections.emptyList());
        }

        public String getName() {
            return name;
        }

        public Collection<String> getAvailabilityZones() {
            return availabilityZones;
        }

        public Collection<String> getPreferredZones() {
            return preferredZones;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AWSRegion awsRegion = (AWSRegion) o;

            return name.equals(awsRegion.name) &&
              availabilityZones.equals(awsRegion.availabilityZones) &&
              preferredZones.equals(awsRegion.preferredZones);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31
              * result
              + availabilityZones.hashCode()
              + preferredZones.hashCode();
            return result;
        }
    }

    @JsonIgnore
    public AWSCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    @Override
    @JsonIgnore
    public AWSCredentials getCredentials() {
        return credentialsProvider.getCredentials();
    }

    @Override
    public String getProvider() { return getCloudProvider(); }

    @Override
    public String getCloudProvider() {
       return CLOUD_PROVIDER;
    }

    @Override
    public List<String> getRequiredGroupMembership() {
        return requiredGroupMembership;
    }

    protected static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        return value;
    }
}
