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

package com.netflix.spinnaker.clouddriver.aws.model
import com.netflix.spinnaker.clouddriver.model.InstanceType
import groovy.transform.Canonical

@Canonical
class AmazonInstanceType implements InstanceType {
  String account
  String region
  String name
  Integer defaultVCpus
  Long memoryInGiB
  String hypervisor
  AmazonInstanceStorageInfo instanceStorageInfo
  AmazonInstanceEbsInfo ebsInfo
  AmazonInstanceGpuInfo gpuInfo

  Boolean instanceStorageSupported
  Boolean currentGeneration
  Boolean bareMetal
  Boolean ipv6Supported
  Boolean burstablePerformanceSupported

  List<String> supportedArchitectures
  List<String> supportedUsageClasses
  List<String> supportedRootDeviceTypes
  List<String> supportedVirtualizationTypes
}

class AmazonInstanceStorageInfo {
  String storageTypes
  Long totalSizeInGB
  String nvmeSupport
}

class AmazonInstanceEbsInfo {
  String ebsOptimizedSupport
  String nvmeSupport
  String encryptionSupport
}

class AmazonInstanceGpuInfo {
  Integer totalGpuMemoryInMiB
  List<AmazonInstanceGpuDeviceInfo> gpus
}

class AmazonInstanceGpuDeviceInfo {
  String name
  String manufacturer
  Integer count
  Integer gpuSizeInMiB
}
