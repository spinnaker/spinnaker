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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.AllowLaunchOperation
import com.netflix.spinnaker.orca.kato.api.ops.DeployOperation
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CreateDeployTaskSpec extends Specification {

  @Subject task = new CreateDeployTask()
  def context = new SimpleTaskContext()
  def mapper = new ObjectMapper()
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

    deployConfig.each {
      context."deploy.${it.key}" = it.value
    }
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

    when:
    task.execute(context)

    then:
    with(operations.find { it.containsKey("basicAmazonDeployDescription") }.basicAmazonDeployDescription) {
      it instanceof DeployOperation
      application == deployConfig.application
      amiName == deployConfig.amiName
      instanceType == deployConfig.instanceType
      securityGroups == deployConfig.securityGroups + ['nf-infrastructure-vpc', 'nf-datacenter-vpc']
      availabilityZones == deployConfig.availabilityZones
      capacity.min == deployConfig.capacity.min
      capacity.max == deployConfig.capacity.max
      capacity.desired == deployConfig.capacity.desired
      credentials == deployConfig.credentials

      !stack.present
      !subnetType.present
    }
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
    task.execute(context)

    then:
    with(operations.findAll { it.containsKey("allowLaunchDescription") }.allowLaunchDescription) { ops ->
      ops.every {
        it instanceof AllowLaunchOperation
      }
      region == deployConfig.availabilityZones.keySet() as List
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
    task.execute(context)

    then:
    operations.findAll { it.containsKey("allowLaunchDescription") }.empty
  }

  def "can include optional parameters"() {
    given:
    context."deploy.stack" = stackValue
    context."deploy.subnetType" = subnetTypeValue

    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(context)

    then:
    operations.size() == 2
    with(operations.find { it.containsKey("basicAmazonDeployDescription") }.basicAmazonDeployDescription) {
      stack.get() == context."deploy.stack"
      subnetType.get() == context."deploy.subnetType"
    }

    where:
    stackValue = "the-stack-value"
    subnetTypeValue = "the-subnet-type-value"
  }

  def "can use the AMI output by a bake"() {
    given:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    and:
    context."bake.ami" = amiName

    when:
    task.execute(context)

    then:
    operations.find { it.containsKey("basicAmazonDeployDescription") }.basicAmazonDeployDescription.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> Observable.from(taskId)
    }

    when:
    def result = task.execute(context)

    then:
    result.status == TaskResult.Status.SUCCEEDED
    result.outputs."kato.task.id" == taskId
  }
}
