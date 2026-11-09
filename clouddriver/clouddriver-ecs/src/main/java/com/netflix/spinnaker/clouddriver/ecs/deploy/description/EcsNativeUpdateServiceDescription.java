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

package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.PlacementConstraint;
import software.amazon.awssdk.services.ecs.model.PlacementStrategy;
import software.amazon.awssdk.services.ecs.model.ServiceRegistry;

/**
 * Description for an in-place update of an existing ECS service under the opt-in {@code ecs-native}
 * provider.
 *
 * <p>Unlike the original provider, which creates a new versioned service per deploy, this drives a
 * native ECS {@code UpdateService} against the durable service named by {@link
 * #getServerGroupName()}. Service-shape fields are optional because rollback operations normally
 * change only the task definition; when present, they are forwarded to {@code UpdateService}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcsNativeUpdateServiceDescription extends ModifyServiceDescription
    implements EcsNativeDeploymentSettings {

  /** ECS cluster the service runs in. Resolved from the service name when omitted. */
  @Nullable String ecsClusterName;

  /** Task definition (ARN or {@code family:revision}) to roll the service to. Optional. */
  @Nullable String taskDefinition;

  /** Desired task count. Native updates treat capacity fields as authoritative when supplied. */
  @Nullable Integer desiredCount;

  /** Full awsvpc network configuration to apply to the service. */
  @Nullable NetworkConfiguration networkConfiguration;

  /** Service discovery registrations to apply to the service. */
  @Nullable List<ServiceRegistry> serviceRegistries;

  /** ECS placement constraints to apply to the service. */
  @Nullable List<PlacementConstraint> placementConstraints;

  /** ECS placement strategy to apply to the service. */
  @Nullable List<PlacementStrategy> placementStrategy;

  /** Capacity provider strategy to apply to the service. */
  @Nullable List<CapacityProviderStrategyItem> capacityProviderStrategy;

  /** Fargate platform version to apply to the service. */
  @Nullable String platformVersion;

  /** Health-check grace period to apply to the service. */
  @Nullable Integer healthCheckGracePeriodSeconds;

  /** Whether ECS Exec is enabled for the service. */
  @Nullable Boolean enableExecuteCommand;

  /** Load balancer mappings to apply to the service. */
  @Nullable List<LoadBalancer> loadBalancers;

  /** Lower bound (percent) of healthy tasks ECS keeps running during the deployment. */
  @Nullable Integer minimumHealthyPercent;

  /** Upper bound (percent) of tasks ECS may run during the deployment. */
  @Nullable Integer maximumPercent;

  /** Enable the ECS deployment circuit breaker for this deployment. */
  @Nullable Boolean enableDeploymentCircuitBreaker;

  /** When the circuit breaker is enabled, automatically roll back a failed deployment. */
  @Nullable Boolean deploymentCircuitBreakerRollback;

  /** Force a new deployment even when the task definition is unchanged. */
  boolean forceNewDeployment;

  @Override
  public Boolean getEnableDeploymentCircuitBreaker() {
    return enableDeploymentCircuitBreaker;
  }

  @Override
  public Boolean getDeploymentCircuitBreakerRollback() {
    return deploymentCircuitBreakerRollback;
  }

  /**
   * Names of CloudWatch alarms ECS should watch during this deployment. See the equivalent field on
   * {@code EcsNativeCreateServerGroupDescription} for the full explanation.
   */
  @Nullable List<String> alarmNames;

  /** Enables deployment alarms for this update. Implied when {@link #getAlarmNames()} is set. */
  boolean enableDeploymentAlarms;

  /** When {@code true}, a deployment that trips a named alarm is automatically rolled back. */
  boolean deploymentAlarmsRollback;

  /** Native ECS deployment strategy: {@code ROLLING} or {@code BLUE_GREEN}. */
  @Nullable String deploymentStrategy;

  /** Minutes ECS soaks the new revision before retiring the previous one (ROLLING/BLUE_GREEN). */
  @Nullable Integer bakeTimeInMinutes;
}
