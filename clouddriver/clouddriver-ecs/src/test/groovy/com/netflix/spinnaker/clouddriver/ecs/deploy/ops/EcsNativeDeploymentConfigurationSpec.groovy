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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookAction
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookStage
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookTargetType
import spock.lang.Specification
import spock.lang.Unroll

class EcsNativeDeploymentConfigurationSpec extends Specification {

  private static EcsNativeCreateServerGroupDescription settings(Map values) {
    def description = new EcsNativeCreateServerGroupDescription()
    values.each { key, value -> description."$key" = value }
    description
  }

  def 'no PAUSE hook is configured unless lifecyclePauseStage is set'() {
    expect:
    !EcsNativeDeploymentConfiguration.forCreate(settings(deploymentStrategy: 'BLUE_GREEN'), null).hasLifecycleHooks()
  }

  @Unroll
  def 'builds a PAUSE hook with explicit timeout and action #expectedAction (configured=#configured)'() {
    when:
    def hook = EcsNativeDeploymentConfiguration.forCreate(settings(
      deploymentStrategy: 'BLUE_GREEN',
      lifecyclePauseStage: 'post_test_traffic_shift',
      lifecyclePauseTimeoutMinutes: 45,
      lifecyclePauseTimeoutAction: configured), null).lifecycleHooks().first()

    then:
    hook.targetType() == DeploymentLifecycleHookTargetType.PAUSE
    hook.lifecycleStages() == [DeploymentLifecycleHookStage.POST_TEST_TRAFFIC_SHIFT]
    hook.timeoutConfiguration().timeoutInMinutes() == 45
    hook.timeoutConfiguration().action() == expectedAction

    where:
    configured | expectedAction
    null       | DeploymentLifecycleHookAction.ROLLBACK
    'continue' | DeploymentLifecycleHookAction.CONTINUE
  }

  @Unroll
  def 'rejects an invalid pause configuration: #reason'() {
    when:
    EcsNativeDeploymentConfiguration.validateLifecycleSupport(settings(values))

    then:
    thrown(IllegalArgumentException)

    where:
    reason                  | values
    'rolling strategy'      | [deploymentStrategy: 'ROLLING', lifecyclePauseStage: 'POST_SCALE_UP', lifecyclePauseTimeoutMinutes: 5]
    'non-pausable stage'    | [deploymentStrategy: 'BLUE_GREEN', lifecyclePauseStage: 'BAKE_TIME', lifecyclePauseTimeoutMinutes: 5]
    'missing timeout'       | [deploymentStrategy: 'BLUE_GREEN', lifecyclePauseStage: 'POST_SCALE_UP']
    'unknown timeout action'| [deploymentStrategy: 'BLUE_GREEN', lifecyclePauseStage: 'POST_SCALE_UP', lifecyclePauseTimeoutMinutes: 5, lifecyclePauseTimeoutAction: 'ABORT']
  }
}
