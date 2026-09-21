/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable aw or agreed to in writing, software
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
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for UpsertLoadBalancersTask
 */
class UpsertLoadBalancersTaskSpec extends Specification {
  @Subject
  def task = new UpsertLoadBalancersTask()

  def stage = new StageExecutionImpl(type: "")
  def taskId = new TaskId(UUID.randomUUID().toString())
  def mapper = new ObjectMapper()

  def insertLoadBalancerConfig = [
      type: "upsertLoadBalancers",
      name: "my test",
      cloudProvider: "aws",
      loadBalancers: [
          [
              name: "test-loadbalancer-1",
              stack: "1",
              isInternal: true,
              credentials: "test-account-1",
              region: "us-west-2",
              vpcId: null,
              healthCheckProtocol: "HTTP",
              healthCheckPort: 7001,
              healthCheckPath: "/healthcheck",
              healthTimeout: 5,
              healthInterval: 10,
              healthyThreshold: 10,
              unhealthyThreshold: 2,
              regionZones: [
                  "us-west-2b"
              ],
              securityGroups: [],
              listeners: [
                  [
                      internalProtocol: "HTTP",
                      internalPort: 7001,
                      externalProtocol: "HTTP",
                      externalPort: 80
                  ]
              ],
              regions: [
                  "us-west-2"
              ],
              editMode: true
          ],
          [
              name: "test-loadbalancer-2",
              stack: "2",
              isInternal: true,
              credentials: "test-account-2",
              region: "us-west-2",
              vpcId: null,
              healthCheckProtocol: "HTTP",
              healthCheckPort: 7001,
              healthCheckPath: "/healthcheck",
              healthTimeout: 5,
              healthInterval: 10,
              healthyThreshold: 10,
              unhealthyThreshold: 2,
              regionZones: [
                  "us-west-2b"
              ],
              securityGroups: [],
              listeners: [
                  [
                      internalProtocol: "HTTP",
                      internalPort: 7001,
                      externalProtocol: "HTTP",
                      externalPort: 80
                  ]
              ],
              regions: [
                  "us-west-2"
              ],
              editMode: true
          ]
      ]
  ]


  def setup() {
    stage.context.putAll(insertLoadBalancerConfig)
  }

  def "create load balancers"() {
    given:
    def operations

    task.kato = Mock(KatoService) {
      1 * requestOperations(this.insertLoadBalancerConfig.cloudProvider, _) >> {
        operations = it[1]
        taskId
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == this.insertLoadBalancerConfig.loadBalancers.size()
    with(operations.upsertLoadBalancer) {
      it instanceof ArrayList
      for (int i = 0; i < it.size(); i++) {
        it[i] instanceof Map
        it[i].name == this.insertLoadBalancerConfig.loadBalancers[i].name
        it[i].stack == this.insertLoadBalancerConfig.loadBalancers[i].stack
        it[i].regions == this.insertLoadBalancerConfig.loadBalancers[i].regions
        it[i].regionZones == this.insertLoadBalancerConfig.loadBalancers[i].regionZones
        it[i].credentials == this.insertLoadBalancerConfig.loadBalancers[i].credentials
        it[i].vpcId == this.insertLoadBalancerConfig.loadBalancers[i].vpcId
        it[i].listeners == this.insertLoadBalancerConfig.loadBalancers[i].listeners
      }
    }
  }

  def "emits one target-local identity per operation through JSON"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      credentials: "stage-account",
      loadBalancerType: "HTTP",
      loadBalancers: [
        [
          account: "account-a",
          region: "us-central1",
          availabilityZones: ["us-central1": ["us-central1-a"]],
          loadBalancerType: "EXTERNAL_MANAGED",
          name: "listener-a",
          urlMapName: "shared-map",
        ],
        [
          credentials: "account-b",
          region: "us-east1",
          regionZones: ["us-east1-b"],
          loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
          loadBalancerName: "listener-b",
          name: "display-alias-must-not-win",
        ],
      ],
    ]
    task.kato = Stub(KatoService) {
      requestOperations("gce", _) >> taskId
    }

    when:
    def result = task.execute(stage)
    List<Map> targets = mapper.readValue(
      mapper.writeValueAsString(result.context.targets),
      new TypeReference<List<Map>>() {}
    )

    then:
    targets*.subMap([
      "credentials",
      "account",
      "region",
      "loadBalancerType",
      "loadBalancerName",
      "name",
      "urlMapName",
    ]) == [
      [
        credentials: "account-a",
        account: "account-a",
        region: "us-central1",
        loadBalancerType: "EXTERNAL_MANAGED",
        loadBalancerName: "listener-a",
        name: "listener-a",
        urlMapName: "shared-map",
      ],
      [
        credentials: "account-b",
        account: "account-b",
        region: "us-east1",
        loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
        loadBalancerName: "listener-b",
        name: "listener-b",
        urlMapName: null,
      ],
    ]
  }

}
