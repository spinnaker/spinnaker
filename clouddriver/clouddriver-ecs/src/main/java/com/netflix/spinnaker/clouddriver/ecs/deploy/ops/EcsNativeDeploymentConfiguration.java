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

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeDeploymentSettings;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.model.DeploymentAlarms;
import software.amazon.awssdk.services.ecs.model.DeploymentCircuitBreaker;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHook;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookAction;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookStage;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookTargetType;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookTimeoutConfiguration;

/** Builds native ECS deployment settings consistently for create and update requests. */
final class EcsNativeDeploymentConfiguration {

  /** Stages where ECS allows a PAUSE hook and that are meaningful as a Spinnaker gate. */
  static final Set<String> PAUSE_STAGES =
      Set.of(
          "POST_SCALE_UP",
          "POST_TEST_TRAFFIC_SHIFT",
          "PRE_PRODUCTION_TRAFFIC_SHIFT",
          "POST_PRODUCTION_TRAFFIC_SHIFT");

  private EcsNativeDeploymentConfiguration() {}

  static DeploymentConfiguration forCreate(
      EcsNativeDeploymentSettings settings, DeploymentConfiguration defaults) {
    DeploymentConfiguration.Builder builder =
        defaults == null ? DeploymentConfiguration.builder() : defaults.toBuilder();
    applyCommonSettings(builder, settings);
    builder.deploymentCircuitBreaker(
        DeploymentCircuitBreaker.builder()
            .enable(Boolean.TRUE.equals(settings.getEnableDeploymentCircuitBreaker()))
            .rollback(Boolean.TRUE.equals(settings.getDeploymentCircuitBreakerRollback()))
            .build());
    return builder.build();
  }

  static DeploymentConfiguration forUpdate(
      EcsNativeDeploymentSettings settings, DeploymentConfiguration existingConfiguration) {
    if (!hasConfiguration(settings)) {
      return null;
    }

    DeploymentConfiguration.Builder builder =
        existingConfiguration == null
            ? DeploymentConfiguration.builder()
            : existingConfiguration.toBuilder();
    applyCommonSettings(builder, settings);
    if (settings.getEnableDeploymentCircuitBreaker() != null
        || settings.getDeploymentCircuitBreakerRollback() != null) {
      builder.deploymentCircuitBreaker(
          DeploymentCircuitBreaker.builder()
              .enable(Boolean.TRUE.equals(settings.getEnableDeploymentCircuitBreaker()))
              .rollback(Boolean.TRUE.equals(settings.getDeploymentCircuitBreakerRollback()))
              .build());
    }
    return builder.build();
  }

  static void validateLifecycleSupport(EcsNativeDeploymentSettings settings) {
    validateLifecycleSupport(settings, null);
  }

  /**
   * The strategy a deployment will run with: the requested one, or the service's stored strategy
   * when the request omits it.
   */
  static String effectiveStrategy(
      EcsNativeDeploymentSettings settings, DeploymentConfiguration existingConfiguration) {
    String effectiveStrategy = settings.getDeploymentStrategy();
    if (StringUtils.isBlank(effectiveStrategy) && existingConfiguration != null) {
      effectiveStrategy = existingConfiguration.strategyAsString();
    }
    return effectiveStrategy;
  }

