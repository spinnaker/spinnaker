/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.aws.services;

import static java.util.Comparator.comparing;

import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMappingRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDeviceRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecificationRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptionsRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMetadataOptionsRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecificationRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateSpotMarketOptionsRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.LaunchTemplatesMonitoringRequest;
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData;
import com.netflix.spinnaker.clouddriver.aws.deploy.LaunchConfigurationBuilder.LaunchConfigurationSettings;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider.UserDataRequest;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LaunchTemplateService {
  private final AmazonEC2 ec2;
  private final List<UserDataProvider> userDataProviders;
  private final RetrySupport retrySupport = new RetrySupport();

  public LaunchTemplateService(AmazonEC2 ec2, List<UserDataProvider> userDataProviders) {
    this.ec2 = ec2;
    this.userDataProviders = userDataProviders;
  }

  public Optional<LaunchTemplateVersion> getLaunchTemplateVersion(
      LaunchTemplateSpecification launchTemplateSpecification) {
    final List<LaunchTemplateVersion> versions = new ArrayList<>();
    final String version = launchTemplateSpecification.getVersion();
    final DescribeLaunchTemplateVersionsRequest request =
        new DescribeLaunchTemplateVersionsRequest()
            .withLaunchTemplateId(launchTemplateSpecification.getLaunchTemplateId());

    while (true) {
      final DescribeLaunchTemplateVersionsResult result =
          ec2.describeLaunchTemplateVersions(request);
      versions.addAll(result.getLaunchTemplateVersions());
      if (result.getNextToken() != null) {
        request.withNextToken(result.getNextToken());
      } else {
        break;
      }
    }

    if ("$Latest".equals(version)) {
      return versions.stream().max(comparing(LaunchTemplateVersion::getVersionNumber));
    } else if ("$Default".equals(version)) {
      return versions.stream().filter(LaunchTemplateVersion::isDefaultVersion).findFirst();
    }

    return versions.stream()
        .filter(i -> i.getVersionNumber().equals(Long.parseLong(version)))
        .findFirst();
  }

  public LaunchTemplate createLaunchTemplate(
      LaunchConfigurationSettings settings,
      String launchTemplateName,
      Boolean requireIMDSv2,
      Boolean associateIPv6Address) {
    final RequestLaunchTemplateData request =
        new RequestLaunchTemplateData()
            .withImageId(settings.getAmi())
            .withKernelId(settings.getKernelId())
            .withInstanceType(settings.getInstanceType())
            .withRamDiskId(settings.getRamdiskId())
            .withEbsOptimized(settings.getEbsOptimized())
            .withKeyName(settings.getKeyPair())
            .withIamInstanceProfile(
                new LaunchTemplateIamInstanceProfileSpecificationRequest()
                    .withName(settings.getIamRole()))
            .withMonitoring(
                new LaunchTemplatesMonitoringRequest()
                    .withEnabled(settings.getInstanceMonitoring()));

    UserDataRequest userDataRequest =
        UserDataRequest.builder()
            .launchTemplate(true)
            .asgName(settings.getBaseName())
            .launchSettingName(launchTemplateName)
            .region(settings.getRegion())
            .account(settings.getAccount())
            .environment(settings.getEnvironment())
            .accountType(settings.getAccountType())
            .build();

    request.withUserData(
        userDataRequest.getUserData(userDataProviders, settings.getBase64UserData()));

    // block device mappings
    final List<LaunchTemplateBlockDeviceMappingRequest> mappings = new ArrayList<>();
    for (AmazonBlockDevice blockDevice : settings.getBlockDevices()) {
      LaunchTemplateBlockDeviceMappingRequest mapping =
          new LaunchTemplateBlockDeviceMappingRequest().withDeviceName(blockDevice.getDeviceName());
      if (blockDevice.getVirtualName() != null) {
        mapping.setVirtualName(blockDevice.getVirtualName());
      } else {
        mapping.setEbs(getLaunchTemplateEbsBlockDeviceRequest(blockDevice));
      }

      mappings.add(mapping);
    }

    request.setBlockDeviceMappings(mappings);

    // metadata options
    if (requireIMDSv2 != null && requireIMDSv2) {
      request.setMetadataOptions(
          new LaunchTemplateInstanceMetadataOptionsRequest().withHttpTokens("required"));
    }

    // instance market options
    request.withInstanceMarketOptions(
        new LaunchTemplateInstanceMarketOptionsRequest()
            .withSpotOptions(
                new LaunchTemplateSpotMarketOptionsRequest()
                    .withMaxPrice(settings.getSpotPrice())));

    // network interfaces
    request.withNetworkInterfaces(
        new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest()
            .withAssociatePublicIpAddress(settings.getAssociatePublicIpAddress())
            .withIpv6AddressCount(associateIPv6Address ? 1 : 0)
            .withGroups(settings.getSecurityGroups())
            .withDeviceIndex(0));

    return retrySupport.retry(
        () -> {
          final CreateLaunchTemplateRequest launchTemplateRequest =
              new CreateLaunchTemplateRequest()
                  .withLaunchTemplateName(launchTemplateName)
                  .withLaunchTemplateData(request);
          return ec2.createLaunchTemplate(launchTemplateRequest).getLaunchTemplate();
        },
        3,
        Duration.ofMillis(3000),
        false);
  }

  private LaunchTemplateEbsBlockDeviceRequest getLaunchTemplateEbsBlockDeviceRequest(
      AmazonBlockDevice blockDevice) {
    final LaunchTemplateEbsBlockDeviceRequest blockDeviceRequest =
        new LaunchTemplateEbsBlockDeviceRequest().withVolumeSize(blockDevice.getSize());

    if (blockDevice.getDeleteOnTermination() != null) {
      blockDeviceRequest.setDeleteOnTermination(blockDevice.getDeleteOnTermination());
    }

    if (blockDevice.getVolumeType() != null) {
      blockDeviceRequest.setVolumeType(blockDevice.getVolumeType());
    }

    if (blockDevice.getIops() != null) {
      blockDeviceRequest.setIops(blockDevice.getIops());
    }

    if (blockDevice.getSnapshotId() != null) {
      blockDeviceRequest.setSnapshotId(blockDevice.getSnapshotId());
    }

    if (blockDevice.getEncrypted() != null) {
      blockDeviceRequest.setEncrypted(blockDevice.getEncrypted());
    }
    return blockDeviceRequest;
  }
}
