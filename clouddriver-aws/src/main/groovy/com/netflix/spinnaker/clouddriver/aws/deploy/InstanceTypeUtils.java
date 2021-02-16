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

package com.netflix.spinnaker.clouddriver.aws.deploy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Utility class for AWS EC2 instance types. */
public class InstanceTypeUtils {

  // https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/virtualization_types.html
  private static final String PARAVIRTUAL = "paravirtual";
  private static final Set<String> PARAVIRTUAL_FAMILIES =
      ImmutableSet.of("c1", "c3", "hi1", "hs1", "m1", "m2", "m3", "t1");

  // http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EBSOptimized.html
  private static final Set<String> DEFAULT_EBS_OPTIMIZED_FAMILIES =
      ImmutableSet.of(
          "a1", "c4", "c5", "d2", "f1", "g3", "i3", "m4", "m5", "p2", "p3", "r4", "r5", "x1", "t3");

  // https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances.html
  private static final Set<String> BURSTABLE_PERFORMANCE_FAMILIES =
      ImmutableSet.of("t2", "t3", "t3a", "t4g");

  /**
   * Validate compatibility of instance type and AMI.
   *
   * <p>AWS supports two types of virtualization: paravirtual (PV) and hardware virtual machine
   * (HVM). All current generation instance types support HVM AMIs only. The previous generation
   * instance families that support PV are listed in {@link #PARAVIRTUAL_FAMILIES}.
   *
   * @param virtualizationType from the AMI
   * @param instanceType
   */
  public static void validateCompatibility(String virtualizationType, String instanceType) {
    final String family = getInstanceFamily(instanceType);

    // if virtualizationType is PV, check for compatiblity. Otherwise, assume compatiblity is true.
    if (PARAVIRTUAL.equals(virtualizationType) && !PARAVIRTUAL_FAMILIES.contains(family)) {
      throw new IllegalArgumentException(
          "Instance type "
              + instanceType
              + " does not support "
              + "virtualization type "
              + virtualizationType
              + ". Please select a different image or instance type.");
    }
  }

  public static boolean getDefaultEbsOptimizedFlag(String instanceType) {
    return DEFAULT_EBS_OPTIMIZED_FAMILIES.contains(getInstanceFamily(instanceType));
  }

  public static boolean isBurstingSupported(String instanceType) {
    return BURSTABLE_PERFORMANCE_FAMILIES.contains(getInstanceFamily(instanceType));
  }

  private static String getInstanceFamily(String instanceType) {
    if (instanceType != null && instanceType.contains(".")) {
      return instanceType.split("\\.")[0];
    }

    return "";
  }

  /** Class to handle AWS EC2 block device configuration. */
  public static class BlockDeviceConfig {

    private final DeployDefaults deployDefaults;
    private final Map<String, List<AmazonBlockDevice>> blockDevicesByInstanceType;

