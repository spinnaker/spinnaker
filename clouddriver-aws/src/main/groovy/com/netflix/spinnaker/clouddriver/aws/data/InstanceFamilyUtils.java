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

package com.netflix.spinnaker.clouddriver.aws.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;

public class InstanceFamilyUtils {

  private static final Map<String, Set<String>> KNOWN_VIRTUALIZATION_FAMILIES =
      ImmutableMap.of(
          "paravirtual", ImmutableSet.of("c1", "c3", "hi1", "hs1", "m1", "m2", "m3", "t1"),
          "hvm", ImmutableSet.of("c3", "c4", "d2", "i2", "g2", "r3", "m3", "m4", "t2", "t3"));

  // http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EBSOptimized.html
  private static final Set<String> DEFAULT_EBS_OPTIMIZED_FAMILIES =
      ImmutableSet.of("c4", "d2", "f1", "g3", "i3", "m4", "p2", "r4", "x1", "t3");

  private static final Set<String> BURSTABLE_PERFORMANCE_FAMILIES =
      ImmutableSet.of("t2", "t3", "t3a");

  private static String getInstanceFamily(String instanceType) {
    if (instanceType != null && instanceType.contains(".")) {
      return instanceType.split("\\.")[0];
    }

    return "";
  }

  public static boolean isAmiAndFamilyCompatible(String virtualizationType, String instanceType) {
    // check the compatibility of instance type and AMI only if the instance family is known.
    final String family = getInstanceFamily(instanceType);
    boolean isFamilyKnown =
        KNOWN_VIRTUALIZATION_FAMILIES.containsKey(virtualizationType)
            && KNOWN_VIRTUALIZATION_FAMILIES.values().stream().anyMatch(f -> f.contains(family));

    if (isFamilyKnown && !KNOWN_VIRTUALIZATION_FAMILIES.get(virtualizationType).contains(family)) {
      throw new IllegalArgumentException(
          "Instance type "
              + instanceType
              + " does not support "
              + "virtualization type "
              + virtualizationType
              + ". Please select a different image or instance type.");
    }

    return true;
  }

  public static boolean getDefaultEbsOptimizedFlag(String instanceType) {
    return DEFAULT_EBS_OPTIMIZED_FAMILIES.contains(getInstanceFamily(instanceType));
  }

  public static boolean isBurstingSupported(String instanceType) {
    return BURSTABLE_PERFORMANCE_FAMILIES.contains(getInstanceFamily(instanceType));
  }
}
