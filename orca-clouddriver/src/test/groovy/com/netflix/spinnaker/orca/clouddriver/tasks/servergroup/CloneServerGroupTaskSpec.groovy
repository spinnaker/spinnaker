/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.clone.BakeryImageAccessDescriptionDecorator
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CloneServerGroupTaskSpec extends Specification {
  @Subject task = new CloneServerGroupTask()
  def stage = ExecutionBuilder.stage {
    type = "cloneServerGroup"
  }
  def mapper = OrcaObjectMapper.newInstance()
  def taskId = new TaskId(UUID.randomUUID().toString())

  //The minimum required fields to cloneServerGroup
  def cloneServerGroupConfig = [
    application      : "hodor",
    availabilityZones: ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]],
    credentials      : "fzlem",
    amiName          : "hodor-image",
    cloudProvider    : "aws"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper
    task.cloneDescriptionDecorators = [new BakeryImageAccessDescriptionDecorator()]

    stage.execution.stages.add(stage)
    stage.context = cloneServerGroupConfig
  }

  def "creates a deployment based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 3
    operations[0].cloneServerGroup.amiName == "hodor-image"
    operations[0].cloneServerGroup.application == "hodor"
    operations[0].cloneServerGroup.availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
    operations[0].cloneServerGroup.credentials == "fzlem"
  }

  def "can include optional parameters"() {
    given:
    stage.context.instanceType = "t1.megaBig"
    stage.context.stack = "hodork"

    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 3
    with(operations[0].cloneServerGroup) {
      amiName == "hodor-image"
      application == "hodor"
      availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
      credentials == "fzlem"
      instanceType == "t1.megaBig"
      stack == "hodork"
    }
  }

  def "amiName prefers value from context over bake input"() {
    given:
    stage.context.amiName = contextAmi
    stage.context.ami = stageAmi


    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 3
    with(operations[0].cloneServerGroup) {
      amiName == contextAmi
      application == "hodor"
      availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
      credentials == "fzlem"
    }

    where:
    contextAmi = 'ami-ctx'
    stageAmi = 'ami-stage'
  }

  def "amiName uses value from bake"() {
    given:
    def bakeEast = new Stage(stage.execution, "bake", [ami: bakeAmi, region: 'us-east-1', cloudProvider: 'aws'])
    bakeEast.refId = "1"
    stage.refId = "3"
    stage.requisiteStageRefIds = [ "1" ]
    stage.context.amiName = null
    stage.execution.stages.removeAll()
    stage.execution.stages.addAll([bakeEast, stage])


    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 3
    with(operations[0].cloneServerGroup) {
      amiName == bakeAmi
      application == "hodor"
      availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
      credentials == "fzlem"
    }

    where:
    bakeAmi = "ami-bake"
  }

  def "image is not resolved from bake if cloud provider does not match"() {
    given:
    def bakeEast = new Stage(stage.execution, "bake", [ami: bakeAmi, region: 'us-east-1', cloudProvider: 'gce'])
    bakeEast.refId = "1"
    stage.refId = "3"
    stage.requisiteStageRefIds = [ "1" ]
    stage.context.amiName = null
    stage.execution.stages.removeAll()
    stage.execution.stages.addAll([bakeEast, stage])


    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    with(operations[0].cloneServerGroup) {
      !amiName
      application == "hodor"
      availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
      credentials == "fzlem"
    }

    where:
    bakeAmi = "ami-bake"
  }

  def "calls allowlaunch prior to copyLast"() {
    given:
    stage.context.amiName = contextAmi


    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 3
    operations[0].cloneServerGroup.amiName == contextAmi
    operations[1].allowLaunchDescription.amiName == contextAmi
    operations[1].allowLaunchDescription.region == "us-east-1"
    operations[2].allowLaunchDescription.amiName == contextAmi
    operations[2].allowLaunchDescription.region == "us-west-1"

    where:
    contextAmi = "ami-ctx"
  }

  def "favors 'region' to 'availabilityZones' when adding allowLaunch"() {
    given:
    stage.context.amiName = "ami-123"
    stage.context.region = "eu-west-1"

    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(_, _) >> {
        operations = it[1]
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 2
    operations[1].allowLaunchDescription.region == "eu-west-1"
  }
}