    public BlockDeviceConfig(DeployDefaults deployDefaults) {
      this.deployDefaults = deployDefaults;
      this.blockDevicesByInstanceType =
          ImmutableMap.<String, List<AmazonBlockDevice>>builder()
              .put("a1.medium", sizedBlockDevicesForEbs(40))
              .put("a1.large", sizedBlockDevicesForEbs(40))
              .put("a1.xlarge", sizedBlockDevicesForEbs(80))
              .put("a1.2xlarge", sizedBlockDevicesForEbs(80))
              .put("a1.4xlarge", sizedBlockDevicesForEbs(120))
              .put("a1.metal", sizedBlockDevicesForEbs(120))
              .put("c1.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("c1.xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("c3.large", enumeratedBlockDevicesWithVirtualName(2))
              .put("c3.xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c3.2xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c3.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c3.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c4.large", sizedBlockDevicesForEbs(40))
              .put("c4.xlarge", sizedBlockDevicesForEbs(80))
              .put("c4.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c4.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c4.8xlarge", sizedBlockDevicesForEbs(120))
              .put("c5.large", sizedBlockDevicesForEbs(40))
              .put("c5.xlarge", sizedBlockDevicesForEbs(80))
              .put("c5.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c5.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c5.9xlarge", sizedBlockDevicesForEbs(120))
              .put("c5.12xlarge", sizedBlockDevicesForEbs(120))
              .put("c5.18xlarge", sizedBlockDevicesForEbs(120))
              .put("c5.24xlarge", sizedBlockDevicesForEbs(120))
              .put("c5d.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5d.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5d.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5d.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5d.9xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5d.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5d.18xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5d.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("c5d.metal", enumeratedBlockDevicesWithVirtualName(4))
              .put("c5n.large", sizedBlockDevicesForEbs(40))
              .put("c5n.xlarge", sizedBlockDevicesForEbs(80))
              .put("c5n.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c5n.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c5n.9xlarge", sizedBlockDevicesForEbs(120))
              .put("c5n.18xlarge", sizedBlockDevicesForEbs(120))
              .put("c5n.metal", sizedBlockDevicesForEbs(120))
              .put("c5a.large", sizedBlockDevicesForEbs(40))
              .put("c5a.xlarge", sizedBlockDevicesForEbs(80))
              .put("c5a.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c5a.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c5a.8xlarge", sizedBlockDevicesForEbs(120))
              .put("c5a.12xlarge", sizedBlockDevicesForEbs(120))
              .put("c5a.16xlarge", sizedBlockDevicesForEbs(120))
              .put("c5a.24xlarge", sizedBlockDevicesForEbs(120))
              .put("c5ad.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5ad.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5ad.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c5ad.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5ad.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5ad.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5ad.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c5ad.24xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c6g.medium", sizedBlockDevicesForEbs(40))
              .put("c6g.large", sizedBlockDevicesForEbs(40))
              .put("c6g.xlarge", sizedBlockDevicesForEbs(80))
              .put("c6g.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c6g.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c6g.8xlarge", sizedBlockDevicesForEbs(120))
              .put("c6g.12xlarge", sizedBlockDevicesForEbs(120))
              .put("c6g.16xlarge", sizedBlockDevicesForEbs(120))
              .put("c6g.metal", sizedBlockDevicesForEbs(120))
              .put("c6gd.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("c6gd.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c6gd.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("c6gd.metal", enumeratedBlockDevicesWithVirtualName(2))
              .put("c6gn.medium", sizedBlockDevicesForEbs(40))
              .put("c6gn.large", sizedBlockDevicesForEbs(40))
              .put("c6gn.xlarge", sizedBlockDevicesForEbs(80))
              .put("c6gn.2xlarge", sizedBlockDevicesForEbs(80))
              .put("c6gn.4xlarge", sizedBlockDevicesForEbs(120))
              .put("c6gn.8xlarge", sizedBlockDevicesForEbs(120))
              .put("c6gn.12xlarge", sizedBlockDevicesForEbs(120))
              .put("c6gn.16xlarge", sizedBlockDevicesForEbs(120))
              .put("cc2.8xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("cg1.4xlarge", sizedBlockDevicesForEbs(120))
              .put("cr1.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("d2.xlarge", enumeratedBlockDevicesWithVirtualName(3))
              .put("d2.2xlarge", enumeratedBlockDevicesWithVirtualName(6))
              .put("d2.4xlarge", enumeratedBlockDevicesWithVirtualName(12))
              .put("d2.8xlarge", enumeratedBlockDevicesWithVirtualName(24))
              .put("d3.xlarge", enumeratedBlockDevicesWithVirtualName(3))
              .put("d3.2xlarge", enumeratedBlockDevicesWithVirtualName(6))
              .put("d3.4xlarge", enumeratedBlockDevicesWithVirtualName(12))
              .put("d3.8xlarge", enumeratedBlockDevicesWithVirtualName(24))
              .put("d3en.xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("d3en.2xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("d3en.4xlarge", enumeratedBlockDevicesWithVirtualName(8))
              .put("d3en.6xlarge", enumeratedBlockDevicesWithVirtualName(12))
              .put("d3en.8xlarge", enumeratedBlockDevicesWithVirtualName(16))
              .put("d3en.12xlarge", enumeratedBlockDevicesWithVirtualName(24))
              .put("f1.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("f1.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("f1.16xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("g2.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g2.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("g3s.xlarge", sizedBlockDevicesForEbs(80))
              .put("g3.4xlarge", sizedBlockDevicesForEbs(120))
              .put("g3.8xlarge", sizedBlockDevicesForEbs(120))
              .put("g3.16xlarge", sizedBlockDevicesForEbs(120))
              .put("g4ad.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4ad.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4ad.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("g4dn.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.12xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.16xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("g4dn.metal", enumeratedBlockDevicesWithVirtualName(2))
              .put("h1.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("h1.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("h1.8xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("h1.16xlarge", enumeratedBlockDevicesWithVirtualName(8))
              .put("hs1.8xlarge", enumeratedBlockDevicesWithVirtualName(24))
              .put("i2.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("i2.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("i2.2xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("i2.4xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("i2.8xlarge", enumeratedBlockDevicesWithVirtualName(8))
              .put("i3.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("i3.8xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("i3.16xlarge", enumeratedBlockDevicesWithVirtualName(8))
              .put("i3.metal", enumeratedBlockDevicesWithVirtualName(8))
              .put("i3en.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3en.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3en.2xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("i3en.3xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("i3en.6xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("i3en.12xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("i3en.24xlarge", enumeratedBlockDevicesWithVirtualName(8))
              .put("inf1.xlarge", sizedBlockDevicesForEbs(80))
              .put("inf1.2xlarge", sizedBlockDevicesForEbs(80))
              .put("inf1.6xlarge", sizedBlockDevicesForEbs(120))
              .put("inf1.24xlarge", sizedBlockDevicesForEbs(120))
              .put("m1.small", enumeratedBlockDevicesWithVirtualName(1))
              .put("m1.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("m1.large", enumeratedBlockDevicesWithVirtualName(2))
              .put("m1.xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m2.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m2.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m2.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m3.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("m3.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("m3.xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m3.2xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m4.large", sizedBlockDevicesForEbs(40))
              .put("m4.xlarge", sizedBlockDevicesForEbs(80))
              .put("m4.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m4.4xlarge", sizedBlockDevicesForEbs(120))
              .put("m4.10xlarge", sizedBlockDevicesForEbs(120))
              .put("m4.16xlarge", sizedBlockDevicesForEbs(120))
              .put("m5.large", sizedBlockDevicesForEbs(40))
              .put("m5.xlarge", sizedBlockDevicesForEbs(80))
              .put("m5.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m5.4xlarge", sizedBlockDevicesForEbs(120))
              .put("m5.8xlarge", sizedBlockDevicesForEbs(120))
              .put("m5.12xlarge", sizedBlockDevicesForEbs(120))
              .put("m5.16xlarge", sizedBlockDevicesForEbs(120))
              .put("m5.24xlarge", sizedBlockDevicesForEbs(120))
              .put("m5d.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5d.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5d.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5d.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5d.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5d.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5d.16xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m5d.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m5n.large", sizedBlockDevicesForEbs(40))
              .put("m5n.xlarge", sizedBlockDevicesForEbs(80))
              .put("m5n.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m5n.4xlarge", sizedBlockDevicesForEbs(120))
              .put("m5n.8xlarge", sizedBlockDevicesForEbs(120))
              .put("m5n.12xlarge", sizedBlockDevicesForEbs(120))
              .put("m5n.16xlarge", sizedBlockDevicesForEbs(120))
              .put("m5n.24xlarge", sizedBlockDevicesForEbs(120))
              .put("m5dn.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5dn.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5dn.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5dn.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5dn.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5dn.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5dn.16xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m5dn.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m5a.large", sizedBlockDevicesForEbs(40))
              .put("m5a.xlarge", sizedBlockDevicesForEbs(80))
              .put("m5a.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m5a.4xlarge", sizedBlockDevicesForEbs(120))
              .put("m5a.8xlarge", sizedBlockDevicesForEbs(120))
              .put("m5a.12xlarge", sizedBlockDevicesForEbs(120))
              .put("m5a.16xlarge", sizedBlockDevicesForEbs(120))
              .put("m5a.24xlarge", sizedBlockDevicesForEbs(120))
              .put("m5ad.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5ad.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5ad.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m5ad.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5ad.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5ad.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5ad.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m5ad.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("m5zn.large", sizedBlockDevicesForEbs(40))
              .put("m5zn.xlarge", sizedBlockDevicesForEbs(80))
              .put("m5zn.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m5zn.3xlarge", sizedBlockDevicesForEbs(120))
              .put("m5zn.6xlarge", sizedBlockDevicesForEbs(120))
              .put("m5zn.12xlarge", sizedBlockDevicesForEbs(120))
              .put("m6g.medium", sizedBlockDevicesForEbs(40))
              .put("m6g.large", sizedBlockDevicesForEbs(40))
              .put("m6g.xlarge", sizedBlockDevicesForEbs(80))
              .put("m6g.2xlarge", sizedBlockDevicesForEbs(80))
              .put("m6g.4xlarge", sizedBlockDevicesForEbs(120))
              .put("m6g.8xlarge", sizedBlockDevicesForEbs(120))
              .put("m6g.12xlarge", sizedBlockDevicesForEbs(120))
              .put("m6g.16xlarge", sizedBlockDevicesForEbs(120))
              .put("m6g.metal", sizedBlockDevicesForEbs(120))
              .put("m6gd.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("m6gd.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m6gd.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("m6gd.metal", enumeratedBlockDevicesWithVirtualName(2))
              .put("r3.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("r3.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r3.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r3.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r3.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r4.large", sizedBlockDevicesForEbs(40))
              .put("r4.xlarge", sizedBlockDevicesForEbs(80))
              .put("r4.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r4.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r4.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r4.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r5.large", sizedBlockDevicesForEbs(40))
              .put("r5.xlarge", sizedBlockDevicesForEbs(80))
              .put("r5.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r5.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r5.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r5.12xlarge", sizedBlockDevicesForEbs(120))
              .put("r5.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r5.24xlarge", sizedBlockDevicesForEbs(120))
              .put("r5d.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5d.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5d.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5d.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5d.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5d.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5d.16xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("r5d.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("r5n.large", sizedBlockDevicesForEbs(40))
              .put("r5n.xlarge", sizedBlockDevicesForEbs(80))
              .put("r5n.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r5n.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r5n.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r5n.12xlarge", sizedBlockDevicesForEbs(120))
              .put("r5n.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r5n.24xlarge", sizedBlockDevicesForEbs(120))
              .put("r5dn.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5dn.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5dn.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5dn.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5dn.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5dn.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5dn.16xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("r5dn.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("r5a.large", sizedBlockDevicesForEbs(40))
              .put("r5a.xlarge", sizedBlockDevicesForEbs(80))
              .put("r5a.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r5a.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r5a.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r5a.12xlarge", sizedBlockDevicesForEbs(120))
              .put("r5a.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r5a.24xlarge", sizedBlockDevicesForEbs(120))
              .put("r5ad.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5ad.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5ad.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r5ad.4xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5ad.8xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5ad.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5ad.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r5ad.24xlarge", enumeratedBlockDevicesWithVirtualName(4))
              .put("r5b.large", sizedBlockDevicesForEbs(40))
              .put("r5b.xlarge", sizedBlockDevicesForEbs(80))
              .put("r5b.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r5b.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r5b.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r5b.12xlarge", sizedBlockDevicesForEbs(120))
              .put("r5b.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r5b.24xlarge", sizedBlockDevicesForEbs(120))
              .put("r6g.medium", sizedBlockDevicesForEbs(40))
              .put("r6g.large", sizedBlockDevicesForEbs(40))
              .put("r6g.xlarge", sizedBlockDevicesForEbs(80))
              .put("r6g.2xlarge", sizedBlockDevicesForEbs(80))
              .put("r6g.4xlarge", sizedBlockDevicesForEbs(120))
              .put("r6g.8xlarge", sizedBlockDevicesForEbs(120))
              .put("r6g.12xlarge", sizedBlockDevicesForEbs(120))
              .put("r6g.16xlarge", sizedBlockDevicesForEbs(120))
              .put("r6g.metal", sizedBlockDevicesForEbs(120))
              .put("r6gd.medium", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("r6gd.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r6gd.16xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("r6gd.metal", enumeratedBlockDevicesWithVirtualName(2))
              .put("p2.xlarge", sizedBlockDevicesForEbs(80))
              .put("p2.8xlarge", sizedBlockDevicesForEbs(120))
              .put("p2.16xlarge", sizedBlockDevicesForEbs(120))
              .put("p3.2xlarge", sizedBlockDevicesForEbs(80))
              .put("p3.8xlarge", sizedBlockDevicesForEbs(120))
              .put("p3.16xlarge", sizedBlockDevicesForEbs(120))
              .put("p3dn.24xlarge", sizedBlockDevicesForEbs(120))
              .put("p4d.24xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("t1.micro", Collections.emptyList())
              .put("t2.nano", Collections.emptyList())
              .put("t2.micro", Collections.emptyList())
              .put("t2.small", Collections.emptyList())
              .put("t2.medium", Collections.emptyList())
              .put("t2.large", Collections.emptyList())
              .put("t2.xlarge", Collections.emptyList())
              .put("t2.2xlarge", Collections.emptyList())
              .put("t3.nano", Collections.emptyList())
              .put("t3.micro", Collections.emptyList())
              .put("t3.small", Collections.emptyList())
              .put("t3.medium", Collections.emptyList())
              .put("t3.large", Collections.emptyList())
              .put("t3.xlarge", Collections.emptyList())
              .put("t3.2xlarge", Collections.emptyList())
              .put("t3a.nano", Collections.emptyList())
              .put("t3a.micro", Collections.emptyList())
              .put("t3a.small", Collections.emptyList())
              .put("t3a.medium", Collections.emptyList())
              .put("t3a.large", Collections.emptyList())
              .put("t3a.xlarge", Collections.emptyList())
              .put("t3a.2xlarge", Collections.emptyList())
              .put("t4g.nano", Collections.emptyList())
              .put("t4g.micro", Collections.emptyList())
              .put("t4g.small", Collections.emptyList())
              .put("t4g.medium", Collections.emptyList())
              .put("t4g.large", Collections.emptyList())
              .put("t4g.xlarge", Collections.emptyList())
              .put("t4g.2xlarge", Collections.emptyList())
              .put("x1.16xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1.32xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("x1e.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1e.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1e.4xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1e.8xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1e.16xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("x1e.32xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .put("z1d.large", enumeratedBlockDevicesWithVirtualName(1))
              .put("z1d.xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("z1d.2xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("z1d.3xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("z1d.6xlarge", enumeratedBlockDevicesWithVirtualName(1))
              .put("z1d.12xlarge", enumeratedBlockDevicesWithVirtualName(2))
              .build();
    }

    public List<AmazonBlockDevice> getBlockDevicesForInstanceType(String instanceType) {
      final List<AmazonBlockDevice> blockDevices = blockDevicesByInstanceType.get(instanceType);
      if (blockDevices == null && deployDefaults.getUnknownInstanceTypeBlockDevice() != null) {
        // return a default block device mapping if no instance-specific default exists <optional>
        return ImmutableList.of(deployDefaults.getUnknownInstanceTypeBlockDevice());
      }

      return blockDevices;
    }

    public Set<String> getInstanceTypesWithBlockDeviceMappings() {
      return blockDevicesByInstanceType.keySet();
    }

    private List<AmazonBlockDevice> enumeratedBlockDevicesWithVirtualName(int size) {
      char[] letters = "abcdefghijklmnopqrstuvwxyz".toCharArray();
      return IntStream.range(0, size)
          .mapToObj(
              i ->
                  new AmazonBlockDevice.Builder()
                      .deviceName("/dev/sd" + letters[i + 1])
                      .virtualName("ephemeral" + i)
                      .build())
          .collect(ImmutableList.toImmutableList());
    }

    private List<AmazonBlockDevice> defaultBlockDevicesForEbsOnly() {
      return ImmutableList.of(
          new AmazonBlockDevice.Builder()
              .deviceName("/dev/sdb")
              .size(125)
              .volumeType(deployDefaults.getDefaultBlockDeviceType())
              .build(),
          new AmazonBlockDevice.Builder()
              .deviceName("/dev/sdc")
              .size(125)
              .volumeType(deployDefaults.getDefaultBlockDeviceType())
              .build());
    }

    private List<AmazonBlockDevice> sizedBlockDevicesForEbs(int capacity) {
      return ImmutableList.of(
          new AmazonBlockDevice.Builder()
              .deviceName("/dev/sdb")
              .size(capacity)
              .volumeType(deployDefaults.getDefaultBlockDeviceType())
              .build());
    }
  }
}
