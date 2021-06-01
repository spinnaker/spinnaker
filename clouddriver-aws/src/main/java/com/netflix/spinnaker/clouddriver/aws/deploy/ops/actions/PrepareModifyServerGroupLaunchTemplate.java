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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMapping;
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptions;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver;
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils.BlockDeviceConfig;
import com.netflix.spinnaker.clouddriver.aws.deploy.ModifyServerGroupUtils;
import com.netflix.spinnaker.clouddriver.aws.deploy.ResolvedAmiResult;
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

/**
 * Action to prepare for the description for launch template changes. This action may be skipped if
 * no launch template changes are requested.
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
        "[SAGA_ACTION] Performing modifyServerGroupLaunchTemplate operation for description "
            + description);

    RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());
    AutoScalingGroup autoScalingGroup =
        getAutoScalingGroup(description.getAsgName(), regionScopedProvider);
    LaunchTemplateVersion launchTemplateVersion =
        getLaunchTemplateVersion(autoScalingGroup, regionScopedProvider);
    ResponseLaunchTemplateData launchTemplateData = launchTemplateVersion.getLaunchTemplateData();

    boolean isAsgBackedByMip = autoScalingGroup.getMixedInstancesPolicy() != null;
    boolean asgUsesSpotLt = launchTemplateData.getInstanceMarketOptions() != null;

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
    transformLaunchTemplateVersionToDesc(
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
                autoScalingGroup.getMixedInstancesPolicy() != null
                    ? autoScalingGroup
                        .getMixedInstancesPolicy()
                        .getLaunchTemplate()
                        .getLaunchTemplateSpecification()
                    : autoScalingGroup.getLaunchTemplate())
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

  private void transformLaunchTemplateVersionToDesc(
      Saga saga,
      ModifyServerGroupLaunchTemplateDescription modifyDesc,
      LaunchTemplateVersion sourceLtVersion,
      String accountId,
      AmazonEC2 amazonEC2,
      AutoScalingGroup autoScalingGroup) {
    ResponseLaunchTemplateData sourceLtData = sourceLtVersion.getLaunchTemplateData();

    modifyDesc.setSpotPrice(
        getSpotMaxPrice(modifyDesc.getSpotPrice(), autoScalingGroup, sourceLtData));
    modifyDesc.setImageId(
        getImageId(saga, amazonEC2, accountId, modifyDesc).orElse(sourceLtData.getImageId()));

    Set<String> securityGroups = new HashSet<>();
    if (modifyDesc.getSecurityGroups() != null) {
      securityGroups.addAll(modifyDesc.getSecurityGroups());
    }

    Boolean includePreviousGroups =
        Optional.ofNullable(modifyDesc.getSecurityGroupsAppendOnly())
            .orElseGet(securityGroups::isEmpty);
    if (includePreviousGroups) {
      securityGroups.addAll(
          sourceLtData.getNetworkInterfaces().stream()
              .filter(i -> i.getDeviceIndex() == 0)
              .findFirst()
              .map(LaunchTemplateInstanceNetworkInterfaceSpecification::getGroups)
              .orElse(Collections.emptyList()));
    }
    modifyDesc.setSecurityGroups(new ArrayList<>(securityGroups));

    LaunchTemplateIamInstanceProfileSpecification iamInstanceProfileInLt =
        sourceLtData.getIamInstanceProfile();
    String iamRoleInLt = null;
    if (iamInstanceProfileInLt != null) {
      iamRoleInLt = iamInstanceProfileInLt.getName();
    }
    modifyDesc.setIamRole(Optional.ofNullable(modifyDesc.getIamRole()).orElse(iamRoleInLt));
    modifyDesc.setKeyPair(
        Optional.ofNullable(modifyDesc.getKeyPair()).orElseGet(sourceLtData::getKeyName));
    modifyDesc.setRamdiskId(
        Optional.ofNullable(modifyDesc.getRamdiskId()).orElseGet(sourceLtData::getRamDiskId));
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
        && !modifyDesc.getInstanceType().equals(ltDataOldVersion.getInstanceType())) {
      final List<AmazonBlockDevice> defaultBdmForNewType =
          blockDeviceConfig.getBlockDevicesForInstanceType(modifyDesc.getInstanceType());
      // if copy from source flag is unset, use default mapping for the modified instance type
      if (!modifyDesc.isCopySourceCustomBlockDeviceMappings()) {
        return defaultBdmForNewType;
      } else {
        // if prior version used default mapping do use default mapping on new version as well
        List<AmazonBlockDevice> defaultBdmForOldType =
            blockDeviceConfig.getBlockDevicesForInstanceType(ltDataOldVersion.getInstanceType());
        if (matchingBlockDevices(ltDataOldVersion.getBlockDeviceMappings(), defaultBdmForOldType)) {
          return defaultBdmForNewType;
        }
      }
    }
    return null;
  }

  private Optional<String> getImageId(
      Saga saga,
      AmazonEC2 ec2,
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
    if (autoScalingGroup.getMixedInstancesPolicy() != null) {
      spotMaxPriceForAsg =
          Optional.ofNullable(
              autoScalingGroup
                  .getMixedInstancesPolicy()
                  .getInstancesDistribution()
                  .getSpotMaxPrice());
    } else {
      LaunchTemplateInstanceMarketOptions marketOptions = ltData.getInstanceMarketOptions();
      if (marketOptions != null && marketOptions.getSpotOptions() != null) {
        spotMaxPriceForAsg = Optional.ofNullable(marketOptions.getSpotOptions().getMaxPrice());
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
            .withDeviceName(mapping.getDeviceName())
            .withVirtualName(mapping.getVirtualName())
            .withSize(mapping.getEbs().getVolumeSize());

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
