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
import com.amazonaws.services.ec2.model.*;
import com.netflix.spinnaker.clouddriver.aws.deploy.AmazonResourceTagger;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProviderAggregator;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class LaunchTemplateService {
  private final AmazonEC2 ec2;
  private final UserDataProviderAggregator userDataProviderAggregator;
  private final LocalFileUserDataProperties localFileUserDataProperties;
  private final Collection<AmazonResourceTagger> amazonResourceTaggers;
  private final RetrySupport retrySupport = new RetrySupport();

  /**
   * Traditional Amazon EC2 instance types provide fixed CPU utilization, while burstable
   * performance instances provide a baseline level of CPU utilization with the ability to burst CPU
   * utilization above the baseline level. The baseline utilization and ability to burst are
   * governed by CPU credits.
   *
   * <p>CPU credits can be configured with 2 modes: (1) unlimited: Can sustain high CPU utilization
   * for any period of time whenever required. If the average CPU usage over a rolling 24-hour
   * period exceeds the baseline, charges for surplus credits will apply. (2) standard: Suited to
   * workloads with an average CPU utilization that is consistently below the baseline CPU
   * utilization of the instance. To burst above the baseline, the instance spends credits that it
   * has accrued in its CPU credit balance.
   *
   * <p>https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances.html
   */
  private static final String UNLIMITED_CPU_CREDITS = "unlimited";

  private static final String STANDARD_CPU_CREDITS = "standard";

  public LaunchTemplateService(
      AmazonEC2 ec2,
      UserDataProviderAggregator userDataProviderAggregator,
      LocalFileUserDataProperties localFileUserDataProperties,
      Collection<AmazonResourceTagger> amazonResourceTaggers) {
    this.ec2 = ec2;
    this.userDataProviderAggregator = userDataProviderAggregator;
    this.localFileUserDataProperties = localFileUserDataProperties;
    this.amazonResourceTaggers = amazonResourceTaggers;
  }

  public LaunchTemplateVersion modifyLaunchTemplate(
      NetflixAmazonCredentials credentials,
      ModifyServerGroupLaunchTemplateDescription description,
      LaunchTemplateVersion sourceVersion) {
    RequestLaunchTemplateData data =
        buildLaunchTemplateData(credentials, description, sourceVersion);
    CreateLaunchTemplateVersionResult result =
        ec2.createLaunchTemplateVersion(
            new CreateLaunchTemplateVersionRequest()
                .withSourceVersion(String.valueOf(sourceVersion.getVersionNumber()))
                .withLaunchTemplateId(sourceVersion.getLaunchTemplateId())
                .withLaunchTemplateData(data));
    return result.getLaunchTemplateVersion();
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
      AsgConfiguration asgConfig, String asgName, String launchTemplateName) {
    final RequestLaunchTemplateData data =
        buildLaunchTemplateData(asgConfig, asgName, launchTemplateName);
    log.debug("Creating launch template with name {}", launchTemplateName);
    return retrySupport.retry(
        () -> {
          final CreateLaunchTemplateRequest launchTemplateRequest =
              new CreateLaunchTemplateRequest()
                  .withLaunchTemplateName(launchTemplateName)
                  .withLaunchTemplateData(data);
          return ec2.createLaunchTemplate(launchTemplateRequest).getLaunchTemplate();
        },
        3,
        Duration.ofMillis(3000),
        false);
  }

  /**
   * Build launch template data for launch template modification i.e. new launch template version
   */
  private RequestLaunchTemplateData buildLaunchTemplateData(
      NetflixAmazonCredentials credentials,
      ModifyServerGroupLaunchTemplateDescription description,
      LaunchTemplateVersion launchTemplateVersion) {
    RequestLaunchTemplateData request =
        new RequestLaunchTemplateData()
            .withImageId(description.getImageId())
            .withKernelId(description.getKernelId())
            .withInstanceType(description.getInstanceType())
            .withRamDiskId(description.getRamdiskId())
            .withIamInstanceProfile(
                new LaunchTemplateIamInstanceProfileSpecificationRequest()
                    .withName(description.getIamRole()));

    Optional<LaunchTemplateTagSpecificationRequest> tagSpecification =
        tagSpecification(amazonResourceTaggers, description.getAsgName());
    if (tagSpecification.isPresent()) {
      request = request.withTagSpecifications(tagSpecification.get());
    }

    if (description.getEbsOptimized() != null) {
      request.setEbsOptimized(description.getEbsOptimized());
    }

    if (description.getInstanceMonitoring() != null) {
      request.setMonitoring(
          new LaunchTemplatesMonitoringRequest().withEnabled(description.getInstanceMonitoring()));
    }

    /*
     Copy over the original user data only if the UserDataProviders behavior is disabled.
     This is to avoid having duplicate user data.
    */
    String base64UserData =
        (localFileUserDataProperties != null && !localFileUserDataProperties.isEnabled())
            ? launchTemplateVersion.getLaunchTemplateData().getUserData()
            : null;
    setUserData(
        request,
        description.getAsgName(),
        launchTemplateVersion.getLaunchTemplateName(),
        description.getRegion(),
        description.getAccount(),
        credentials.getEnvironment(),
        credentials.getAccountType(),
        description.getIamRole(),
        description.getImageId(),
        base64UserData,
        description.getUserDataOverride());

    // block device mappings
    if (description.getBlockDevices() != null) {
      request.setBlockDeviceMappings(buildDeviceMapping(description.getBlockDevices()));
    }

    // metadata options
    if (description.getRequireIMDV2() != null) {
      request.setMetadataOptions(
          new LaunchTemplateInstanceMetadataOptionsRequest()
              .withHttpTokens(description.getRequireIMDV2() ? "required" : ""));
    }

    // instance market options
    setSpotInstanceMarketOptions(request, description.getSpotPrice());

    setCreditSpecification(request, description.getUnlimitedCpuCredits());

    // network interfaces
    LaunchTemplateInstanceNetworkInterfaceSpecificationRequest networkInterfaceRequest =
        new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest();

    LaunchTemplateInstanceNetworkInterfaceSpecification defaultInterface;
    List<LaunchTemplateInstanceNetworkInterfaceSpecification> networkInterfaces =
        launchTemplateVersion.getLaunchTemplateData().getNetworkInterfaces();
    if (networkInterfaces != null && !networkInterfaces.isEmpty()) {
      defaultInterface =
          networkInterfaces.stream()
              .filter(i -> i.getDeviceIndex() == 0)
              .findFirst()
              .orElseGet(LaunchTemplateInstanceNetworkInterfaceSpecification::new);
    } else {
      defaultInterface = new LaunchTemplateInstanceNetworkInterfaceSpecification();
    }

    if (description.getAssociateIPv6Address() != null) {
      networkInterfaceRequest.setIpv6AddressCount(
          description.getAssociateIPv6Address() || defaultInterface.getIpv6AddressCount() > 0
              ? 1
              : 0);
    }

    if (description.getAssociatePublicIpAddress() != null) {
      networkInterfaceRequest.setAssociatePublicIpAddress(
          description.getAssociatePublicIpAddress());
    }

    networkInterfaceRequest.setDeviceIndex(0);
    networkInterfaceRequest.setGroups(description.getSecurityGroups());

    return request.withNetworkInterfaces(networkInterfaceRequest);
  }

  /** Build launch template data for new launch template creation */
  private RequestLaunchTemplateData buildLaunchTemplateData(
      AsgConfiguration asgConfig, String asgName, String launchTemplateName) {
    RequestLaunchTemplateData request =
        new RequestLaunchTemplateData()
            .withImageId(asgConfig.getAmi())
            .withKernelId(asgConfig.getKernelId())
            .withInstanceType(asgConfig.getInstanceType())
            .withRamDiskId(asgConfig.getRamdiskId())
            .withEbsOptimized(asgConfig.getEbsOptimized())
            .withKeyName(asgConfig.getKeyPair())
            .withIamInstanceProfile(
                new LaunchTemplateIamInstanceProfileSpecificationRequest()
                    .withName(asgConfig.getIamRole()))
            .withMonitoring(
                new LaunchTemplatesMonitoringRequest()
                    .withEnabled(asgConfig.getInstanceMonitoring()));

    Optional<LaunchTemplateTagSpecificationRequest> tagSpecification =
        tagSpecification(amazonResourceTaggers, asgName);
    if (tagSpecification.isPresent()) {
      request = request.withTagSpecifications(tagSpecification.get());
    }

    if (asgConfig.getPlacement() != null) {
      request =
          request.withPlacement(
              new LaunchTemplatePlacementRequest()
                  .withAffinity(asgConfig.getPlacement().getAffinity())
                  .withAvailabilityZone(asgConfig.getPlacement().getAvailabilityZone())
                  .withGroupName(asgConfig.getPlacement().getGroupName())
                  .withHostId(asgConfig.getPlacement().getHostId())
                  .withTenancy(asgConfig.getPlacement().getTenancy())
                  .withHostResourceGroupArn(asgConfig.getPlacement().getHostResourceGroupArn())
                  .withPartitionNumber(asgConfig.getPlacement().getPartitionNumber())
                  .withSpreadDomain(asgConfig.getPlacement().getSpreadDomain()));
    }

    if (asgConfig.getLicenseSpecifications() != null) {
      request =
          request.withLicenseSpecifications(
              asgConfig.getLicenseSpecifications().stream()
                  .map(
                      licenseSpecification ->
                          new LaunchTemplateLicenseConfigurationRequest()
                              .withLicenseConfigurationArn(licenseSpecification.getArn()))
                  .collect(Collectors.toList()));
    }

    setUserData(
        request,
        asgName,
        launchTemplateName,
        asgConfig.getRegion(),
        asgConfig.getCredentials().getName(),
        asgConfig.getCredentials().getEnvironment(),
        asgConfig.getCredentials().getAccountType(),
        asgConfig.getIamRole(),
        asgConfig.getAmi(),
        asgConfig.getBase64UserData(),
        asgConfig.getUserDataOverride());

    // block device mappings
    request.setBlockDeviceMappings(buildDeviceMapping(asgConfig.getBlockDevices()));

    // metadata options
    if (asgConfig.getRequireIMDSv2() != null && asgConfig.getRequireIMDSv2()) {
      request.setMetadataOptions(
          new LaunchTemplateInstanceMetadataOptionsRequest().withHttpTokens("required"));
    }

    // instance market options
    setSpotInstanceMarketOptions(request, asgConfig.getSpotMaxPrice());

    setCreditSpecification(request, asgConfig.getUnlimitedCpuCredits());

    // network interfaces
    request.withNetworkInterfaces(
        new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest()
            .withAssociatePublicIpAddress(asgConfig.getAssociatePublicIpAddress())
            .withIpv6AddressCount(asgConfig.getAssociateIPv6Address() ? 1 : 0)
            .withGroups(asgConfig.getSecurityGroups())
            .withDeviceIndex(0));

    return request;
  }

  /** Set credit option for burstable performance instances to 'unlimited' only if explicitly set */
  private void setCreditSpecification(
      RequestLaunchTemplateData request, Boolean unlimitedCpuCredits) {
    if (unlimitedCpuCredits != null) {
      request.setCreditSpecification(
          new CreditSpecificationRequest()
              .withCpuCredits(unlimitedCpuCredits ? UNLIMITED_CPU_CREDITS : STANDARD_CPU_CREDITS));
    }
  }

  /**
   * Set instance market options, required when launching spot instances
   * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-launchtemplate-launchtemplatedata-instancemarketoptions.html
   */
  private void setSpotInstanceMarketOptions(
      RequestLaunchTemplateData request, String maxSpotPrice) {
    if (maxSpotPrice != null && StringUtils.isNotEmpty(maxSpotPrice.trim())) {
      request.setInstanceMarketOptions(
          new LaunchTemplateInstanceMarketOptionsRequest()
              .withMarketType("spot")
              .withSpotOptions(
                  new LaunchTemplateSpotMarketOptionsRequest().withMaxPrice(maxSpotPrice)));
    }
  }

  private void setUserData(
      RequestLaunchTemplateData request,
      String asgName,
      String launchTemplateName,
      String region,
      String account,
      String env,
      String accType,
      String iamRole,
      String imageId,
      String base64UserData,
      UserDataOverride userDataOverride) {
    final UserDataInput userDataRequest =
        UserDataInput.builder()
            .launchTemplate(true)
            .asgName(asgName)
            .launchSettingName(launchTemplateName)
            .region(region)
            .account(account)
            .environment(env)
            .accountType(accType)
            .iamRole(iamRole)
            .imageId(imageId)
            .userDataOverride(userDataOverride)
            .base64UserData(base64UserData)
            .build();

    request.setUserData(userDataProviderAggregator.aggregate(userDataRequest));
  }

  private List<LaunchTemplateBlockDeviceMappingRequest> buildDeviceMapping(
      List<AmazonBlockDevice> amazonBlockDevices) {
    final List<LaunchTemplateBlockDeviceMappingRequest> mappings = new ArrayList<>();
    for (AmazonBlockDevice blockDevice : amazonBlockDevices) {
      LaunchTemplateBlockDeviceMappingRequest mapping =
          new LaunchTemplateBlockDeviceMappingRequest().withDeviceName(blockDevice.getDeviceName());
      if (blockDevice.getVirtualName() != null) {
        mapping.setVirtualName(blockDevice.getVirtualName());
      } else {
        mapping.setEbs(getLaunchTemplateEbsBlockDeviceRequest(blockDevice));
      }

      mappings.add(mapping);
    }
    return mappings;
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

    if (blockDevice.getKmsKeyId() != null) {
      blockDeviceRequest.setKmsKeyId(blockDevice.getKmsKeyId());
    }
    return blockDeviceRequest;
  }

  @NotNull
  private Optional<LaunchTemplateTagSpecificationRequest> tagSpecification(
      Collection<AmazonResourceTagger> amazonResourceTaggers, @NotNull String serverGroupName) {
    if (amazonResourceTaggers != null && !amazonResourceTaggers.isEmpty()) {
      List<Tag> volumeTags =
          amazonResourceTaggers.stream()
              .flatMap(t -> t.volumeTags(serverGroupName).stream())
              .map(t -> new Tag(t.getKey(), t.getValue()))
              .collect(Collectors.toList());

      if (!volumeTags.isEmpty()) {
        return Optional.of(
            new LaunchTemplateTagSpecificationRequest()
                .withResourceType("volume")
                .withTags(volumeTags));
      }
    }

    return Optional.empty();
  }
}
