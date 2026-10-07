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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver;
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils.BlockDeviceConfig;
import com.netflix.spinnaker.clouddriver.aws.deploy.ModifyServerGroupUtils;
import com.netflix.spinnaker.clouddriver.aws.deploy.ResolvedAmiResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ModifyServerGroupLaunchTemplateAtomicOperation.LaunchTemplateException;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateBlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateIamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateInstanceMarketOptions;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecification;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateVersion;
import software.amazon.awssdk.services.ec2.model.ResponseLaunchTemplateData;

/**
 * Action to prepare the description of type ModifyServerGroupLaunchTemplateDescription for launch
 * template and server group configuration changes. Steps: 1. populate description with config from
 * server group's mixed instances policy 2. populate description with config from current launch
 * template version, in preparation to create a new version
 *
 * <p>Step 2 of this action may be skipped if no launch template changes are requested.
 */
@Component
public class PrepareModifyServerGroupLaunchTemplate
    implements SagaAction<
        PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand> {
  private final BlockDeviceConfig blockDeviceConfig;
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private final RegionScopedProviderFactory regionScopedProviderFactory;

  public PrepareModifyServerGroupLaunchTemplate(
      BlockDeviceConfig blockDeviceConfig,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      RegionScopedProviderFactory regionScopedProviderFactory) {
    this.blockDeviceConfig = blockDeviceConfig;
    this.credentialsRepository = credentialsRepository;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull PrepareModifyServerGroupLaunchTemplateCommand command, @NotNull Saga saga) {
    ModifyServerGroupLaunchTemplateDescription description = command.description;
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) credentialsRepository.getOne(description.getAccount());

    saga.log(
        "[SAGA_ACTION] Performing modifyServerGroupLaunchTemplate operation for server group "
            + description.getAsgName());

    RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());
    AutoScalingGroup autoScalingGroup =
        getAutoScalingGroup(description.getAsgName(), regionScopedProvider);
    LaunchTemplateVersion launchTemplateVersion =
        getLaunchTemplateVersion(autoScalingGroup, regionScopedProvider);
    ResponseLaunchTemplateData launchTemplateData = launchTemplateVersion.launchTemplateData();
    boolean isAsgBackedByMip = autoScalingGroup.mixedInstancesPolicy() != null;

    // Step #1: populate description with config from server group's mixed instances policy
    if (autoScalingGroup.mixedInstancesPolicy() != null) {
      populateDescWithMipFields(description, autoScalingGroup);
    }

    // Determine if step #2(populate description with config from current launch template version)
    // can be skipped
    boolean asgUsesSpotLt = launchTemplateData.instanceMarketOptions() != null;

    /**
     * A valid request should include fields mapped to either launch template or AWS ASG config or
     * both. ModifyServerGroupLaunchTemplateValidator rejects requests with only metadata fields
     * i.e. no launch template or ASG config changes.
     */
    final Set<String> nonMetadataFieldsSet =
        ModifyServerGroupUtils.getNonMetadataFieldsSetInReq(description);
    boolean isReqToModifyMipFieldsOnly =
        nonMetadataFieldsSet.stream()
            .allMatch(
                f ->
                    ModifyServerGroupLaunchTemplateDescription.getMixedInstancesPolicyFieldNames()
                        .contains(f));

    // Selectively skip launch template modification in some cases when NO launch template changes
    // are required:
    // 1. ASG with MIP + isReqToModifyMixedInstancesPolicyOnlyFields (including spotMaxPrice)
    // 2. ASG with OD LT + isReqToModifyMixedInstancesPolicyOnlyFields (including spotMaxPrice)
    //    Reason is to prevent an error like ->
    //    AmazonAutoScalingException: Incompatible launch template:
    //        You cannot use a launch template that is set to request Spot Instances
    //        (InstanceMarketOptions) when you configure an Auto Scaling group with a mixed
    // instances policy.
    //        Add a different launch template to the group and try again.
    if (isReqToModifyMipFieldsOnly && (isAsgBackedByMip || !asgUsesSpotLt)) {
      saga.log(
          "[SAGA_ACTION] Skipping PrepareModifyServerGroupLaunchTemplate as only mixed instances policy will be updated.");

      return new Result(
          ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand.builder()
              .description(description)
              .isReqToModifyLaunchTemplate(false)
              .isAsgBackedByMixedInstancesPolicy(isAsgBackedByMip)
              .isReqToUpgradeAsgToMixedInstancesPolicy(!isAsgBackedByMip)
              .sourceVersion(launchTemplateVersion)
              .build(),
          Collections.emptyList());
    }

    saga.log("[SAGA_ACTION] Preparing for launch template modification");
    populateDescWithLaunchTemplateVersion(
        saga,
        description,
        launchTemplateVersion,
        credentials.getAccountId(),
        regionScopedProvider.getAmazonEC2(),
        autoScalingGroup);

    boolean isReqToModifyAtleastOneMipOnlyField =
        nonMetadataFieldsSet.stream()
            .anyMatch(
                f ->
                    ModifyServerGroupLaunchTemplateDescription
                        .getMixedInstancesPolicyOnlyFieldNames()
                        .contains(f));

    return new Result(
        ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand.builder()
            .description(description)
            .isReqToModifyLaunchTemplate(true)
            .isAsgBackedByMixedInstancesPolicy(isAsgBackedByMip)
            .isReqToUpgradeAsgToMixedInstancesPolicy(
                !isAsgBackedByMip
                    && isReqToModifyAtleastOneMipOnlyField) // upgrade to MIP if request includes at
            // least 1 MIP field (along with 1 or
            // more launch template fields)
            .sourceVersion(launchTemplateVersion)
            .build(),
        Collections.emptyList());
  }

  private AutoScalingGroup getAutoScalingGroup(
      String autoScalingGroupName, RegionScopedProvider regionScopedProvider) {
    try {
      return regionScopedProvider.getAsgService().getAutoScalingGroup(autoScalingGroupName);
    } catch (Exception e) {
      throw new LaunchTemplateException(
          String.format("Failed to get server group %s.", autoScalingGroupName), e);
    }
  }

  private LaunchTemplateVersion getLaunchTemplateVersion(
      AutoScalingGroup autoScalingGroup, RegionScopedProvider regionScopedProvider) {
    LaunchTemplateSpecification launchTemplateSpec =
        Optional.ofNullable(
                autoScalingGroup.mixedInstancesPolicy() != null
                    ? autoScalingGroup
                        .mixedInstancesPolicy()
                        .launchTemplate()
                        .launchTemplateSpecification()
                    : autoScalingGroup.launchTemplate())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Server group is not backed by a launch template.\n%s",
                            autoScalingGroup)));

    return regionScopedProvider
        .getLaunchTemplateService()
        .getLaunchTemplateVersion(launchTemplateSpec)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Requested launch template %s does not exist.", launchTemplateSpec)));
  }

  private void populateDescWithMipFields(
      ModifyServerGroupLaunchTemplateDescription modifyDesc, AutoScalingGroup autoScalingGroup) {
    final InstancesDistribution distInAsg =
        autoScalingGroup.mixedInstancesPolicy().instancesDistribution();

    modifyDesc.setOnDemandAllocationStrategy(
        Optional.ofNullable(modifyDesc.getOnDemandAllocationStrategy())
            .orElse(distInAsg.onDemandAllocationStrategy()));
    modifyDesc.setOnDemandBaseCapacity(
        Optional.ofNullable(modifyDesc.getOnDemandBaseCapacity())
            .orElse(distInAsg.onDemandBaseCapacity()));
    modifyDesc.setOnDemandPercentageAboveBaseCapacity(
        Optional.ofNullable(modifyDesc.getOnDemandPercentageAboveBaseCapacity())
            .orElse(distInAsg.onDemandPercentageAboveBaseCapacity()));
    modifyDesc.setSpotAllocationStrategy(
        Optional.ofNullable(modifyDesc.getSpotAllocationStrategy())
            .orElse(distInAsg.spotAllocationStrategy()));
    modifyDesc.setSpotInstancePools(
        Optional.ofNullable(modifyDesc.getSpotInstancePools())
            .orElse(
                // return the spotInstancePools in ASG iff it is compatible with the
                // spotAllocationStrategy
                modifyDesc.getSpotAllocationStrategy().equals("lowest-price")
                    ? distInAsg.spotInstancePools()
                    : null));
    modifyDesc.setLaunchTemplateOverridesForInstanceType(
        Optional.ofNullable(modifyDesc.getLaunchTemplateOverridesForInstanceType())
            .orElse(
                AsgConfigHelper.getDescriptionOverrides(
                    autoScalingGroup.mixedInstancesPolicy().launchTemplate().overrides())));

    modifyDesc.setSpotPrice(getSpotMaxPrice(modifyDesc.getSpotPrice(), autoScalingGroup, null));
  }

  private void populateDescWithLaunchTemplateVersion(
      Saga saga,
      ModifyServerGroupLaunchTemplateDescription modifyDesc,
      LaunchTemplateVersion sourceLtVersion,
      String accountId,
      Ec2Client amazonEC2,
      AutoScalingGroup autoScalingGroup) {
    ResponseLaunchTemplateData sourceLtData = sourceLtVersion.launchTemplateData();

    modifyDesc.setSpotPrice(
        getSpotMaxPrice(modifyDesc.getSpotPrice(), autoScalingGroup, sourceLtData));
    modifyDesc.setImageId(
        getImageId(saga, amazonEC2, accountId, modifyDesc).orElse(sourceLtData.imageId()));

    Set<String> securityGroups = new HashSet<>();
    if (modifyDesc.getSecurityGroups() != null) {
      securityGroups.addAll(modifyDesc.getSecurityGroups());
    }

    Boolean includePreviousGroups =
        Optional.ofNullable(modifyDesc.getSecurityGroupsAppendOnly())
            .orElseGet(securityGroups::isEmpty);
    if (includePreviousGroups) {
      securityGroups.addAll(
          sourceLtData.networkInterfaces().stream()
              .filter(i -> i.deviceIndex() == 0)
              .findFirst()
              .map(LaunchTemplateInstanceNetworkInterfaceSpecification::groups)
              .orElse(Collections.emptyList()));
    }
    modifyDesc.setSecurityGroups(new ArrayList<>(securityGroups));

    LaunchTemplateIamInstanceProfileSpecification iamInstanceProfileInLt =
        sourceLtData.iamInstanceProfile();
    String iamRoleInLt = null;
    if (iamInstanceProfileInLt != null) {
      iamRoleInLt = iamInstanceProfileInLt.name();
    }
    modifyDesc.setIamRole(Optional.ofNullable(modifyDesc.getIamRole()).orElse(iamRoleInLt));
    modifyDesc.setKeyPair(
        Optional.ofNullable(modifyDesc.getKeyPair()).orElseGet(sourceLtData::keyName));
    modifyDesc.setRamdiskId(
        Optional.ofNullable(modifyDesc.getRamdiskId()).orElseGet(sourceLtData::ramDiskId));
    modifyDesc.setBlockDevices(getBlockDeviceMapping(modifyDesc, sourceLtData));
  }

  private List<AmazonBlockDevice> getBlockDeviceMapping(
      ModifyServerGroupLaunchTemplateDescription modifyDesc,
      ResponseLaunchTemplateData ltDataOldVersion) {

    // if block device mappings are explicitly specified, use them
    if (modifyDesc.getBlockDevices() != null) {
      return modifyDesc.getBlockDevices();
    }

    // modify mapping iff instance type has changed.
    // for multiple instance types case, use the top-level instance type as it is used to derive
    // defaults in {@link BasicAmazonDeployHandler}
    if (modifyDesc.getInstanceType() != null
        && !modifyDesc.getInstanceType().equals(ltDataOldVersion.instanceTypeAsString())) {
      final List<AmazonBlockDevice> defaultBdmForNewType =
          blockDeviceConfig.getBlockDevicesForInstanceType(modifyDesc.getInstanceType());
      // if copy from source flag is unset, use default mapping for the modified instance type
      if (!modifyDesc.isCopySourceCustomBlockDeviceMappings()) {
        return defaultBdmForNewType;
      } else {
        // if prior version used default mapping do use default mapping on new version as well
        List<AmazonBlockDevice> defaultBdmForOldType =
            blockDeviceConfig.getBlockDevicesForInstanceType(
                ltDataOldVersion.instanceTypeAsString());
        if (matchingBlockDevices(ltDataOldVersion.blockDeviceMappings(), defaultBdmForOldType)) {
          return defaultBdmForNewType;
        }
      }
    }
    return null;
  }

  private Optional<String> getImageId(
      Saga saga,
      Ec2Client ec2,
      String accountId,
      ModifyServerGroupLaunchTemplateDescription modifyDesc) {
    if (modifyDesc.getImageId() != null) {
      return Optional.of(modifyDesc.getImageId());
    }

    String amiNameInReq = modifyDesc.getAmiName();
    if (amiNameInReq != null) {
      saga.log("Resolving Image Id for " + amiNameInReq);
      try {
        ResolvedAmiResult ami =
            AmiIdResolver.resolveAmiIdFromAllSources(
                ec2, modifyDesc.getRegion(), amiNameInReq, accountId);
        return Optional.ofNullable(ami.getAmiId());
      } catch (Exception e) {
        throw new LaunchTemplateException(
                String.format("Failed to resolve image id for %s", amiNameInReq), e)
            .setRetryable(true);
      }
    }

    return Optional.empty();
  }

  private String getSpotMaxPrice(
      String spotMaxPriceInReq,
      AutoScalingGroup autoScalingGroup,
      ResponseLaunchTemplateData ltData) {
    if (spotMaxPriceInReq != null) {
      return spotMaxPriceInReq.trim().equals("") ? null : spotMaxPriceInReq;
    }

    Optional<String> spotMaxPriceForAsg = Optional.empty();
    if (autoScalingGroup.mixedInstancesPolicy() != null) {
      spotMaxPriceForAsg =
          Optional.ofNullable(
              autoScalingGroup.mixedInstancesPolicy().instancesDistribution().spotMaxPrice());
    } else {
      LaunchTemplateInstanceMarketOptions marketOptions = ltData.instanceMarketOptions();
      if (marketOptions != null && marketOptions.spotOptions() != null) {
        spotMaxPriceForAsg = Optional.ofNullable(marketOptions.spotOptions().maxPrice());
      }
    }
    if (spotMaxPriceForAsg.isPresent()) {
      return spotMaxPriceForAsg.get().trim().equals("") ? null : spotMaxPriceForAsg.get();
    }

    return null;
  }

  private boolean matchingBlockDevices(
      List<LaunchTemplateBlockDeviceMapping> mappings,
      List<AmazonBlockDevice> defaultBlockDevicesForInstanceType) {
    for (LaunchTemplateBlockDeviceMapping mapping : mappings) {
      if (defaultBlockDevicesForInstanceType.stream()
          .anyMatch(deviceForType -> !matchesDevice(deviceForType, mapping))) {
        return false;
      }
    }

    return true;
  }

  private boolean matchesDevice(
      AmazonBlockDevice deviceForType, LaunchTemplateBlockDeviceMapping mapping) {
    BlockDevice device1 =
        new BlockDevice()
            .withDeviceName(deviceForType.getDeviceName())
            .withVirtualName(deviceForType.getVirtualName())
            .withSize(deviceForType.getSize());

    BlockDevice device2 =
        new BlockDevice()
            .withDeviceName(mapping.deviceName())
            .withVirtualName(mapping.virtualName())
            .withSize(mapping.ebs().volumeSize());

    return device1.equals(device2);
  }

  private static class BlockDevice {
    private String deviceName;
    private String virtualName;
    private Integer size;

    public BlockDevice withDeviceName(String deviceName) {
      this.deviceName = deviceName;
      return this;
    }

    public BlockDevice withVirtualName(String virtualName) {
      this.virtualName = virtualName;
      return this;
    }

    public BlockDevice withSize(Integer size) {
      this.size = size;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BlockDevice that = (BlockDevice) o;
      return Objects.equals(deviceName, that.deviceName)
          && Objects.equals(virtualName, that.virtualName)
          && Objects.equals(size, that.size);
    }

    @Override
    public int hashCode() {
      return Objects.hash(deviceName, virtualName, size);
    }
  }

  @Builder(
      builderClassName = "PrepareModifyServerGroupLaunchTemplateCommandBuilder",
      toBuilder = true)
  @JsonDeserialize(
      builder =
          PrepareModifyServerGroupLaunchTemplateCommand
              .PrepareModifyServerGroupLaunchTemplateCommandBuilder.class)
  @JsonTypeName("prepareModifyServerGroupLaunchTemplateCommand")
  @Value
  public static class PrepareModifyServerGroupLaunchTemplateCommand implements SagaCommand {
    @Nonnull private ModifyServerGroupLaunchTemplateDescription description;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class PrepareModifyServerGroupLaunchTemplateCommandBuilder {}
  }
}
