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

package com.netflix.amazoncomponents.data;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class AmazonObjectMapper extends ObjectMapper {
  public AmazonObjectMapper() {
    configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    addMixInAnnotations(AutoScalingGroup.class, AutoScalingGroupMixins.class);
    addMixInAnnotations(AvailabilityZone.class, AvailabilityZoneMixins.class);
    addMixInAnnotations(Instance.class, InstanceMixins.class);
    addMixInAnnotations(Image.class, ImageMixins.class);
    addMixInAnnotations(com.amazonaws.services.autoscaling.model.Instance.class, AutoScalingInstanceMixins.class);
    addMixInAnnotations(EbsBlockDevice.class, EbsBlockDeviceMixins.class);
    addMixInAnnotations(EbsInstanceBlockDevice.class, EbsInstanceBlockDeviceMixins.class);
    addMixInAnnotations(InstanceNetworkInterface.class, InstanceNetworkInterfaceMixins.class);
    addMixInAnnotations(InstanceNetworkInterfaceAttachment.class, InstanceNetworkInterfaceAttachmentMixins.class);
    addMixInAnnotations(VolumeAttachment.class, VolumeAttachmentMixins.class);
    addMixInAnnotations(Volume.class, VolumeMixins.class);
    addMixInAnnotations(InstancePrivateIpAddress.class, InstancePrivateIpAddressMixins.class);
    addMixInAnnotations(com.amazonaws.services.autoscaling.model.TagDescription.class, TagDescriptionMixins.class);
    addMixInAnnotations(Monitoring.class, MonitoringMixins.class);
    addMixInAnnotations(InstanceState.class, InstanceStateMixins.class);
    addMixInAnnotations(Placement.class, PlacementMixins.class);
    addMixInAnnotations(ProductCode.class, ProductCodeMixins.class);
    addMixInAnnotations(Subnet.class, SubnetMixins.class);
    addMixInAnnotations(LoadBalancerDescription.class, LoadBalancerMixins.class);
  }

  private interface LoadBalancerMixins {
    @JsonProperty("DNSName")
    void setDNSName(String dNSName);

    @JsonProperty("VPCId")
    void setVPCId(String vPCId);
  }

  private interface SubnetMixins {
    @JsonIgnore
    void setState(String state);
  }

  private interface AvailabilityZoneMixins {
    @JsonIgnore
    void setState(String state);
  }

  private interface ImageMixins {
    @JsonProperty("state")
    void setState(String state);

    @JsonProperty("architecture")
    void setArchitecture(String architecture);

    @JsonProperty("imageType")
    void setImageType(String imageType);

    @JsonProperty("platform")
    void setPlatform(String platform);

    @JsonProperty("rootDeviceType")
    void setRootDeviceType(String type);

    @JsonProperty("virtualizationType")
    void setVirtualizationType(String virtualizationType);

    @JsonProperty("hypervisor")
    void setHypervisor(String hypervisor);
  }

  private interface InstanceMixins {
    @JsonIgnore
    Boolean getSourceDestCheck();

    @JsonIgnore
    Boolean getEbsOptimized();

    @JsonProperty("instanceType")
    void setInstanceType(String type);

    @JsonIgnore
    void setPlatform(PlatformValues platform);

    @JsonProperty("architecture")
    void setArchitecture(String architecture);

    @JsonIgnore
    void setRootDeviceType(DeviceType type);

    @JsonIgnore
    void setVirtualizationType(VirtualizationType type);

    @JsonIgnore
    void setInstanceLifecycle(InstanceLifecycleType type);

    @JsonIgnore
    void setHypervisor(HypervisorType type);
  }

  private interface AutoScalingGroupMixins {
    @JsonProperty("VPCZoneIdentifier")
    void setVPCZoneIdentifier(String VPCZoneIdentifier);
  }

  private interface AutoScalingInstanceMixins {
    @JsonProperty("lifecycleState")
    void setLifecycleState(String state);
  }

  private interface InstancePrivateIpAddressMixins {
    @JsonIgnore
    Boolean getPrimary();
  }

  private interface InstanceNetworkInterfaceMixins {
    @JsonIgnore
    Boolean getSourceDestCheck();

    @JsonIgnore
    void setStatus(NetworkInterfaceStatus status);
  }

  private interface InstanceNetworkInterfaceAttachmentMixins {
    @JsonIgnore
    Boolean getDeleteOnTermination();

    @JsonIgnore
    void setStatus(AttachmentStatus status);
  }

  private interface EbsBlockDeviceMixins {
    @JsonIgnore
    Boolean getDeleteOnTermination();

    @JsonProperty("volumeType")
    void setVolumeType(String type);
  }

  private interface EbsInstanceBlockDeviceMixins {
    @JsonIgnore
    Boolean getDeleteOnTermination();

    @JsonIgnore
    void setStatus(AttachmentStatus status);
  }

  private interface VolumeMixins {
    @JsonProperty("state")
    void setState(String state);

    @JsonProperty("volumeType")
    void setVolumeType(String type);
  }

  private interface VolumeAttachmentMixins {
    @JsonIgnore
    Boolean getDeleteOnTermination();

    @JsonIgnore
    void setState(VolumeAttachmentState state);
  }

  private interface TagDescriptionMixins {
    @JsonIgnore
    Boolean getPropagateAtLaunch();
  }

  private interface MonitoringMixins {
    @JsonIgnore
    void setState(MonitoringState state);
  }

  private interface InstanceStateMixins {
    @JsonIgnore
    void setName(InstanceStateName instanceStateName);
  }

  private interface PlacementMixins {
    @JsonIgnore
    void setTenancy(Tenancy tenancy);
  }

  private interface ProductCodeMixins {
    @JsonIgnore
    void setProductCodeType(ProductCodeValues values);
  }
}
