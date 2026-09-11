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

import com.netflix.spinnaker.clouddriver.aws.deploy.AmazonResourceTagger;
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

@Slf4j
public class LaunchTemplateService {
  private final Ec2Client ec2;
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
      Ec2Client ec2,
      UserDataProviderAggregator userDataProviderAggregator,
      LocalFileUserDataProperties localFileUserDataProperties,
      Collection<AmazonResourceTagger> amazonResourceTaggers) {
    this.ec2 = ec2;
    this.userDataProviderAggregator = userDataProviderAggregator;
    this.localFileUserDataProperties = localFileUserDataProperties;
    this.amazonResourceTaggers = amazonResourceTaggers;
  }

  public Optional<LaunchTemplateVersion> getLaunchTemplateVersion(
      LaunchTemplateSpecification launchTemplateSpecification) {
    final List<LaunchTemplateVersion> versions = new ArrayList<>();
    final String version = launchTemplateSpecification.version();
    DescribeLaunchTemplateVersionsRequest request =
        DescribeLaunchTemplateVersionsRequest.builder()
            .launchTemplateId(launchTemplateSpecification.launchTemplateId())
            .build();

    while (true) {
      final DescribeLaunchTemplateVersionsResponse result =
          ec2.describeLaunchTemplateVersions(request);
      versions.addAll(result.launchTemplateVersions());
      if (result.nextToken() != null) {
        request = request.toBuilder().nextToken(result.nextToken()).build();
      } else {
        break;
      }
    }

    if ("$Latest".equals(version)) {
      return versions.stream().max(comparing(LaunchTemplateVersion::versionNumber));
    } else if ("$Default".equals(version)) {
      return versions.stream().filter(LaunchTemplateVersion::defaultVersion).findFirst();
    }

    return versions.stream()
        .filter(i -> i.versionNumber().equals(Long.parseLong(version)))
        .findFirst();
  }

  public LaunchTemplate createLaunchTemplate(
      AsgConfiguration asgConfig, String asgName, String launchTemplateName) {
    final RequestLaunchTemplateData data =
        buildLaunchTemplateData(asgConfig, asgName, launchTemplateName);
    log.info("Creating launch template with name {}", launchTemplateName);
    return retrySupport.retry(
        () -> {
          final CreateLaunchTemplateRequest launchTemplateRequest =
              CreateLaunchTemplateRequest.builder()
                  .launchTemplateName(launchTemplateName)
                  .launchTemplateData(data)
                  .build();
          return ec2.createLaunchTemplate(launchTemplateRequest).launchTemplate();
        },
        3,
        Duration.ofMillis(3000),
        false);
  }

  public LaunchTemplateVersion modifyLaunchTemplate(
      NetflixAmazonCredentials credentials,
      ModifyServerGroupLaunchTemplateDescription description,
      LaunchTemplateVersion sourceLtVersion,
      boolean shouldUseMixedInstancesPolicy) {

    RequestLaunchTemplateData data =
        buildLaunchTemplateDataForModify(
            credentials, description, sourceLtVersion, shouldUseMixedInstancesPolicy);
    CreateLaunchTemplateVersionResponse result =
        ec2.createLaunchTemplateVersion(
            CreateLaunchTemplateVersionRequest.builder()
                .launchTemplateId(sourceLtVersion.launchTemplateId())
                .launchTemplateData(data)
                .build());

    log.info(
        String.format(
            "Created new launch template version %s for launch template ID %s",
            result.launchTemplateVersion().versionNumber(),
            result.launchTemplateVersion().launchTemplateId()));

    return result.launchTemplateVersion();
  }

  /**
   * Delete a launch template version. A new launch template when it is modified.
   * https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DeleteLaunchTemplateVersions.html
   *
   * @param launchTemplateId launch template ID for the version to delete
   * @param versionToDelete launch template version to delete
   */
  public void deleteLaunchTemplateVersion(String launchTemplateId, Long versionToDelete) {
    log.info(
        String.format(
            "Attempting to delete launch template version %s for launch template ID %s.",
            versionToDelete, launchTemplateId));

    DeleteLaunchTemplateVersionsResponse result =
        ec2.deleteLaunchTemplateVersions(
            DeleteLaunchTemplateVersionsRequest.builder()
                .launchTemplateId(launchTemplateId)
                .versions(String.valueOf(versionToDelete))
                .build());

    if (result.unsuccessfullyDeletedLaunchTemplateVersions() != null
        && !result.unsuccessfullyDeletedLaunchTemplateVersions().isEmpty()) {
      DeleteLaunchTemplateVersionsResponseErrorItem responseErrorItem =
          result.unsuccessfullyDeletedLaunchTemplateVersions().get(0);
      ResponseError failureResponseError = responseErrorItem.responseError();

      // certain error codes can be considered success when they match the desired end state.
      // this also acts as a safety net in retry scenarios.
      List<String> codesConsideredSuccess =
          List.of("launchTemplateIdDoesNotExist", "launchTemplateVersionDoesNotExist");

      if (failureResponseError != null
          && !codesConsideredSuccess.contains(failureResponseError.codeAsString())) {
        throw new RuntimeException(
            String.format(
                "Failed to delete launch template version %s for launch template ID %s because of error '%s'",
                responseErrorItem.versionNumber(),
                responseErrorItem.launchTemplateId(),
                failureResponseError.codeAsString()));
      }
    }
  }

  /**
   * Build launch template data for launch template modification i.e. new launch template version
   */
  private RequestLaunchTemplateData buildLaunchTemplateDataForModify(
      NetflixAmazonCredentials credentials,
      ModifyServerGroupLaunchTemplateDescription modifyDesc,
      LaunchTemplateVersion sourceLtVersion,
      boolean shouldUseMixedInstancesPolicy) {

    ResponseLaunchTemplateData sourceLtData = sourceLtVersion.launchTemplateData();

    RequestLaunchTemplateData.Builder request =
        RequestLaunchTemplateData.builder()
            .imageId(
                modifyDesc.getImageId() != null ? modifyDesc.getImageId() : sourceLtData.imageId())
            .kernelId(
                StringUtils.isNotBlank(modifyDesc.getKernelId())
                    ? modifyDesc.getKernelId()
                    : sourceLtData.kernelId())
            .instanceType(
                StringUtils.isNotBlank(modifyDesc.getInstanceType())
                    ? modifyDesc.getInstanceType()
                    : sourceLtData.instanceTypeAsString())
            .ramDiskId(
                StringUtils.isNotBlank(modifyDesc.getRamdiskId())
                    ? modifyDesc.getRamdiskId()
                    : sourceLtData.ramDiskId())
            .ebsOptimized(
                Optional.ofNullable(modifyDesc.getEbsOptimized())
                    .orElseGet(sourceLtData::ebsOptimized));

    // key name
    if (StringUtils.isNotBlank(modifyDesc.getKeyPair())) {
      request.keyName(modifyDesc.getKeyPair());
    } else if (StringUtils.isNotBlank(sourceLtData.keyName())) {
      request.keyName(sourceLtData.keyName());
    }

    // iam instance profile
    if (StringUtils.isNotBlank(modifyDesc.getIamRole())) {
      request.iamInstanceProfile(
          LaunchTemplateIamInstanceProfileSpecificationRequest.builder()
              .name(modifyDesc.getIamRole())
              .build());
    } else if (sourceLtData.iamInstanceProfile() != null
        && StringUtils.isNotBlank(sourceLtData.iamInstanceProfile().name())) {
      request.iamInstanceProfile(
          LaunchTemplateIamInstanceProfileSpecificationRequest.builder()
              .name(sourceLtData.iamInstanceProfile().name())
              .build());
    }

    // instance monitoring
    if (modifyDesc.getInstanceMonitoring() != null) {
      request.monitoring(
          LaunchTemplatesMonitoringRequest.builder()
              .enabled(modifyDesc.getInstanceMonitoring())
              .build());
    } else if (sourceLtData.monitoring() != null && sourceLtData.monitoring().enabled() != null) {
      request.monitoring(
          LaunchTemplatesMonitoringRequest.builder()
              .enabled(sourceLtData.monitoring().enabled())
              .build());
    }

    // block device mappings
    if (modifyDesc.getBlockDevices() != null) {
      request.blockDeviceMappings(buildDeviceMapping(modifyDesc.getBlockDevices()));
    } else if (sourceLtData.blockDeviceMappings() != null) {
      request.blockDeviceMappings(
          buildDeviceMapping(
              AsgConfigHelper.transformLaunchTemplateBlockDeviceMapping(
                  sourceLtData.blockDeviceMappings())));
    }

    // tags
    Optional<LaunchTemplateTagSpecificationRequest> tagSpecification =
        tagSpecification(amazonResourceTaggers, null, modifyDesc.getAsgName());
    if (tagSpecification.isPresent()) {
      request.tagSpecifications(tagSpecification.get());
    }

    /*
     Copy over the original user data only if the UserDataProviders behavior is disabled.
     This is to avoid having duplicate user data.
    */
    String base64UserData =
        (localFileUserDataProperties != null && !localFileUserDataProperties.isEnabled())
            ? sourceLtData.userData()
            : null;
    setUserData(
        request,
        modifyDesc.getAsgName(),
        sourceLtVersion.launchTemplateName(),
        modifyDesc.getRegion(),
        modifyDesc.getAccount(),
        credentials.getEnvironment(),
        credentials.getAccountType(),
        modifyDesc.getIamRole(),
        modifyDesc.getImageId(),
        base64UserData,
        modifyDesc.getLegacyUdf(),
        modifyDesc.getUserDataOverride());

    // metadata options
    if (modifyDesc.getRequireIMDV2() != null) {
      request.metadataOptions(
          LaunchTemplateInstanceMetadataOptionsRequest.builder()
              .httpTokens(modifyDesc.getRequireIMDV2() ? "required" : "")
              .build());
    } else if (sourceLtData.metadataOptions() != null) {
      request.metadataOptions(
          LaunchTemplateInstanceMetadataOptionsRequest.builder()
              .httpTokens(sourceLtData.metadataOptions().httpTokensAsString())
              .build());
    }

    // set instance market options only when mixed instances policy is NOT used in order to maintain
    // launch template compatibility
    if (!shouldUseMixedInstancesPolicy) {
      setSpotInstanceMarketOptions(request, modifyDesc.getSpotPrice());
    }

    // credit specification
    if (modifyDesc.getUnlimitedCpuCredits() != null) {
      // compatibility is already validated by validator
      setCreditSpecification(request, modifyDesc.getUnlimitedCpuCredits());
    } else if (sourceLtData.creditSpecification() != null) {
      // The description might include changed instance types.
      // Ensure compatibility before using value from sourceLtData.
      Boolean unlimitedCpuCreditsFromSrcAsg =
          AsgConfigHelper.getUnlimitedCpuCreditsFromAncestorLt(
              sourceLtData.creditSpecification(),
              InstanceTypeUtils.isBurstingSupportedByAllTypes(modifyDesc.getAllInstanceTypes()));
      setCreditSpecification(request, unlimitedCpuCreditsFromSrcAsg);
    }

    // network interfaces
    LaunchTemplateInstanceNetworkInterfaceSpecification defaultInterface;
    if (sourceLtData.networkInterfaces() != null && !sourceLtData.networkInterfaces().isEmpty()) {
      defaultInterface =
          sourceLtData.networkInterfaces().stream()
              .filter(i -> i.deviceIndex() == 0)
              .findFirst()
              .orElseGet(
                  () -> LaunchTemplateInstanceNetworkInterfaceSpecification.builder().build());
    } else {
      defaultInterface = LaunchTemplateInstanceNetworkInterfaceSpecification.builder().build();
    }

    request.networkInterfaces(
        LaunchTemplateInstanceNetworkInterfaceSpecificationRequest.builder()
            .associatePublicIpAddress(
                Optional.ofNullable(modifyDesc.getAssociatePublicIpAddress())
                    .orElseGet(() -> defaultInterface.associatePublicIpAddress()))
            .ipv6AddressCount(
                modifyDesc.getAssociateIPv6Address() != null
                    ? modifyDesc.getAssociateIPv6Address() ? 1 : 0
                    : defaultInterface.ipv6AddressCount() != null
                            && defaultInterface.ipv6AddressCount() > 0
                        ? 1
                        : 0)
            .groups(
                modifyDesc.getSecurityGroups() != null && !modifyDesc.getSecurityGroups().isEmpty()
                    ? modifyDesc.getSecurityGroups()
                    : defaultInterface.groups())
            .deviceIndex(0)
            .build());

    // Nitro Enclave options
    if (modifyDesc.getEnableEnclave() != null) {
      request.enclaveOptions(
          LaunchTemplateEnclaveOptionsRequest.builder()
              .enabled(modifyDesc.getEnableEnclave())
              .build());
    } else if (sourceLtData.enclaveOptions() != null) {
      request.enclaveOptions(
          LaunchTemplateEnclaveOptionsRequest.builder()
              .enabled(sourceLtData.enclaveOptions().enabled())
              .build());
    }

    return request.build();
  }

  /** Build launch template data for new launch template creation */
  private RequestLaunchTemplateData buildLaunchTemplateData(
      AsgConfiguration asgConfig, String asgName, String launchTemplateName) {
    RequestLaunchTemplateData.Builder request =
        RequestLaunchTemplateData.builder()
            .imageId(asgConfig.getAmi())
            .kernelId(asgConfig.getKernelId())
            .instanceType(asgConfig.getInstanceType())
            .ramDiskId(asgConfig.getRamdiskId())
            .ebsOptimized(asgConfig.getEbsOptimized())
            .keyName(asgConfig.getKeyPair())
            .iamInstanceProfile(
                LaunchTemplateIamInstanceProfileSpecificationRequest.builder()
                    .name(asgConfig.getIamRole())
                    .build())
            .monitoring(
                LaunchTemplatesMonitoringRequest.builder()
                    .enabled(asgConfig.getInstanceMonitoring())
                    .build());

    Optional<LaunchTemplateTagSpecificationRequest> tagSpecification =
        tagSpecification(amazonResourceTaggers, asgConfig.getBlockDeviceTags(), asgName);
    if (tagSpecification.isPresent()) {
      request.tagSpecifications(tagSpecification.get());
    }

    if (asgConfig.getPlacement() != null) {
      request.placement(
          LaunchTemplatePlacementRequest.builder()
              .affinity(asgConfig.getPlacement().getAffinity())
              .availabilityZone(asgConfig.getPlacement().getAvailabilityZone())
              .groupName(asgConfig.getPlacement().getGroupName())
              .hostId(asgConfig.getPlacement().getHostId())
              .tenancy(asgConfig.getPlacement().getTenancy())
              .hostResourceGroupArn(asgConfig.getPlacement().getHostResourceGroupArn())
              .partitionNumber(asgConfig.getPlacement().getPartitionNumber())
              .spreadDomain(asgConfig.getPlacement().getSpreadDomain())
              .build());
    }

    if (asgConfig.getLicenseSpecifications() != null) {
      request.licenseSpecifications(
          asgConfig.getLicenseSpecifications().stream()
              .map(
                  licenseSpecification ->
                      LaunchTemplateLicenseConfigurationRequest.builder()
                          .licenseConfigurationArn(licenseSpecification.getArn())
                          .build())
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
        asgConfig.getLegacyUdf(),
        asgConfig.getUserDataOverride());

    // metadata options
    if (asgConfig.getRequireIMDSv2() != null && asgConfig.getRequireIMDSv2()) {
      request.metadataOptions(
          LaunchTemplateInstanceMetadataOptionsRequest.builder().httpTokens("required").build());
    }

    // set instance market options only when mixed instances policy is NOT used in order to maintain
    // launch template compatibility
    if (!asgConfig.shouldUseMixedInstancesPolicy()) {
      setSpotInstanceMarketOptions(request, asgConfig.getSpotMaxPrice());
    }

    setCreditSpecification(request, asgConfig.getUnlimitedCpuCredits());

    // network interfaces
    request.networkInterfaces(
        LaunchTemplateInstanceNetworkInterfaceSpecificationRequest.builder()
            .associatePublicIpAddress(asgConfig.getAssociatePublicIpAddress())
            .ipv6AddressCount(asgConfig.getAssociateIPv6Address() ? 1 : 0)
            .groups(asgConfig.getSecurityGroups())
            .deviceIndex(0)
            .build());

    // Nitro Enclave options
    if (asgConfig.getEnableEnclave() != null) {
      request.enclaveOptions(
          LaunchTemplateEnclaveOptionsRequest.builder()
              .enabled(asgConfig.getEnableEnclave())
              .build());
    }

    // block device mappings
    if (asgConfig.getBlockDevices() != null && !asgConfig.getBlockDevices().isEmpty()) {
      request.blockDeviceMappings(buildDeviceMapping(asgConfig.getBlockDevices()));
    }

    return request.build();
  }

  /** Set credit option for burstable performance instances to 'unlimited' only if explicitly set */
  private void setCreditSpecification(
      RequestLaunchTemplateData.Builder request, Boolean unlimitedCpuCredits) {
    if (unlimitedCpuCredits != null) {
      request.creditSpecification(
          CreditSpecificationRequest.builder()
              .cpuCredits(unlimitedCpuCredits ? UNLIMITED_CPU_CREDITS : STANDARD_CPU_CREDITS)
              .build());
    }
  }

  /**
   * Set instance market options, required when launching spot instances
   * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-launchtemplate-launchtemplatedata-instancemarketoptions.html
   */
  private void setSpotInstanceMarketOptions(
      RequestLaunchTemplateData.Builder request, String maxSpotPrice) {
    if (maxSpotPrice != null && StringUtils.isNotEmpty(maxSpotPrice.trim())) {
      request.instanceMarketOptions(
          LaunchTemplateInstanceMarketOptionsRequest.builder()
              .marketType(MarketType.SPOT)
              .spotOptions(
                  LaunchTemplateSpotMarketOptionsRequest.builder().maxPrice(maxSpotPrice).build())
              .build());
    }
  }

  private void setUserData(
      RequestLaunchTemplateData.Builder request,
      String asgName,
      String launchTemplateName,
      String region,
      String account,
      String env,
      String accType,
      String iamRole,
      String imageId,
      String base64UserData,
      Boolean legacyUdf,
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
            .legacyUdf(legacyUdf)
            .build();

    request.userData(userDataProviderAggregator.aggregate(userDataRequest));
  }

  private List<LaunchTemplateBlockDeviceMappingRequest> buildDeviceMapping(
      List<AmazonBlockDevice> amazonBlockDevices) {
    if (amazonBlockDevices == null || amazonBlockDevices.isEmpty()) {
      return null;
    }

    final List<LaunchTemplateBlockDeviceMappingRequest> mappings = new ArrayList<>();
    for (AmazonBlockDevice blockDevice : amazonBlockDevices) {
      LaunchTemplateBlockDeviceMappingRequest.Builder mapping =
          LaunchTemplateBlockDeviceMappingRequest.builder().deviceName(blockDevice.getDeviceName());
      if (blockDevice.getVirtualName() != null) {
        mapping.virtualName(blockDevice.getVirtualName());
      } else {
        mapping.ebs(getLaunchTemplateEbsBlockDeviceRequest(blockDevice));
      }

      mappings.add(mapping.build());
    }
    return mappings;
  }

  private LaunchTemplateEbsBlockDeviceRequest getLaunchTemplateEbsBlockDeviceRequest(
      AmazonBlockDevice blockDevice) {
    final LaunchTemplateEbsBlockDeviceRequest.Builder blockDeviceRequest =
        LaunchTemplateEbsBlockDeviceRequest.builder().volumeSize(blockDevice.getSize());

    if (blockDevice.getDeleteOnTermination() != null) {
      blockDeviceRequest.deleteOnTermination(blockDevice.getDeleteOnTermination());
    }

    if (blockDevice.getVolumeType() != null) {
      blockDeviceRequest.volumeType(blockDevice.getVolumeType());
    }

    if (blockDevice.getIops() != null) {
      blockDeviceRequest.iops(blockDevice.getIops());
    }

    if (blockDevice.getThroughput() != null) {
      blockDeviceRequest.throughput(blockDevice.getThroughput());
    }

    if (blockDevice.getSnapshotId() != null) {
      blockDeviceRequest.snapshotId(blockDevice.getSnapshotId());
    }

    if (blockDevice.getEncrypted() != null) {
      blockDeviceRequest.encrypted(blockDevice.getEncrypted());
    }

    if (blockDevice.getKmsKeyId() != null) {
      blockDeviceRequest.kmsKeyId(blockDevice.getKmsKeyId());
    }
    return blockDeviceRequest.build();
  }

  @NotNull
  private Optional<LaunchTemplateTagSpecificationRequest> tagSpecification(
      Collection<AmazonResourceTagger> amazonResourceTaggers,
      @Nullable Map<String, String> blockDeviceTags,
      @NotNull String serverGroupName) {
    if (amazonResourceTaggers != null && !amazonResourceTaggers.isEmpty()) {
      List<Tag> volumeTags =
          amazonResourceTaggers.stream()
              .flatMap(t -> t.volumeTags(blockDeviceTags, serverGroupName).stream())
              .map(t -> Tag.builder().key(t.getKey()).value(t.getValue()).build())
              .collect(Collectors.toList());

      if (!volumeTags.isEmpty()) {
        return Optional.of(
            LaunchTemplateTagSpecificationRequest.builder()
                .resourceType("volume")
                .tags(volumeTags)
                .build());
      }
    }

    return Optional.empty();
  }
}
