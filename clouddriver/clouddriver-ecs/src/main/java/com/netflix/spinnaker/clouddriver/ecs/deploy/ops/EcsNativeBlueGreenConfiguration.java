/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeDeploymentSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.model.AdvancedConfiguration;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;

/** Validates and builds the ALB traffic-shift settings for native blue/green deployments. */
final class EcsNativeBlueGreenConfiguration {

  private EcsNativeBlueGreenConfiguration() {}

  static void validateLoadBalancerConfig(
      EcsNativeCreateServerGroupDescription description, List<LoadBalancer> loadBalancers) {
    boolean isBlueGreen =
        EcsNativeDeploymentSettings.isBlueGreen(description.getDeploymentStrategy());
    boolean hasLoadBalancer = loadBalancers != null && !loadBalancers.isEmpty();
    if (isBlueGreen && hasLoadBalancer && !description.hasAllTrafficShiftFields()) {
      throw missingTrafficShiftConfiguration();
    }
  }

  /**
   * Rejects every blue/green traffic-shift misconfiguration that can be decided from the
   * description alone, so the operation can fail before it registers a task-definition revision or
   * calls AWS at all. The request-building helpers below re-check the same conditions on the
   * materialized request as a last line of defence; this method exists so that those later checks
   * never fire for a deterministic description error.
   */
  static void validateDescription(EcsNativeCreateServerGroupDescription description) {
    if (EcsNativeDeploymentSettings.isBlueGreen(description.getDeploymentStrategy())
        && description.declaresLoadBalancer()
        && !description.hasAllTrafficShiftFields()) {
      throw missingTrafficShiftConfiguration();
    }
    if (description.hasAnyTrafficShiftField() && !description.hasAllTrafficShiftFields()) {
      throw partialTrafficShiftConfiguration();
    }
    if (description.hasAllTrafficShiftFields()) {
      int declaredMappings = declaredTargetGroupMappingCount(description);
      if (declaredMappings != 1) {
        throw singleTargetGroupMappingRequired(declaredMappings);
      }
    }
  }

  static AdvancedConfiguration buildAdvancedConfiguration(
      EcsNativeCreateServerGroupDescription description) {
    if (!description.hasAnyTrafficShiftField()) {
      return null;
    }
    if (!description.hasAllTrafficShiftFields()) {
      throw partialTrafficShiftConfiguration();
    }
    return AdvancedConfiguration.builder()
        .alternateTargetGroupArn(description.getAlternateTargetGroupArn())
        .productionListenerRule(description.getProductionListenerRule())
        .testListenerRule(description.getTestListenerRule())
        .roleArn(description.getBlueGreenRoleArn())
        .build();
  }

  static List<LoadBalancer> withAdvancedConfiguration(
      List<LoadBalancer> loadBalancers, AdvancedConfiguration advancedConfiguration) {
    if (loadBalancers == null || loadBalancers.size() != 1) {
      throw singleTargetGroupMappingRequired(loadBalancers == null ? 0 : loadBalancers.size());
    }
    List<LoadBalancer> updated = new ArrayList<>(loadBalancers.size());
    updated.add(
        loadBalancers.get(0).toBuilder().advancedConfiguration(advancedConfiguration).build());
    return updated;
  }

  /**
   * Carries an existing service's blue/green ALB traffic-shift config onto requested load balancers
   * that omit it. {@code UpdateService} replaces the service's load balancers wholesale, so
   * resending them without {@code advancedConfiguration} would drop the alternate target group,
   * listener rules and role that a {@code BLUE_GREEN} service depends on.
   *
   * <p>A requested load balancer inherits the config of the existing one with the same target
   * group, container name and container port. Requested entries that already carry their own
   * config, or that match no existing entry, are sent unchanged (ECS then validates them).
   */
  static List<LoadBalancer> preserveAdvancedConfiguration(
      List<LoadBalancer> requested, List<LoadBalancer> existing) {
    if (requested == null || existing == null || existing.isEmpty()) {
      return requested;
    }
    List<LoadBalancer> preserved = new ArrayList<>(requested.size());
    for (LoadBalancer loadBalancer : requested) {
      AdvancedConfiguration existingConfiguration =
          loadBalancer.advancedConfiguration() != null
              ? null
              : existing.stream()
                  .filter(
                      candidate ->
                          candidate.advancedConfiguration() != null
                              && sameTarget(loadBalancer, candidate))
                  .map(LoadBalancer::advancedConfiguration)
                  .findFirst()
                  .orElse(null);
      preserved.add(
          existingConfiguration == null
              ? loadBalancer
              : loadBalancer.toBuilder().advancedConfiguration(existingConfiguration).build());
    }
    return preserved;
  }

  private static boolean sameTarget(LoadBalancer a, LoadBalancer b) {
    return Objects.equals(a.targetGroupArn(), b.targetGroupArn())
        && Objects.equals(a.containerName(), b.containerName())
        && Objects.equals(a.containerPort(), b.containerPort());
  }

  /**
   * Mirrors how {@code CreateServerGroupAtomicOperation.retrieveLoadBalancers} materializes the
   * description into ECS load balancers: one per explicit target-group mapping plus one for the
   * legacy single {@code targetGroup}.
   */
  private static int declaredTargetGroupMappingCount(
      EcsNativeCreateServerGroupDescription description) {
    int count =
        description.getTargetGroupMappings() == null
            ? 0
            : description.getTargetGroupMappings().size();
    if (StringUtils.isNotBlank(description.getTargetGroup())) {
      count++;
    }
    return count;
  }

  private static IllegalArgumentException partialTrafficShiftConfiguration() {
    return new IllegalArgumentException(
        "alternateTargetGroupArn, productionListenerRule, testListenerRule and"
            + " blueGreenRoleArn must all be set together to configure a blue/green ALB"
            + " traffic shift, or all left unset to skip it.");
  }

  private static IllegalArgumentException singleTargetGroupMappingRequired(int found) {
    return new IllegalArgumentException(
        "A blue/green ALB traffic shift requires exactly one target-group mapping; found "
            + found
            + ".");
  }

  private static IllegalArgumentException missingTrafficShiftConfiguration() {
    return new IllegalArgumentException(
        "The Blue/Green deployment strategy on a load-balanced ECS service requires the ALB"
            + " traffic-shift config: alternateTargetGroupArn, productionListenerRule,"
            + " testListenerRule and blueGreenRoleArn must all be set. AWS ECS rejects a"
            + " Blue/Green deploy whose load balancers have no advancedConfiguration. Provide all"
            + " four ARNs, or use the Rolling strategy for an in-place, single-target-group"
            + " deploy.");
  }
}
