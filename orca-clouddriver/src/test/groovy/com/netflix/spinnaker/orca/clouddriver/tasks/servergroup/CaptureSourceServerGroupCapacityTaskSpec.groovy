/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CaptureSourceServerGroupCapacityTaskSpec extends Specification {
  def oortHelper = Mock(OortHelper)

  @Subject
  def task = new CaptureSourceServerGroupCapacityTask(oortHelper: oortHelper, objectMapper: new ObjectMapper())

  @Unroll
  void "should no-op if useSourceCapacity is false"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "", stageContext)

    when:
    def result = task.execute(stage)

    then:
    result.outputs.isEmpty()
    result.context.isEmpty()
    stage.context == stageContext

    where:
    stageContext                         || _
    [useSourceCapacity: false]           || _
    [source: [useSourceCapacity: false]] || _
  }

  void "should set useSourceCapacity to false if no source found and preferSourceCapacity is true"() {
    given:
    def stage = new Stage<>(new Pipeline(), "", [useSourceCapacity: true, preferSourceCapacity: true])

    when:
    def result = task.execute(stage)

    then:
    result.context == [useSourceCapacity: false]
  }

  void "should capture source server group capacity and update target capacity"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "", [
      useSourceCapacity: true,
      capacity         : [
        min    : 0,
        desired: 5,
        max    : 10
      ],
      application      : "application",
      cloudProvider    : "aws",
      source           : [
        account: "test",
        asgName: "application-v001",
        region : "us-west-1"
      ]
    ])

    and:
    def targetServerGroup = new TargetServerGroup(
      capacity: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    )

    when:
    def result = task.execute(stage)

    then:
    1 * oortHelper.getTargetServerGroup(
      stage.context.source.account as String,
      stage.context.source.asgName as String,
      stage.context.source.region as String,
      stage.context.cloudProvider as String
    ) >> Optional.of(targetServerGroup)

    result.context.useSourceCapacity == false
    result.context.source.useSourceCapacity == false
    result.context.capacity == [
      min    : 5,
      desired: 5,
      max    : 10
    ]
    result.context.sourceServerGroupCapacitySnapshot == [
      min    : 0,
      desired: 5,
      max    : 10

    ]
    result.outputs.isEmpty()
  }
}
