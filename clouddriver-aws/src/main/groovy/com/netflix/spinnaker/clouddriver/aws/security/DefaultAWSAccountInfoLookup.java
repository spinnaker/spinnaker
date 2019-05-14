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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials.AWSRegion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultAWSAccountInfoLookup implements AWSAccountInfoLookup {
  private static final String DEFAULT_SECURITY_GROUP_NAME = "default";
  private static final Pattern IAM_ARN_PATTERN =
      Pattern.compile(".*?arn:aws(?:-cn|-us-gov)?:(?:iam|sts)::(\\d+):.*");

  private final AWSCredentialsProvider credentialsProvider;
  private final AmazonClientProvider amazonClientProvider;

  public DefaultAWSAccountInfoLookup(
      AWSCredentialsProvider credentialsProvider, AmazonClientProvider amazonClientProvider) {
    this.credentialsProvider = credentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
  }

  @Override
  public String findAccountId() {
    AmazonEC2 ec2 =
        amazonClientProvider.getAmazonEC2(credentialsProvider, AmazonClientProvider.DEFAULT_REGION);
    try {
      List<Vpc> vpcs = ec2.describeVpcs().getVpcs();
      boolean supportsByName = false;
      if (vpcs.isEmpty()) {
        supportsByName = true;
      } else {
        for (Vpc vpc : vpcs) {
          if (vpc.getIsDefault()) {
            supportsByName = true;
            break;
          }
        }
      }

      DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest();
      if (supportsByName) {
        request.withGroupNames(DEFAULT_SECURITY_GROUP_NAME);
      }
      DescribeSecurityGroupsResult result = ec2.describeSecurityGroups(request);

      for (SecurityGroup sg : result.getSecurityGroups()) {
        // if there is a vpcId or it is the default security group it won't be an EC2 cross account
        // group
        if ((sg.getVpcId() != null && sg.getVpcId().length() > 0)
            || DEFAULT_SECURITY_GROUP_NAME.equals(sg.getGroupName())) {
          return sg.getOwnerId();
        }
      }

      throw new IllegalArgumentException("Unable to lookup accountId with provided credentials");
    } catch (AmazonServiceException ase) {
      if ("AccessDenied".equals(ase.getErrorCode())) {
        String message = ase.getMessage();
        Matcher matcher = IAM_ARN_PATTERN.matcher(message);
        if (matcher.matches()) {
          return matcher.group(1);
        }
      }
      throw ase;
    }
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
    AmazonEC2 ec2 =
        amazonClientProvider.getAmazonEC2(credentialsProvider, AmazonClientProvider.DEFAULT_REGION);

    DescribeRegionsRequest request = new DescribeRegionsRequest();
    if (!nameSet.isEmpty()) {
      request.withRegionNames(regionNames);
    }
    List<Region> regions = ec2.describeRegions(request).getRegions();
    if (regions.size() != nameSet.size()) {
      Set<String> missingSet = new HashSet<>(nameSet);
      for (Region region : regions) {
        missingSet.remove(region.getRegionName());
      }
      throw new IllegalArgumentException(
          "Unknown region" + (missingSet.size() > 1 ? "s: " : ": ") + missingSet);
    }
    List<AWSRegion> awsRegions = new ArrayList<>(regions.size());
    for (Region region : regions) {
      AmazonEC2 regionalEc2 =
          amazonClientProvider.getAmazonEC2(credentialsProvider, region.getRegionName());
      List<AvailabilityZone> azs = regionalEc2.describeAvailabilityZones().getAvailabilityZones();
      List<String> availabilityZoneNames = new ArrayList<>(azs.size());
      for (AvailabilityZone az : azs) {
        availabilityZoneNames.add(az.getZoneName());
      }

      awsRegions.add(new AWSRegion(region.getRegionName(), availabilityZoneNames));
    }
    return awsRegions;
  }
}
