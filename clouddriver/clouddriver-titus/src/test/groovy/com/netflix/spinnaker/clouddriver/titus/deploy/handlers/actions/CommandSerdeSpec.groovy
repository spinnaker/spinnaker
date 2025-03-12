/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers.actions

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.event.CompositeSpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper.TargetGroupLookupResult
import static com.netflix.spinnaker.clouddriver.titus.deploy.actions.AttachTitusServiceLoadBalancers.AttachTitusServiceLoadBalancersCommand
import static com.netflix.spinnaker.clouddriver.titus.deploy.actions.CopyTitusServiceScalingPolicies.CopyTitusServiceScalingPoliciesCommand
import static com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App.Front50App
import static com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App.LoadFront50AppCommand
import static com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy.PrepareTitusDeployCommand

class CommandSerdeSpec extends Specification {

  @Shared NetflixTitusCredentials titusCredentials = Mock() {
    getName() >> "titus"
  }

  @Shared TitusDeployDescription deployDescription = new TitusDeployDescription(credentials: titusCredentials)

  @Shared Front50App front50App = new Front50App("example@example.com", true)

  @Unroll
  def "can serialize and deserialize #command.class.simpleName"() {
    given:
    ObjectMapper objectMapper = new ObjectMapper()
    objectMapper
      .findAndRegisterModules()

    and:
    registerSubtypes(objectMapper, command)
    initializeEvent(command)

    when:
    def serialized = objectMapper.writeValueAsString(command)
    objectMapper.readValue(serialized, SpinnakerEvent)

    then:
    noExceptionThrown()

    where:
    command << [
      LoadFront50AppCommand.builder()
        .appName("myApp")
        .nextCommand(PrepareTitusDeployCommand.builder()
          .description(deployDescription)
          .front50App(front50App)
          .build())
        .allowMissing(true)
        .build(),
      PrepareTitusDeployCommand.builder()
        .description(deployDescription)
        .front50App(front50App)
        .build(),
      AttachTitusServiceLoadBalancersCommand.builder()
        .description(deployDescription)
        .jobUri("http://localhost/id")
        .targetGroupLookupResult(new TargetGroupLookupResult())
        .build(),
      CopyTitusServiceScalingPoliciesCommand.builder()
        .description(deployDescription)
        .jobUri("http://localhost/id")
        .deployedServerGroupName("myapp-v000")
        .build(),
      SubmitTitusJob.SubmitTitusJobCommand.builder()
        .description(deployDescription)
        .submitJobRequest(SubmitJobRequest.builder().build())
        .nextServerGroupName("myapp-v000")
        .front50App(front50App)
        .build()
    ]
  }

  static void registerSubtypes(ObjectMapper objectMapper, SpinnakerEvent event) {
    if (event instanceof CompositeSpinnakerEvent) {
      objectMapper.registerSubtypes(((CompositeSpinnakerEvent) event).composedEvents.collect { it.class })
    }
    objectMapper.registerSubtypes(event.class)
  }

  static void initializeEvent(SpinnakerEvent event) {
    if (event instanceof CompositeSpinnakerEvent) {
      event.composedEvents.forEach {
        initializeEvent(it)
      }
    }
    event.metadata = new EventMetadata(
      UUID.randomUUID().toString(),
      "aggType",
      "aggId",
      0,
      0,
      Instant.now(),
      "unknown",
      "unknown"
    )
  }
}
