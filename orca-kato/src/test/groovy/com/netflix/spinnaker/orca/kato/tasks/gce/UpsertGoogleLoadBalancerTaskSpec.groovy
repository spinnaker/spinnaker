/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleLoadBalancerTaskSpec extends Specification {
  @Subject task = new UpsertGoogleLoadBalancerTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def upsertGoogleLoadBalancerConfig = [
    name       : "flapjack-frontend",
    region     : ["us-central1"],
    credentials: "test-account-name"
  ]

  def upsertGoogleLoadBalancerConfigWithPort = [
    name       : "flapjack-frontend",
    region     : ["us-central1"],
    credentials: "test-account-name",
    listeners  : [
      [
        protocol: "TCP",
        portRange: "8080",
        healthCheck: false
      ]
    ]
  ]

  def upsertGoogleLoadBalancerConfigWithPortRange = [
    name       : "flapjack-frontend",
    region     : ["us-central1"],
    credentials: "test-account-name",
    listeners  : [
      [
        protocol: "UDP",
        portRange: "4040-5050",
        healthCheck: false
      ]
    ]
  ]

  def upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck = [
    name       : "flapjack-frontend",
    region     : ["us-central1"],
    credentials: "test-account-name",
    healthCheckPort: 80,
    healthCheckPath: "/healthCheck",
    healthTimeout: 5,
    healthInterval: 10,
    healthyThreshold: 10,
    unhealthyThreshold: 2,
    listeners  : [
      [
        protocol: "TCP",
        portRange: "4040-5050",
        healthCheck: true
      ]
    ]
  ]

  def upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse = [
    name       : "flapjack-frontend",
    region     : ["us-central1"],
    credentials: "test-account-name",
    healthCheckPort: 80,
    healthCheckPath: "/healthCheck",
    healthTimeout: 5,
    healthInterval: 10,
    healthyThreshold: 10,
    unhealthyThreshold: 2,
    listeners  : [
      [
        protocol: "TCP",
        portRange: "4040-5050",
        healthCheck: false
      ]
    ]
  ]

  def "creates an upsert load balancer task based on job parameters"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfig)
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].createGoogleNetworkLoadBalancerDescription) {
        it instanceof Map
        networkLoadBalancerName == this.upsertGoogleLoadBalancerConfig.name
        region == this.upsertGoogleLoadBalancerConfig.region
        credentials == this.upsertGoogleLoadBalancerConfig.credentials
        !portRange
        !healthCheck
      }
  }

  def "creates an upsert load balancer task with port"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfigWithPort)
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].createGoogleNetworkLoadBalancerDescription) {
        it instanceof Map
        networkLoadBalancerName == this.upsertGoogleLoadBalancerConfigWithPort.name
        region == this.upsertGoogleLoadBalancerConfigWithPort.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPort.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPort.listeners[0].portRange
        !healthCheck
      }
  }

  def "creates an upsert load balancer task with port range and IP protocol"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfigWithPortRange)
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].createGoogleNetworkLoadBalancerDescription) {
        it instanceof Map
        networkLoadBalancerName == this.upsertGoogleLoadBalancerConfigWithPortRange.name
        region == this.upsertGoogleLoadBalancerConfigWithPortRange.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPortRange.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPortRange.listeners[0].portRange
        ipProtocol == this.upsertGoogleLoadBalancerConfigWithPortRange.listeners[0].protocol
        !healthCheck
      }
  }

  def "creates an upsert load balancer task with port range and health check"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck)
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].createGoogleNetworkLoadBalancerDescription) {
        it instanceof Map
        networkLoadBalancerName == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.name
        region == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.listeners[0].portRange
        healthCheck == [
          port: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheckPort,
          requestPath: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheckPath,
          timeoutSec: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthTimeout,
          checkIntervalSec: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthInterval,
          healthyThreshold: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthyThreshold,
          unhealthyThreshold: this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.unhealthyThreshold
        ]
      }
  }

  def "health check parameters are ignored if health check is false"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse)
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].createGoogleNetworkLoadBalancerDescription) {
        it instanceof Map
        networkLoadBalancerName == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse.name
        region == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheckFalse.listeners[0].portRange
        !healthCheck
      }
  }

  def "returns a success status with the kato task id"() {
    setup:
      stage.context.putAll(upsertGoogleLoadBalancerConfig)
      task.kato = Stub(KatoService) {
        requestOperations(*_) >> rx.Observable.from(taskId)
      }

    when:
      def result = task.execute(stage.asImmutable())

    then:
      result.status == ExecutionStatus.SUCCEEDED
      result.outputs."kato.task.id" == taskId
      result.outputs."upsert.account" == upsertGoogleLoadBalancerConfig.credentials
  }
}
