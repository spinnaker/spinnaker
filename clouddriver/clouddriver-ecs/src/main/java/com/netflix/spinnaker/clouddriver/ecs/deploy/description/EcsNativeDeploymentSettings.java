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
import org.apache.commons.lang3.StringUtils;

/** Common native ECS deployment settings shared by create and update descriptions. */
public interface EcsNativeDeploymentSettings {
  String ROLLING_STRATEGY = "ROLLING";
  String BLUE_GREEN_STRATEGY = "BLUE_GREEN";

  /** Whether {@code strategy} is a native ECS strategy the ecs-native provider supports. */
  static boolean isSupportedStrategy(@Nullable String strategy) {
    return StringUtils.equalsAnyIgnoreCase(strategy, ROLLING_STRATEGY, BLUE_GREEN_STRATEGY);
  }

  /** Whether {@code strategy} is the native ECS blue/green strategy. */
  static boolean isBlueGreen(@Nullable String strategy) {
    return BLUE_GREEN_STRATEGY.equalsIgnoreCase(StringUtils.trimToEmpty(strategy));
  }

  @Nullable
  Integer getMinimumHealthyPercent();

  @Nullable
  Integer getMaximumPercent();

  @Nullable
  Boolean getEnableDeploymentCircuitBreaker();

  @Nullable
  Boolean getDeploymentCircuitBreakerRollback();

  @Nullable
  List<String> getAlarmNames();

  boolean isEnableDeploymentAlarms();

  boolean isDeploymentAlarmsRollback();

  @Nullable
  String getDeploymentStrategy();

  @Nullable
  Integer getBakeTimeInMinutes();

  /**
   * Blue/green lifecycle stage at which ECS pauses for an explicit Continue stage. Blank disables
   * the PAUSE hook.
   */
  @Nullable
  default String getLifecyclePauseStage() {
    return null;
  }

  /** Minutes ECS waits at the PAUSE hook before applying the timeout action. */
  @Nullable
  default Integer getLifecyclePauseTimeoutMinutes() {
    return null;
  }

  /** {@code ROLLBACK} (default) or {@code CONTINUE}, applied when the pause hook times out. */
  @Nullable
  default String getLifecyclePauseTimeoutAction() {
    return null;
  }
}
