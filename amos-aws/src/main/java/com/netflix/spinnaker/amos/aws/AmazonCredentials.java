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
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.netflix.spinnaker.amos.AccountCredentials;

import java.util.Collection;
import java.util.List;

/**
 * Basic set of Amazon credentials that will a provided {@link com.amazonaws.auth.AWSCredentialsProvider} to resolve account credentials.
 * If none provided, the {@link com.amazonaws.auth.DefaultAWSCredentialsProviderChain} will be used. The account's active
 * regions and availability zones can be specified as well.
 *
 * @author Dan Woods
 */
public class AmazonCredentials implements AccountCredentials<AWSCredentials> {
    private String name;
    private Long accountId;
    protected AWSCredentialsProvider credentialsProvider;
    private List<AWSRegion> regions;


    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public List<AWSRegion> getRegions() {
        return regions;
    }

    public void setRegions(List<AWSRegion> regions) {
        this.regions = regions;
    }

    public static class AWSRegion {
        public String name;
        public Collection<String> availabilityZones;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Collection<String> getAvailabilityZones() {
            return availabilityZones;
        }

        public void setAvailabilityZones(Collection<String> availabilityZones) {
            this.availabilityZones = availabilityZones;
        }

        public boolean equals(Object other) {
            if (!(other instanceof AWSRegion)) {
                return false;
            }

            AWSRegion o = (AWSRegion)other;
            return this.name.equals(o.getName()) &&
                    this.availabilityZones.containsAll(o.getAvailabilityZones());
        }
    }

    public AWSCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public void setCredentialsProvider(AWSCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public AWSCredentials getCredentials() {
        if (credentialsProvider == null) {
            this.credentialsProvider = new DefaultAWSCredentialsProviderChain();
        }
        return credentialsProvider.getCredentials();
    }

}
