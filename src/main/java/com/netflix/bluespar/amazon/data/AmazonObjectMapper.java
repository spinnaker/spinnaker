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

package com.netflix.bluespar.amazon.data;

import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class AmazonObjectMapper extends ObjectMapper {
  public AmazonObjectMapper() {
    configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
  }

  private interface SubnetMixins {
    @JsonIgnore
    public abstract void setState(String state);
  }

  private interface AvailabilityZoneMixins {
    @JsonIgnore
    public abstract void setState(String state);
  }

  private interface ImageMixins {
    @JsonProperty("state")
    public abstract void setState(String state);

    @JsonProperty("architecture")
    public abstract void setArchitecture(String architecture);

    @JsonProperty("imageType")
    public abstract void setImageType(String imageType);

    @JsonProperty("platform")
    public abstract void setPlatform(String platform);

    @JsonProperty("rootDeviceType")
    public abstract void setRootDeviceType(String type);

    @JsonProperty("virtualizationType")
    public abstract void setVirtualizationType(String virtualizationType);

    @JsonProperty("hypervisor")
    public abstract void setHypervisor(String hypervisor);
  }

  private interface InstanceMixins {
    @JsonIgnore
    public abstract Boolean getSourceDestCheck();

    @JsonIgnore
    public abstract Boolean getEbsOptimized();

    @JsonIgnore
    public abstract void setInstanceType(String type);

    @JsonIgnore
    public abstract void setPlatform(PlatformValues platform);

    @JsonProperty("architecture")
    public abstract void setArchitecture(String architecture);

    @JsonIgnore
    public abstract void setRootDeviceType(DeviceType type);

    @JsonIgnore
    public abstract void setVirtualizationType(VirtualizationType type);

    @JsonIgnore
    public abstract void setInstanceLifecycle(InstanceLifecycleType type);

    @JsonIgnore
    public abstract void setHypervisor(HypervisorType type);
  }

  private interface AutoScalingInstanceMixins {
    @JsonProperty("lifecycleState")
    public abstract void setLifecycleState(String state);
  }

  private interface InstancePrivateIpAddressMixins {
    @JsonIgnore
    public abstract Boolean getPrimary();
  }

  private interface InstanceNetworkInterfaceMixins {
    @JsonIgnore
    public abstract Boolean getSourceDestCheck();

    @JsonIgnore
    public abstract void setStatus(NetworkInterfaceStatus status);
  }

  private interface InstanceNetworkInterfaceAttachmentMixins {
    @JsonIgnore
    public abstract Boolean getDeleteOnTermination();

    @JsonIgnore
    public abstract void setStatus(AttachmentStatus status);
  }

  private interface EbsBlockDeviceMixins {
    @JsonIgnore
    public abstract Boolean getDeleteOnTermination();

    @JsonProperty("volumeType")
    public abstract void setVolumeType(String type);
  }

  private interface EbsInstanceBlockDeviceMixins {
    @JsonIgnore
    public abstract Boolean getDeleteOnTermination();

    @JsonIgnore
    public abstract void setStatus(AttachmentStatus status);
  }

  private interface VolumeMixins {
    @JsonProperty("state")
    public abstract void setState(String state);

    @JsonProperty("volumeType")
    public abstract void setVolumeType(String type);
  }

  private interface VolumeAttachmentMixins {
    @JsonIgnore
    public abstract Boolean getDeleteOnTermination();

    @JsonIgnore
    public abstract void setState(VolumeAttachmentState state);
  }

  private interface TagDescriptionMixins {
    @JsonIgnore
    public abstract Boolean getPropagateAtLaunch();
  }

  private interface MonitoringMixins {
    @JsonIgnore
    public abstract void setState(MonitoringState state);
  }

  private interface InstanceStateMixins {
    @JsonIgnore
    public abstract void setName(InstanceStateName instanceStateName);
  }

  private interface PlacementMixins {
    @JsonIgnore
    public abstract void setTenancy(Tenancy tenancy);
  }

  private interface ProductCodeMixins {
    @JsonIgnore
    public abstract void setProductCodeType(ProductCodeValues values);
  }
}
