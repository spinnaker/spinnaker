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

  def "emits target-local concrete listener identity through JSON"() {
    given:
    def executionStage = stage {
      context = [
        cloudProvider: "gce",
        credentials: "legacy-stage-account",
        account: "target-account",
        region: "us-central1",
        regionZones: ["us-central1-a"],
        loadBalancerType: "EXTERNAL_MANAGED",
        loadBalancerName: "listener-443",
        name: "display-alias-must-not-win",
        urlMapName: "shared-url-map",
        vpcId: "network-a",
      ]
    }
    kato.requestOperations("gce", _) >> new TaskId("task-id")

    when:
    def result = task.execute(executionStage)
    List<Map> targets = mapper.readValue(
      mapper.writeValueAsString(result.context.targets),
      new TypeReference<List<Map>>() {}
    )

    then:
    targets == [[
      credentials: "target-account",
      account: "target-account",
      region: "us-central1",
      availabilityZones: ["us-central1": ["us-central1-a"]],
      loadBalancerType: "EXTERNAL_MANAGED",
      loadBalancerName: "listener-443",
      name: "listener-443",
      urlMapName: "shared-url-map",
      vpcId: "network-a",
    ]]
  }
}
