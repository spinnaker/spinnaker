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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import com.netflix.spinnaker.orca.clouddriver.model.TaskId


class ExecuteCloudFormationChangeSetTaskSpec extends Specification {

  def katoService = Mock(KatoService)

  @Subject
  def executeCloudFormationChangeSetTask = new ExecuteCloudFormationChangeSetTask(katoService: katoService);

  def "should put a kato task information as output"() {
    given:
    def taskId = new TaskId(id: 'id')
    def pipeline = Execution.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      source: 'text',
      regions: ['eu-west-1'],
      isChangeSet: 'true',
      changeSetName: 'changeSetName',
      actionOnReplacement: 'execute'
    ]
    def outputs = [
      changeSets: [
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: 'true'
              ]
            ]
          ]
        ]
      ]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    stage.setOutputs(outputs)

    when:
    def result = executeCloudFormationChangeSetTask.execute(stage)

    then:
    1 * katoService.requestOperations("aws", {
      it.get(0).get("executeCloudFormationChangeSet")
    }) >> Observable.just(taskId)
    result.context.'kato.result.expected' == true
    result.context.'kato.last.task.id' == taskId

  }

  @Unroll
  def "should finish successfully unless is a replacement and it's configured to skip"(){
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'cloudProvider': 'aws',
      'isChangeSet': true,
      'actionOnReplacement': 'skip',
      'changeSetName': 'changeSetName'
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def outputs = [
      changeSets: [
        [
          name: 'notThisChangeSet',
          changes: [
            [
              resourceChange: [
                replacement: null
              ]
            ]
          ]
        ],
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: "true"
              ]
            ],
            [
              resourceChange: [
                replacement: "false"
              ]
            ]
          ]
        ]
      ]
    ]
    stage.setOutputs(outputs)

    when:
    def result = executeCloudFormationChangeSetTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

  }

  def "should throw and exception when is a changeset and is set to fail"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'cloudProvider': 'aws',
      'isChangeSet': true,
      'actionOnReplacement': 'fail',
      'changeSetName': 'changeSetName'
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def outputs = [
      changeSets: [
        [
          name: 'notThisChangeSet',
          changes: [
            [
              resourceChange: [
                replacement: "false"
              ]
            ]
          ]
        ],
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: "true"
              ]
            ],
            [
              resourceChange: [
                replacement: "false"
              ]
            ]
          ]
        ]
      ]
    ]
    stage.setOutputs(outputs)

    when:
    def result = executeCloudFormationChangeSetTask.execute(stage)

    then:
    thrown(RuntimeException)
  }
}
