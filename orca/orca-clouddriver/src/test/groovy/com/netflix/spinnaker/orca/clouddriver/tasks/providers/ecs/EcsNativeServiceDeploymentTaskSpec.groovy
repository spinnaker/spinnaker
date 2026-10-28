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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll

class EcsNativeServiceDeploymentTaskSpec extends Specification {

  @Unroll
  def 'lifecycle task #taskName resolves the deployment ARN from the referenced deploy stage'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def deploy = new StageExecutionImpl(pipeline, 'createServerGroup', 'deploy', [:])
    deploy.refId = 'deploy-ref'
    deploy.outputs.ecsNativeExpectedServiceDeploymentArn = 'arn:deployment:7'
    pipeline.stages << deploy
    def stage = new StageExecutionImpl(pipeline, taskName, taskName, [
      cloudProvider: 'ecs-native', account: 'test', region: 'us-west-2', serverGroupName: 'service',
      deploymentStageRefId: 'deploy-ref'
    ])
    stage.requisiteStageRefIds = ['deploy-ref']

    when:
    def operation = task.convert(stage)

    then:
    operation.ecsNativeExpectedServiceDeploymentArn == 'arn:deployment:7'

    where:
    taskName                         | task
    'continueServiceDeployment'      | new EcsNativeContinueServiceDeploymentTask()
    'stopServiceDeployment'          | new EcsNativeStopServiceDeploymentTask()
  }

  def 'lifecycle tasks retain direct ARN fallback'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = new StageExecutionImpl(pipeline, 'stopServiceDeployment', 'stop', [
      cloudProvider: 'ecs-native', account: 'test', region: 'us-west-2', serverGroupName: 'service',
      ecsNativeExpectedServiceDeploymentArn: 'arn:legacy'
    ])

    expect:
    new EcsNativeStopServiceDeploymentTask().convert(stage).ecsNativeExpectedServiceDeploymentArn == 'arn:legacy'
  }
}
