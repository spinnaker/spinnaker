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
import java.util.Objects;

import static java.util.Objects.requireNonNull;

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
    private final List<String> defaultSecurityGroups;
    private final AWSCredentialsProvider credentialsProvider;

    public static AmazonCredentials fromAWSCredentials(String name, String environment, String accountType, AWSCredentialsProvider credentialsProvider, AmazonClientProvider amazonClientProvider) {
        return fromAWSCredentials(name, environment, accountType, null, credentialsProvider, amazonClientProvider);
    }

    public static AmazonCredentials fromAWSCredentials(String name, String environment, String accountType, String defaultKeyPair, AWSCredentialsProvider credentialsProvider, AmazonClientProvider amazonClientProvider) {
        AWSAccountInfoLookup lookup = new DefaultAWSAccountInfoLookup(credentialsProvider, amazonClientProvider);
        final String accountId = lookup.findAccountId();
        final List<AWSRegion> regions = lookup.listRegions();
        return new AmazonCredentials(name, environment, accountType, accountId, defaultKeyPair, regions, null, null, credentialsProvider);
    }

    public AmazonCredentials(@JsonProperty("name") String name,
                             @JsonProperty("environment") String environment,
                             @JsonProperty("accountType") String accountType,
                             @JsonProperty("accountId") String accountId,
                             @JsonProperty("defaultKeyPair") String defaultKeyPair,
                             @JsonProperty("regions") List<AWSRegion> regions,
                             @JsonProperty("defaultSecurityGroups") List<String> defaultSecurityGroups,
                             @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership) {
        this(name, environment, accountType, accountId, defaultKeyPair, regions, defaultSecurityGroups, requiredGroupMembership, null);
    }

    public AmazonCredentials(AmazonCredentials source, AWSCredentialsProvider credentialsProvider) {
        this(
            source.getName(),
            source.getEnvironment(),
            source.getAccountType(),
            source.getAccountId(),
            source.getDefaultKeyPair(),
            source.getRegions(),
            source.getDefaultSecurityGroups(),
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
                      List<String> defaultSecurityGroups,
                      List<String> requiredGroupMembership,
                      AWSCredentialsProvider credentialsProvider) {
        this.name = requireNonNull(name, "name");
        this.environment = requireNonNull(environment, "environment");
        this.accountType = requireNonNull(accountType, "accountType");
        this.accountId = requireNonNull(accountId, "accountId");
        this.defaultKeyPair = defaultKeyPair;
        this.regions = regions == null ? Collections.<AWSRegion>emptyList() : Collections.unmodifiableList(regions);
        this.defaultSecurityGroups = defaultSecurityGroups == null ? null : Collections.unmodifiableList(defaultSecurityGroups);
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

    @Override
    public String getAccountId() {
        return accountId;
    }

    public String getDefaultKeyPair() {
        return defaultKeyPair;
    }

    public List<AWSRegion> getRegions() {
        return regions;
    }

    public List<String> getDefaultSecurityGroups() {
      return defaultSecurityGroups;
    }

    public static class AWSRegion {

        private final String name;
        private final Boolean deprecated;
        private final List<String> availabilityZones;
        private final List<String> preferredZones;

        public AWSRegion(@JsonProperty("name") String name,
                         @JsonProperty("availabilityZones") List<String> availabilityZones,
                         @JsonProperty("preferredZones") List<String> preferredZones,
                         @JsonProperty("deprecated") Boolean deprecated) {
            this.name = Objects.requireNonNull(name, "name");
            this.availabilityZones = availabilityZones == null ? Collections.<String>emptyList() : Collections.unmodifiableList(availabilityZones);
            List<String> preferred = (preferredZones == null || preferredZones.isEmpty()) ? new ArrayList<>(this.availabilityZones) : new ArrayList<>(preferredZones);
            preferred.retainAll(this.availabilityZones);
            this.preferredZones = Collections.unmodifiableList(preferred);

            if (deprecated == null) {
                deprecated = Boolean.FALSE;
            }
            this.deprecated = deprecated;
        }

        public AWSRegion(String name, List<String> availabilityZones) {
            this(name, availabilityZones, Collections.emptyList(), null);
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

        public Boolean getDeprecated() {
            return deprecated;
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
    public String getCloudProvider() {
       return CLOUD_PROVIDER;
    }

    @Override
    public List<String> getRequiredGroupMembership() {
        return requiredGroupMembership;
    }
}
