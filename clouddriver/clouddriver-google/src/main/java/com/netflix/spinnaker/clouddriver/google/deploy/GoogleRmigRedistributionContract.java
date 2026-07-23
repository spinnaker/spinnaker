/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy;

import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerUpdatePolicy;
import org.apache.commons.lang3.StringUtils;

/**
 * Enforces Spinnaker's regional MIG redistribution contract before sending manager requests.
 *
 * <p>GCP requires {@code instanceRedistributionType=NONE} for {@code BALANCED} and {@code
 * ANY_SINGLE_ZONE}; {@code ANY} itself does not proactively rebalance. GCP's instance-flexibility
 * creation examples also use {@code NONE}. Spinnaker applies {@code NONE} uniformly whenever
 * instance flexibility or any non-{@code EVEN} target shape is present.
 *
 * @see <a
 *     href="https://cloud.google.com/compute/docs/instance-groups/regional-mig-set-target-distribution-shape">
 *     Set the target distribution shape (GCP docs)</a>
 * @see <a
 *     href="https://cloud.google.com/compute/docs/instance-groups/configure-instance-flexibility">
 *     Configure instance flexibility (GCP docs)</a>
 */
public final class GoogleRmigRedistributionContract {

  private static final String EVEN_TARGET_SHAPE = "EVEN";
  private static final String DISABLED_REDISTRIBUTION = "NONE";

  public static void enforce(InstanceGroupManager instanceGroupManager) {
    boolean hasFlexPolicy = instanceGroupManager.getInstanceFlexibilityPolicy() != null;
    String targetShape =
        instanceGroupManager.getDistributionPolicy() == null
            ? null
            : instanceGroupManager.getDistributionPolicy().getTargetShape();
    boolean hasNonEvenTargetShape =
        StringUtils.isNotBlank(targetShape)
            && !EVEN_TARGET_SHAPE.equalsIgnoreCase(targetShape.trim());

    if (!hasFlexPolicy && !hasNonEvenTargetShape) {
      return;
    }

    InstanceGroupManagerUpdatePolicy updatePolicy =
        instanceGroupManager.getUpdatePolicy() != null
            ? instanceGroupManager.getUpdatePolicy()
            : new InstanceGroupManagerUpdatePolicy();
    updatePolicy.setInstanceRedistributionType(DISABLED_REDISTRIBUTION);
    instanceGroupManager.setUpdatePolicy(updatePolicy);
  }

  private GoogleRmigRedistributionContract() {}
}
