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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.DescribeRegionsRequest;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.netflix.spinnaker.amos.aws.AmazonCredentials.AWSRegion;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultAWSAccountInfoLookup implements AWSAccountInfoLookup {
    private static final Pattern IAM_ARN_PATTERN = Pattern.compile(".*arn:aws:iam::([\\d+]):.*");

    private final AWSCredentialsProvider credentialsProvider;

    public DefaultAWSAccountInfoLookup(AWSCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public Long findAccountId() {
        AmazonIdentityManagement iam = new AmazonIdentityManagementClient(credentialsProvider.getCredentials());
        try {
            String arn = iam.getUser().getUser().getArn();
            Matcher matcher = IAM_ARN_PATTERN.matcher(arn);
            if (matcher.matches()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (AmazonServiceException ase) {
            if ("AccessDenied".equals(ase.getErrorCode())) {
                String message = ase.getMessage();
                Matcher matcher = IAM_ARN_PATTERN.matcher(message);
                if (matcher.matches()) {
                    return Long.parseLong(matcher.group(1));
                }
            }
            throw ase;
        }
        throw new IllegalArgumentException("Unable to lookup accountId with provided credentials");
    }

    @Override
    public List<String> listAvailabilityZones(String regionName) {
        List<AWSRegion> regions = listRegions(regionName);
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("Unknown region: " + regionName);
        }
        return new ArrayList<>(regions.get(0).getAvailabilityZones());
    }

    public List<AWSRegion> listRegions(String... regionNames) {
        return listRegions(Arrays.asList(regionNames));
    }

    @Override
    public List<AWSRegion> listRegions(Collection<String> regionNames) {
        Set<String> nameSet = new HashSet<>(regionNames);
        AmazonEC2 ec2 = new AmazonEC2Client(credentialsProvider.getCredentials());
        DescribeRegionsRequest request = new DescribeRegionsRequest();
        if (!nameSet.isEmpty()) {
            request.withRegionNames(regionNames);
        }
        List<Region> regions = ec2.describeRegions(request).getRegions();
        if (regions.size() != nameSet.size()) {
            Set<String> missingSet = new HashSet<>(nameSet);
            for (Region region: regions) {
                missingSet.remove(region.getRegionName());
            }
            throw new IllegalArgumentException("Unknown region" + (missingSet.size() > 1 ? "s: " : ": ") + missingSet);
        }
        List<AWSRegion> awsRegions = new ArrayList<>(regions.size());
        for (Region region : regions) {
            ec2.setEndpoint(region.getEndpoint());
            List<AvailabilityZone> azs = ec2.describeAvailabilityZones().getAvailabilityZones();
            List<String> availabilityZoneNames = new ArrayList<>(azs.size());
            for (AvailabilityZone az : azs) {
                availabilityZoneNames.add(az.getZoneName());
            }

            awsRegions.add(new AWSRegion(region.getRegionName(), availabilityZoneNames));
        }
        return awsRegions;
    }
}
