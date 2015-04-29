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
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CreateDeployTaskSpec extends Specification {

  @Subject
  def task = new CreateDeployTask()
  def stage = new PipelineStage(new Pipeline(), "deploy")
  def mapper = new OrcaObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deployConfig = [
    application      : "hodor",
    amiName          : "hodor-ubuntu-1",
    instanceType     : "large",
    securityGroups   : ["a", "b", "c"],
    availabilityZones: ["us-east-1": ["a", "d"]],
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
  }

  def cleanup() {
    stage.execution.stages.clear()
    stage.execution.stages.add(stage)
  }

  def "creates a deployment based on job parameters"() {
    given:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    def expected = Maps.newHashMap(deployConfig)
    expected.with {
      keyPair = 'nf-fzlem-keypair-a'
      securityGroups = securityGroups + ['nf-infrastructure', 'nf-datacenter']
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription == expected
  }

  def "requests an allowLaunch operation for each region"() {
    given:
    deployConfig.availabilityZones["us-west-1"] = []

    and:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

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
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.findAll { it.containsKey("allowLaunchDescription") }.empty
  }

  def "can include optional parameters"() {
    given:
    stage.context.stack = stackValue
    stage.context.subnetType = subnetTypeValue

    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    def expected = [:]

    when:
    task.execute(stage.asImmutable())

    then:
    operations.size() == 2
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription == [
      amiName          : 'hodor-ubuntu-1',
      application      : 'hodor',
      availabilityZones: ['us-east-1': ['a', 'd']],
      capacity         : [min: 1, max: 20, desired: 5],
      credentials      : 'fzlem',
      instanceType     : 'large',
      keyPair          : 'nf-fzlem-keypair-a',
      securityGroups   : ['a', 'b', 'c', 'nf-infrastructure-vpc', 'nf-datacenter-vpc'],
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
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    stage.context.deploymentDetails = [
      ["ami": "not-my-ami", "region": "us-west-1"],
      ["ami": "definitely-not-my-ami", "region": "us-west-2"],
      ["ami": amiName, "region": deployConfig.availabilityZones.keySet()[0]]
    ]

    when:
    task.execute(stage.asImmutable())

    then:
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> Observable.from(taskId)
    }

    when:
    def result = task.execute(stage.asImmutable())

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
  }

  def "replaces stack name with jenkins branch"(){
    given:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    stage.context.stack = '${scmInfo.branch.replaceAll("-", "")}'
    stage.execution.trigger.putAll(
      [buildInfo: [scm: [[branch: 'name-of-my-stack']]]]
    )

    when:
    def result = task.execute(stage.asImmutable())

    then:
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription.stack == "nameofmystack"

    result.stageOutputs.stack == 'nameofmystack'

  }
}
