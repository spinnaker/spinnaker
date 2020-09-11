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
import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver;
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig;
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
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
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

@Component
public class PrepareModifyServerGroupLaunchTemplate
    implements SagaAction<
        PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand> {
  private final BlockDeviceConfig blockDeviceConfig;
  private final AccountCredentialsRepository credentialsRepository;
  private final RegionScopedProviderFactory regionScopedProviderFactory;

  public PrepareModifyServerGroupLaunchTemplate(
      BlockDeviceConfig blockDeviceConfig,
      AccountCredentialsRepository credentialsRepository,
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

    saga.log("Preparing launch template update: " + description);

    RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());

    AutoScalingGroup autoScalingGroup =
        getAutoScalingGroup(description.getAsgName(), regionScopedProvider);
    LaunchTemplateVersion launchTemplateVersion =
        getLaunchTemplateVersion(autoScalingGroup, regionScopedProvider);
    ResponseLaunchTemplateData launchTemplateData = launchTemplateVersion.getLaunchTemplateData();

    LaunchTemplateInstanceMarketOptions marketOptions =
        launchTemplateData.getInstanceMarketOptions();
    String maxPrice = null;
    if (marketOptions != null && marketOptions.getSpotOptions() != null) {
      maxPrice = marketOptions.getSpotOptions().getMaxPrice();
    }

    String spotPrice = Optional.ofNullable(description.getSpotPrice()).orElse(maxPrice);
    if (spotPrice != null && spotPrice.equals("")) {
      // a spotPrice of "" indicates that it should be removed regardless of value on source
      // launch template
      description.setSpotPrice(null);
    }

    description.setSpotPrice(spotPrice);
    if (description.getImageId() == null) {
      saga.log("Resolving Image Id for " + description.getAmiName());
      // resolve imageId
      Optional<String> imageId =
          getImageId(
              description.getAmiName(),
              description.getRegion(),
              credentials.getAccountId(),
              Collections.emptyList(),
              regionScopedProvider.getAmazonEC2());
      description.setImageId(imageId.get());
    }

    Set<String> securityGroups =
        new HashSet<>(
            Optional.ofNullable(description.getSecurityGroups()).orElse(new ArrayList<String>()));

    if (description.getSecurityGroupsAppendOnly() != null
        && description.getSecurityGroupsAppendOnly()) {
      // append security groups
      securityGroups.addAll(launchTemplateData.getSecurityGroupIds());
    }

    // if we are changing instance types and don't have explicitly supplied block device mappings
    String instanceType =
        Optional.ofNullable(description.getInstanceType())
            .orElseGet(launchTemplateData::getInstanceType);
    LaunchTemplateIamInstanceProfileSpecification iamInstanceProfile =
        launchTemplateData.getIamInstanceProfile();
    String iamRole = null;
    if (iamInstanceProfile != null) {
      iamRole = iamInstanceProfile.getName();
    }

    description.setIamRole(Optional.ofNullable(description.getIamRole()).orElse(iamRole));
    description.setKeyPair(
        Optional.ofNullable(description.getIamRole()).orElseGet(launchTemplateData::getKeyName));
    description.setRamdiskId(
        Optional.ofNullable(description.getRamdiskId())
            .orElseGet(launchTemplateData::getRamDiskId));

    List<AmazonBlockDevice> blockDevices = description.getBlockDevices();
    if (blockDevices == null
        && instanceType != null
        && !instanceType.equals(launchTemplateData.getInstanceType())) {
      // set default mapping for instance type if we are changing instance types and don't have
      // explicitly supplied block device mappings
      List<AmazonBlockDevice> devices =
          blockDeviceConfig.getBlockDevicesForInstanceType(instanceType);
      if (!description.isCopySourceCustomBlockDeviceMappings()) {
        description.setBlockDevices(devices);
      } else {
        // if prior version used default mapping do use default mapping on new version as well
        List<AmazonBlockDevice> defaultDevices =
            blockDeviceConfig.getBlockDevicesForInstanceType(launchTemplateData.getInstanceType());
        if (matchingBlockDevices(launchTemplateData.getBlockDeviceMappings(), defaultDevices)) {
          description.setBlockDevices(devices);
        }
      }
    }

    return new Result(
        ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand.builder()
            .description(description)
            .sourceVersion(launchTemplateVersion)
            .build(),
        Collections.emptyList());
  }

  private Optional<String> getImageId(
      String amiName, String region, String accountId, List priorOutputs, AmazonEC2 ec2) {
    if (amiName != null) {
      try {
        ResolvedAmiResult ami =
            getAmiResult(priorOutputs, region, amiName)
                .orElse(AmiIdResolver.resolveAmiIdFromAllSources(ec2, region, amiName, accountId));
        return Optional.ofNullable(ami.getAmiId());
      } catch (Exception e) {
        throw new LaunchTemplateException(
            String.format("Failed to resolve image id for %s", amiName), e);
      }
    }

    return Optional.empty();
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
        Optional.ofNullable(autoScalingGroup.getLaunchTemplate())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Server group %s is not backed by a launch template",
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

  private boolean matchingBlockDevices(
      List<LaunchTemplateBlockDeviceMapping> mappings,
      List<AmazonBlockDevice> blockDevicesForInstanceType) {
    for (LaunchTemplateBlockDeviceMapping mapping : mappings) {
      if (blockDevicesForInstanceType.stream()
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

  private Optional<ResolvedAmiResult> getAmiResult(
      List priorOutputs, String region, String amiName) {
    for (Object o : priorOutputs) {
      if (o instanceof ResolvedAmiResult) {
        ResolvedAmiResult result = ((ResolvedAmiResult) o);
        if (result.getRegion().equals(region)
            && (amiName.equals(result.getAmiId()) || amiName.equals(result.getAmiName()))) {
          return Optional.of(result);
        }
      }
    }

    return Optional.empty();
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
