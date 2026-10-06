/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class UpsertLoadBalancerTaskSpec extends Specification {
  def mapper = new ObjectMapper()
  def kato = Mock(KatoService)

  @Subject
  def task = new UpsertLoadBalancerTask(kato: kato)

  List<Map> serializedTargets(Map context) {
    kato.requestOperations(context.cloudProvider, _) >> new TaskId("task-id")
    def result = task.execute(stage { delegate.context = context })
    return mapper.readValue(
      mapper.writeValueAsString(result.context.targets),
      new TypeReference<List<Map>>() {}
    )
  }

  def "emits the concrete regional listener identity through JSON"() {
    when:
    def targets = serializedTargets([
      cloudProvider: "gce",
      credentials: "stage-account",
      account: "target-account",
      region: "us-central1",
      regionZones: ["us-central1-a"],
      loadBalancerType: "EXTERNAL_MANAGED",
      loadBalancerName: "listener-443",
      name: "display-alias",
      urlMapName: "shared-url-map",
    ])

    then:
    targets == [[
      credentials: "target-account",
      availabilityZones: ["us-central1": ["us-central1-a"]],
      vpcId: null,
      name: "display-alias",
      account: "target-account",
      region: "us-central1",
      loadBalancerType: "EXTERNAL_MANAGED",
      loadBalancerName: "listener-443",
    ]]
  }

  def "keeps the historical target shape for other load balancer families"() {
    when:
    def targets = serializedTargets([
      cloudProvider: cloudProvider,
      credentials: "stage-account",
      region: "us-west-1",
      regionZones: ["us-west-1a"],
      loadBalancerType: loadBalancerType,
      name: "flapjack-frontend",
      vpcId: "vpc-1",
    ])

    then:
    targets == [[
      credentials: "stage-account",
      availabilityZones: ["us-west-1": ["us-west-1a"]],
      vpcId: "vpc-1",
      name: "flapjack-frontend",
    ]]

    where:
    cloudProvider | loadBalancerType
    "aws"         | "application"
    "aws"         | "EXTERNAL_MANAGED"
    "gce"         | "INTERNAL_MANAGED"
  }
}