  static void validateLifecycleSupport(
      EcsNativeDeploymentSettings settings, DeploymentConfiguration existingConfiguration) {
    String effectiveStrategy = effectiveStrategy(settings, existingConfiguration);
    if (StringUtils.isNotBlank(effectiveStrategy)
        && !EcsNativeDeploymentSettings.isSupportedStrategy(effectiveStrategy)) {
      throw new IllegalArgumentException(
          "ecs-native deployment strategy must be ROLLING or BLUE_GREEN");
    }
    if (StringUtils.isNotBlank(settings.getLifecyclePauseStage())) {
      if (!StringUtils.equalsIgnoreCase(
          effectiveStrategy, EcsNativeDeploymentSettings.BLUE_GREEN_STRATEGY)) {
        throw new IllegalArgumentException(
            "ecs-native lifecyclePauseStage requires deploymentStrategy BLUE_GREEN");
      }
      if (!PAUSE_STAGES.contains(settings.getLifecyclePauseStage().toUpperCase())) {
        throw new IllegalArgumentException(
            "ecs-native lifecyclePauseStage must be one of " + PAUSE_STAGES);
      }
      if (settings.getLifecyclePauseTimeoutMinutes() == null
          || settings.getLifecyclePauseTimeoutMinutes() < 1) {
        throw new IllegalArgumentException(
            "ecs-native lifecyclePauseTimeoutMinutes must be set (>= 1) when lifecyclePauseStage "
                + "is set");
      }
      String action = settings.getLifecyclePauseTimeoutAction();
      if (StringUtils.isNotBlank(action)
          && !StringUtils.equalsAnyIgnoreCase(action, "ROLLBACK", "CONTINUE")) {
        throw new IllegalArgumentException(
            "ecs-native lifecyclePauseTimeoutAction must be ROLLBACK or CONTINUE");
      }
    }
  }

  static boolean hasConfiguration(EcsNativeDeploymentSettings settings) {
    return settings.getMinimumHealthyPercent() != null
        || settings.getMaximumPercent() != null
        || settings.getEnableDeploymentCircuitBreaker() != null
        || settings.getDeploymentCircuitBreakerRollback() != null
        || hasDeploymentAlarms(settings)
        || StringUtils.isNotBlank(settings.getDeploymentStrategy())
        || settings.getBakeTimeInMinutes() != null
        || StringUtils.isNotBlank(settings.getLifecyclePauseStage());
  }

  private static void applyCommonSettings(
      DeploymentConfiguration.Builder builder, EcsNativeDeploymentSettings settings) {
    if (settings.getMinimumHealthyPercent() != null) {
      builder.minimumHealthyPercent(settings.getMinimumHealthyPercent());
    }
    if (settings.getMaximumPercent() != null) {
      builder.maximumPercent(settings.getMaximumPercent());
    }
    DeploymentAlarms alarms = buildDeploymentAlarms(settings);
    if (alarms != null) {
      builder.alarms(alarms);
    }
    if (StringUtils.isNotBlank(settings.getDeploymentStrategy())) {
      builder.strategy(settings.getDeploymentStrategy());
    }
    if (settings.getBakeTimeInMinutes() != null) {
      builder.bakeTimeInMinutes(settings.getBakeTimeInMinutes());
    }
    if (StringUtils.isNotBlank(settings.getLifecyclePauseStage())) {
      builder.lifecycleHooks(buildPauseHook(settings));
    }
  }

  /**
   * Both the timeout and its action are always set explicitly: ECS defaults for them are not
   * documented, and an unattended pause must fail safe (roll back) rather than promote.
   */
  private static DeploymentLifecycleHook buildPauseHook(EcsNativeDeploymentSettings settings) {
    String action =
        StringUtils.defaultIfBlank(settings.getLifecyclePauseTimeoutAction(), "ROLLBACK")
            .toUpperCase();
    return DeploymentLifecycleHook.builder()
        .targetType(DeploymentLifecycleHookTargetType.PAUSE)
        .lifecycleStages(
            DeploymentLifecycleHookStage.fromValue(settings.getLifecyclePauseStage().toUpperCase()))
        .timeoutConfiguration(
            DeploymentLifecycleHookTimeoutConfiguration.builder()
                .timeoutInMinutes(settings.getLifecyclePauseTimeoutMinutes())
                .action(DeploymentLifecycleHookAction.fromValue(action))
                .build())
        .build();
  }

  private static boolean hasDeploymentAlarms(EcsNativeDeploymentSettings settings) {
    return (settings.getAlarmNames() != null && !settings.getAlarmNames().isEmpty())
        || settings.isEnableDeploymentAlarms();
  }

  private static DeploymentAlarms buildDeploymentAlarms(EcsNativeDeploymentSettings settings) {
    if (!hasDeploymentAlarms(settings)) {
      return null;
    }
    List<String> alarmNames = settings.getAlarmNames();
    return DeploymentAlarms.builder()
        .alarmNames(alarmNames)
        .enable(true)
        .rollback(settings.isDeploymentAlarmsRollback())
        .build();
  }
}
