/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.amos.AccountCredentials;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Basic set of Amazon credentials that will a provided {@link com.amazonaws.auth.AWSCredentialsProvider} to resolve account credentials.
 * If none provided, the {@link com.amazonaws.auth.DefaultAWSCredentialsProviderChain} will be used. The account's active
 * regions and availability zones can be specified as well.
 *
 * @author Dan Woods
 */
public class AmazonCredentials implements AccountCredentials<AWSCredentials> {
    private static final String PROVIDER = "aws";

    private final String name;
    private final String accountId;
    private final String defaultKeyPair;
    private final List<String> requiredGroupMembership;
    private final List<AWSRegion> regions;
    private final AWSCredentialsProvider credentialsProvider;

    public static AmazonCredentials fromAWSCredentials(String name, AWSCredentialsProvider credentialsProvider) {
        return fromAWSCredentials(name, null, credentialsProvider);
    }

    public static AmazonCredentials fromAWSCredentials(String name, String defaultKeyPair, AWSCredentialsProvider credentialsProvider) {
        AWSAccountInfoLookup lookup = new DefaultAWSAccountInfoLookup(credentialsProvider);
        final String accountId = lookup.findAccountId();
        final List<AWSRegion> regions = lookup.listRegions();
        return new AmazonCredentials(name, accountId, defaultKeyPair, regions, null, credentialsProvider);
    }


    public AmazonCredentials(@JsonProperty("name") String name,
                             @JsonProperty("accountId") String accountId,
                             @JsonProperty("defaultKeyPair") String defaultKeyPair,
                             @JsonProperty("regions") List<AWSRegion> regions,
                             @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership) {
        this(name, accountId, defaultKeyPair, regions, requiredGroupMembership, null);
    }


    public AmazonCredentials(AmazonCredentials source, AWSCredentialsProvider credentialsProvider) {
        this(
            source.getName(),
            source.getAccountId(),
            source.getDefaultKeyPair(),
            source.getRegions(),
            source.getRequiredGroupMembership(),
            credentialsProvider
        );
    }

    AmazonCredentials(String name,
                      String accountId,
                      String defaultKeyPair,
                      List<AWSRegion> regions,
                      List<String> requiredGroupMembership,
                      AWSCredentialsProvider credentialsProvider) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        if (accountId == null) {
            throw new NullPointerException("accountId");
        }

        this.name = name;
        this.accountId = accountId;
        this.defaultKeyPair = defaultKeyPair;
        this.regions = regions == null ? Collections.<AWSRegion>emptyList() : Collections.unmodifiableList(regions);
        this.requiredGroupMembership = requiredGroupMembership == null ? Collections.<String>emptyList() : Collections.unmodifiableList(requiredGroupMembership);
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public String getName() {
        return name;
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

        public AWSRegion(@JsonProperty("name") String name,
                         @JsonProperty("availabilityZones") List<String> availabilityZones) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.name = name;
            this.availabilityZones = availabilityZones == null ? Collections.<String>emptyList() : Collections.unmodifiableList(availabilityZones);
        }

        public String getName() {
            return name;
        }

        public Collection<String> getAvailabilityZones() {
            return availabilityZones;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AWSRegion awsRegion = (AWSRegion) o;

            return name.equals(awsRegion.name) && availabilityZones.equals(awsRegion.availabilityZones);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + availabilityZones.hashCode();
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
    public String getProvider() { return PROVIDER; }

    @Override
    public List<String> getRequiredGroupMembership() {
        return requiredGroupMembership;
    }
}
