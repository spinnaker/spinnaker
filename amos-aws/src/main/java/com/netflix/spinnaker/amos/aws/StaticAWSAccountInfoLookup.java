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

import java.util.*;

public class StaticAWSAccountInfoLookup implements AWSAccountInfoLookup {
    private final long accountId;
    private final List<AmazonCredentials.AWSRegion> knownRegions;

    public StaticAWSAccountInfoLookup(long accountId, List<AmazonCredentials.AWSRegion> knownRegions) {
        this.accountId = accountId;
        this.knownRegions = knownRegions;
    }

    @Override
    public Long findAccountId() {
        return accountId;
    }

    @Override
    public List<AmazonCredentials.AWSRegion> listRegions(String... regionNames) {
        return listRegions(Arrays.asList(regionNames));
    }

    @Override
    public List<AmazonCredentials.AWSRegion> listRegions(Collection<String> regionNames) {
        Set<String> nameSet = new HashSet<>(regionNames);
        List<AmazonCredentials.AWSRegion> result = new ArrayList<>(nameSet.size());
        for (AmazonCredentials.AWSRegion region : knownRegions) {
            if (nameSet.isEmpty() || nameSet.contains(region.getName())) {
                result.add(region);
            }
        }
        return result;
    }

    @Override
    public List<String> listAvailabilityZones(String regionName) {
        for (AmazonCredentials.AWSRegion region : knownRegions) {
            if (region.getName().equals(regionName)) {
                return new ArrayList<>(region.getAvailabilityZones());
            }
        }
        return null;
    }
}
