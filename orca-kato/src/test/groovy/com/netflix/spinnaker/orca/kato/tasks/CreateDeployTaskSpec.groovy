/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.kato.api.DeployOperation
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import rx.Observable

class CreateDeployTaskSpec extends Specification {

  @Subject task = new CreateDeployTask()
  def context = new SimpleTaskContext()
  def mapper = new ObjectMapper()

  def deployConfig = [
      application: "hodor",
      amiName: "hodor-ubuntu-1",
      instanceType: "large",
      securityGroups: ["a", "b", "c"],
      availabilityZones: ["us-east-1": ["a", "d"]],
      capacity: [
          min: 1,
          max: 20,
          desired: 5
      ],
      credentials: "fzlem",
      stack: null,
      subnetType: null
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    context.region = "us-west-1"
    context.deploy = mapper.writeValueAsString(deployConfig)
  }

  def "creates a deployment based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }

    when:
    task.execute(context)

    then:
    operations.size() == 1
    with(operations[0]) {
      it instanceof DeployOperation
      application == deployConfig.application
      amiName == deployConfig.amiName
      instanceType == deployConfig.instanceType
      securityGroups == deployConfig.securityGroups
      availabilityZones == deployConfig.availabilityZones
      capacity.min == deployConfig.capacity.min
      capacity.max == deployConfig.capacity.max
      capacity.desired == deployConfig.capacity.desired
      credentials == deployConfig.credentials

      !stack.present
      !subnetType.present
    }
  }
}
