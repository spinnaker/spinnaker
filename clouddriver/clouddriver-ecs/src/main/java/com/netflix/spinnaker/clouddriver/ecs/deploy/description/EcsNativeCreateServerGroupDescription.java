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
import org.apache.commons.lang3.StringUtils;

/**
 * Deploy description for the opt-in {@code ecs-native} provider. It extends the existing ECS
 * create-server-group contract and adds the native ECS deployment-configuration knobs that the
 * original provider hard-codes: the minimum-healthy / maximum-percent bounds, whether the
 * deployment circuit breaker should automatically roll back on failure, and deployment alarms.
 *
 * <p>All fields are optional. When left unset the behavior matches the original {@code ecs}
 * provider (100/200 percent, circuit-breaker rollback disabled), so an {@code ecs-native} deploy
 * with no extra configuration is a no-op change relative to today.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcsNativeCreateServerGroupDescription extends CreateServerGroupDescription
    implements EcsNativeDeploymentSettings {

  /** Lower bound (percent) of healthy tasks ECS keeps running during a deployment. */
  @Nullable Integer minimumHealthyPercent;

  /** Upper bound (percent) of tasks ECS may run during a deployment. */
  @Nullable Integer maximumPercent;

  /**
   * When {@code true} (and {@link #isEnableDeploymentCircuitBreaker()} is enabled), a failed
   * deployment is automatically rolled back by ECS to the last completed deployment.
   */
  boolean deploymentCircuitBreakerRollback;

  @Override
  public Boolean getEnableDeploymentCircuitBreaker() {
    return enableDeploymentCircuitBreaker;
  }

  @Override
  public Boolean getDeploymentCircuitBreakerRollback() {
    return deploymentCircuitBreakerRollback;
  }

  /**
   * Names of CloudWatch alarms ECS should watch during a deployment. When non-empty (or {@link
   * #isEnableDeploymentAlarms()} is set), ECS's deployment alarms are enabled for the service: if
   * any named alarm is in {@code ALARM} state during a deployment, ECS marks the deployment failed
   * and (when {@link #isDeploymentAlarmsRollback()} is set) automatically rolls it back.
   * Independent of the deployment circuit breaker, which only reacts to task health.
   */
  @Nullable List<String> alarmNames;

  /** Enables deployment alarms for the service. Implied when {@link #getAlarmNames()} is set. */
  boolean enableDeploymentAlarms;

  /** When {@code true}, a deployment that trips a named alarm is automatically rolled back. */
  boolean deploymentAlarmsRollback;

  /**
   * Native ECS deployment strategy: {@code ROLLING} (ECS's default when left unset) or {@code
   * BLUE_GREEN}. {@code BLUE_GREEN} creates a whole new task set alongside the running one and
   * shifts traffic to it after {@link #getBakeTimeInMinutes()}, rather than replacing tasks
   * in-place a few at a time like {@code ROLLING} does.
   */
  @Nullable String deploymentStrategy;

  /**
   * Minutes ECS waits before terminating the previous service revision and marking the deployment
   * complete. Supported by the ECS deployment controller for both {@code ROLLING} and {@code
   * BLUE_GREEN} (AWS only excludes the {@code EXTERNAL} and {@code CODE_DEPLOY} controllers). For
   * {@code BLUE_GREEN} it is the soak after the new task set reaches steady state; for {@code
   * ROLLING} it is the soak after the new tasks are healthy, before the old revision is fully
   * retired. Left unset, ECS applies its own default (15 minutes for blue/green, none for rolling).
   */
  @Nullable Integer bakeTimeInMinutes;

  /**
   * Deck's "copy existing capacity" flag. ecs-native updates the one durable service in place, so
   * the existing service is its own source: when set (here or as {@code source.useSourceCapacity}),
   * a deploy leaves the desired count and the Application Auto Scaling target untouched. As in
   * classic ecs it affects capacity only; the other service settings still come from the stage.
   */
  @Nullable Boolean useSourceCapacity;

  /**
   * Opt-in: lifecycle stage at which ECS pauses a {@code BLUE_GREEN} deployment until a downstream
   * Continue stage (or the timeout) acts on it. One of {@code POST_SCALE_UP}, {@code
   * POST_TEST_TRAFFIC_SHIFT}, {@code PRE_PRODUCTION_TRAFFIC_SHIFT} or {@code
   * POST_PRODUCTION_TRAFFIC_SHIFT}. Blank disables the PAUSE hook.
   */
  @Nullable String lifecyclePauseStage;

  /** Minutes ECS waits at the pause hook; must outlast any gate (e.g. Manual Judgment) used. */
  @Nullable Integer lifecyclePauseTimeoutMinutes;

  /**
   * Action ECS applies when the pause times out: {@code ROLLBACK} (default) or {@code CONTINUE}.
   */
  @Nullable String lifecyclePauseTimeoutAction;

  /**
   * Target group ECS shifts traffic to during a {@code BLUE_GREEN} cutover.
   *
   * <p>Optional <em>only when the service has no load balancer</em>: with no target group attached,
   * {@code BLUE_GREEN} stands up a new task set with no traffic to shift, so this config is not
   * needed. As soon as a load balancer is attached, AWS ECS <em>requires</em> the ALB traffic-shift
   * config on every load balancer for {@code BLUE_GREEN} and rejects the deploy without it; in that
   * case this field, together with {@link #getProductionListenerRule()}, {@link
   * #getTestListenerRule()} and {@link #getBlueGreenRoleArn()}, is mandatory. All four must be set
   * together, or none at all.
   */
  @Nullable String alternateTargetGroupArn;

  /**
   * ALB listener rule ECS repoints at the alternate target group once the new task set is ready for
   * production traffic. See {@link #getAlternateTargetGroupArn()}.
   */
  @Nullable String productionListenerRule;

  /**
   * ALB listener rule ECS uses to send test traffic at the new task set before production traffic
   * is shifted. See {@link #getAlternateTargetGroupArn()}.
   */
  @Nullable String testListenerRule;

  /**
   * IAM role ECS assumes to modify the listener rules above on this account's behalf. See {@link
   * #getAlternateTargetGroupArn()}.
   */
  @Nullable String blueGreenRoleArn;

  // The derived checks below are final and read through the getters so they stay pure functions of
  // the description's fields, even on test doubles that stub only the getters.

  /** Whether this deploy attaches a target group (single or via mappings). */
  public final boolean declaresLoadBalancer() {
    return StringUtils.isNotBlank(getTargetGroup())
        || (getTargetGroupMappings() != null && !getTargetGroupMappings().isEmpty());
  }

  /** Whether any of the four blue/green ALB traffic-shift fields is set. */
  public final boolean hasAnyTrafficShiftField() {
    return StringUtils.isNotBlank(getAlternateTargetGroupArn())
        || StringUtils.isNotBlank(getProductionListenerRule())
        || StringUtils.isNotBlank(getTestListenerRule())
        || StringUtils.isNotBlank(getBlueGreenRoleArn());
  }

  /** Whether all four blue/green ALB traffic-shift fields are set. */
  public final boolean hasAllTrafficShiftFields() {
    return StringUtils.isNotBlank(getAlternateTargetGroupArn())
        && StringUtils.isNotBlank(getProductionListenerRule())
        && StringUtils.isNotBlank(getTestListenerRule())
        && StringUtils.isNotBlank(getBlueGreenRoleArn());
  }
}
