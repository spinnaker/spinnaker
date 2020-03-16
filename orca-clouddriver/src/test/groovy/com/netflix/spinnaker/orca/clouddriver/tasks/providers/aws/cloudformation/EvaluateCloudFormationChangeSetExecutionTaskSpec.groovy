/*
 * Copyright 2019 Adevinta.
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EvaluateCloudFormationChangeSetExecutionTaskSpec extends Specification {

  @Subject
  def evaluateCloudFormationChangeSetExecutionTask = new EvaluateCloudFormationChangeSetExecutionTask();

  @Unroll
  def "should put in context if the changeSet if a replacement if not present"(){
    given:
      def pipeline = PipelineExecutionImpl.newPipeline('orca')
      def context = [
        cloudProvider: 'aws',
        changeSetName: 'changeSetName',
      ]
      def outputs = [
        changeSets: [
          [
            name: 'changeSetName',
            changes: [
              [
                resourceChange: [
                  replacement: outputIsReplacement
                ]
              ]
            ]
          ]
        ]
      ]
      def stage = new StageExecutionImpl(pipeline, 'test', 'test', context)
      stage.setOutputs(outputs)
    when:
      def result = evaluateCloudFormationChangeSetExecutionTask.execute(stage)

    then:
      result.context.changeSetContainsReplacement == resultContextChangeSetIsReplacement
      result.status == resultStatus

    where:
      outputIsReplacement || resultContextChangeSetIsReplacement | resultStatus
       "false"            || false                               | ExecutionStatus.RUNNING
       "true"             || true                                | ExecutionStatus.RUNNING
  }

  @Unroll
  def "Should return succeed if already set in context changeSetIsReplacement to false"(){
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def context = [
      cloudProvider: 'aws',
      changeSetName: 'changeSetName',
      changeSetContainsReplacement: false
    ]
    def outputs = [
      changeSets: [
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: "false"
              ]
            ]
          ]
        ]
      ]
    ]
    def stage = new StageExecutionImpl(pipeline, 'test', 'test', context)
    stage.setOutputs(outputs)
    when:
    def result = evaluateCloudFormationChangeSetExecutionTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "Should set actionOnReplacement in context with the value from UI"(){
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def context = [
      cloudProvider: 'aws',
      changeSetName: 'changeSetName',
      changeSetContainsReplacement:  true,
      changeSetExecutionChoice: changeSetExecutionChoice
    ]
    def outputs = [
      changeSets: [
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: "true"
              ]
            ]
          ]
        ]
      ]
    ]
    def stage = new StageExecutionImpl(pipeline, 'test', 'test', context)
    stage.setOutputs(outputs)
    when:
    def result = evaluateCloudFormationChangeSetExecutionTask.execute(stage)

    then:
    result.context.actionOnReplacement == actionOnReplacement
    result.status == TaskStatus

    where:
      changeSetExecutionChoice || actionOnReplacement | TaskStatus
        null                   || null                | ExecutionStatus.RUNNING
        "ask"                  || Optional.of("ask")  | ExecutionStatus.SUCCEEDED
  }
}

