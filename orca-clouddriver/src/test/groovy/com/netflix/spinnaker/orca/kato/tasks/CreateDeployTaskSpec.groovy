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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.google.common.collect.Maps
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CreateDeployTaskSpec extends Specification {

  @Subject
  def task = new CreateDeployTask()
  def stage = ExecutionBuilder.stage {
    type = "deploy"
  }
  def mapper = OrcaObjectMapper.newInstance()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deployRegion = "us-east-1"
  def deployConfig = [
    application      : "hodor",
    amiName          : "hodor-ubuntu-1",
    instanceType     : "large",
    securityGroups   : ["a", "b", "c"],
    availabilityZones: [(deployRegion): ["a", "d"]],
    capacity         : [
      min    : 1,
      max    : 20,
      desired: 5
    ],
    credentials      : "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper
    task.defaultBakeAccount = "test"

    stage.execution.stages.add(stage)
    stage.context = deployConfig
    stage.requisiteStageRefIds = []
  }

  def cleanup() {
    stage.execution.stages.clear()
    stage.execution.stages.add(stage)
  }

  def "creates a deployment based on job parameters"() {
    given:
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }
    def expected = Maps.newHashMap(deployConfig)
    expected.with {
      keyPair = 'nf-fzlem-keypair-a'
      securityGroups = securityGroups + ['nf-infrastructure', 'nf-datacenter']
    }

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == expected
  }

  def "requests an allowLaunch operation for each region"() {
    given:
    deployConfig.availabilityZones["us-west-1"] = []

    and:
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    with(operations.findAll {
      it.containsKey("allowLaunchDescription")
    }.allowLaunchDescription) { ops ->
      ops.every {
        it instanceof Map
      }
      region == this.deployConfig.availabilityZones.keySet() as List
    }
  }

  def "don't create allowLaunch tasks when in same account"() {
    given:
    task.defaultBakeAccount = 'fzlem'
    deployConfig.availabilityZones["us-west-1"] = []

    and:
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.findAll { it.containsKey("allowLaunchDescription") }.empty
  }

  def "can include optional parameters"() {
    given:
    stage.context.stack = stackValue
    stage.context.subnetType = subnetTypeValue

    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }
    def expected = [:]

    when:
    task.execute(stage)

    then:
    operations.size() == 2
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == [
      amiName          : 'hodor-ubuntu-1',
      application      : 'hodor',
      availabilityZones: ['us-east-1': ['a', 'd']],
      capacity         : [min: 1, max: 20, desired: 5],
      credentials      : 'fzlem',
      instanceType     : 'large',
      keyPair          : 'nf-fzlem-keypair-a',
      securityGroups   : ['a', 'b', 'c', 'nf-infrastructure', 'nf-datacenter'],
      stack            : 'the-stack-value',
      subnetType       : 'the-subnet-type-value'
    ]

    where:
    stackValue = "the-stack-value"
    subnetTypeValue = "the-subnet-type-value"
  }

  def "can use the AMI supplied by deployment details"() {
    given:
    stage.context.amiName = null
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }
    stage.context.deploymentDetails = [
      ["ami": "not-my-ami", "region": "us-west-1", cloudProvider: "aws"],
      ["ami": "definitely-not-my-ami", "region": "us-west-2", cloudProvider: "aws"],
      ["ami": amiName, "region": deployRegion, cloudProvider: "aws"]
    ]

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "create deploy task adds imageId if present in deployment details"() {
    given:
    stage.context.amiName = null
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }
    stage.context.deploymentDetails = [
      ["imageId": "docker-image-is-not-region-specific", "region": "us-west-1"],
      ["imageId": "docker-image-is-not-region-specific", "region": "us-west-2"],
    ]

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.imageId == "docker-image-is-not-region-specific"

    where:
    amiName = "ami-name-from-bake"
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> Observable.from(taskId)
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context."kato.last.task.id" == taskId
  }

  def "prefers the ami from an upstream stage to one from deployment details"() {
    given:
    stage.context.amiName = null
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }
    stage.context.deploymentDetails = [
      ["ami": "not-my-ami", "region": deployRegion, cloudProvider: "aws"],
      ["ami": "also-not-my-ami", "region": deployRegion, cloudProvider: "aws"]
    ]

    and:
    def bakeStage1 = new Stage(stage.execution, "bake")
    bakeStage1.id = UUID.randomUUID()
    bakeStage1.refId = "1a"
    stage.execution.stages << bakeStage1

    def bakeSynthetic1 =
      new Stage(stage.execution, "bake in $deployRegion", [ami: amiName, region: deployRegion, cloudProvider: "aws"])
    bakeSynthetic1.id = UUID.randomUUID()
    bakeSynthetic1.parentStageId = bakeStage1.id
    stage.execution.stages << bakeSynthetic1

    def bakeStage2 = new Stage(stage.execution, "bake")
    bakeStage2.id = UUID.randomUUID()
    bakeStage2.refId = "2a"
    stage.execution.stages << bakeStage2

    def bakeSynthetic2 =
      new Stage(stage.execution, "bake in $deployRegion", [ami: "parallel-branch-ami", region: deployRegion, cloudProvider: "aws"])
    bakeSynthetic2.id = UUID.randomUUID()
    bakeSynthetic2.parentStageId = bakeStage2.id
    stage.execution.stages << bakeSynthetic2

    def intermediateStage = new Stage(stage.execution, "whatever")
    intermediateStage.id = UUID.randomUUID()
    intermediateStage.refId = "1b"
    stage.execution.stages << intermediateStage

    and:
    intermediateStage.requisiteStageRefIds = [bakeStage1.refId]
    stage.requisiteStageRefIds = [intermediateStage.refId]

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "finds the image from an upstream stage matching the cloud provider"() {
    given:
    stage.context.amiName = null
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }

    and:
    def bakeStage1 = new Stage(stage.execution, "bake")
    bakeStage1.id = UUID.randomUUID()
    bakeStage1.refId = "1a"
    stage.execution.stages << bakeStage1

    def bakeSynthetic1 = new Stage(stage.execution, "bake in $deployRegion", [ami: amiName, region: deployRegion, cloudProvider: "aws"])
    bakeSynthetic1.id = UUID.randomUUID()
    bakeSynthetic1.parentStageId = bakeStage1.id
    stage.execution.stages << bakeSynthetic1

    def bakeStage2 = new Stage(stage.execution, "bake")
    bakeStage2.id = UUID.randomUUID()
    bakeStage2.refId = "2a"
    stage.execution.stages << bakeStage2

    def bakeSynthetic2 = new Stage(stage.execution, "bake in $deployRegion", [ami: "different-image", region: deployRegion, cloudProvider: "gce"])
    bakeSynthetic2.id = UUID.randomUUID()
    bakeSynthetic2.parentStageId = bakeStage2.id
    stage.execution.stages << bakeSynthetic2

    and:
    bakeStage2.requisiteStageRefIds = [bakeStage1.refId]
    stage.requisiteStageRefIds = [bakeStage2.refId]

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "find the image from stage matching the cloud provider"() {
    given:
    stage.context.amiName = null
    stage.context.imageId = null
    def operations = []
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> {
        operations.addAll(it[1].flatten())
        Observable.from(taskId)
      }
    }

    and:
    def findImage1 = new Stage(stage.execution, "findImage")
    findImage1.id = UUID.randomUUID()
    findImage1.refId = "1a"
    stage.execution.stages << findImage1

    def findImageSynthetic1 = new Stage(stage.execution, "findImage", [
      ami: "ami-name-from-findimage",
      region: deployRegion,
      cloudProvider: "titus",
      selectionStrategy:"LARGEST",
      imageId:"docker"
    ])
    findImageSynthetic1.id = UUID.randomUUID()
    findImageSynthetic1.parentStageId = findImage1.id
    stage.execution.stages << findImageSynthetic1

    def findImage2 = new Stage(stage.execution, "findImage")
    findImage2.id = UUID.randomUUID()
    findImage2.refId = "2a"
    stage.execution.stages << findImage2


    def findImageSynthetic2 = new Stage(stage.execution, "findImage", [
      ami: "different-image",
      region: deployRegion,
      cloudProvider: "aws",
      selectionStrategy:"LARGEST",
      imageId:"ami-name-from-findimage"
    ])
    findImageSynthetic2.id = UUID.randomUUID()
    findImageSynthetic2.parentStageId = findImage2.id
    stage.execution.stages << findImageSynthetic2
    stage.context.cloudProvider = cloudProvider

    and:
    findImage2.requisiteStageRefIds = [findImage1.refId]
    stage.requisiteStageRefIds = [findImage2.refId]

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.imageId == imageId

    where:
    cloudProvider | imageId
    "titus"       | "docker"
    "aws"         | "ami-name-from-findimage"
  }

}
