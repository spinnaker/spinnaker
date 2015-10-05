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
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleLoadBalancerTaskSpec extends Specification {
  @Subject task = new UpsertGoogleLoadBalancerTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def upsertGoogleLoadBalancerConfig = [
    loadBalancerName: "flapjack-frontend",
    region: ["us-central1"],
    credentials: "test-account-name"
  ]

  def upsertGoogleLoadBalancerConfigWithPort = [
    loadBalancerName: "flapjack-frontend",
    region: ["us-central1"],
    credentials: "test-account-name",
    ipProtocol: "TCP",
    portRange: "8080",
  ]

  def upsertGoogleLoadBalancerConfigWithPortRange = [
    loadBalancerName: "flapjack-frontend",
    region: ["us-central1"],
    credentials: "test-account-name",
    ipProtocol: "UDP",
    portRange: "4040-5050",
  ]

  def upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck = [
    loadBalancerName: "flapjack-frontend",
    region: ["us-central1"],
    credentials: "test-account-name",
    healthCheck: [
      port: 80,
      requestPath: "/healthCheck",
      timeoutSec: 5,
      checkIntervalSec: 10,
      healthyThreshold: 10,
      unhealthyThreshold: 2,
      ipProtocol: "TCP",
      portRange: "4040-5050",
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
      with(operations[0].upsertGoogleLoadBalancerDescription) {
        it instanceof Map
        loadBalancerName == this.upsertGoogleLoadBalancerConfig.loadBalancerName
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
      with(operations[0].upsertGoogleLoadBalancerDescription) {
        it instanceof Map
        loadBalancerName == this.upsertGoogleLoadBalancerConfigWithPort.loadBalancerName
        region == this.upsertGoogleLoadBalancerConfigWithPort.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPort.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPort.portRange
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
      with(operations[0].upsertGoogleLoadBalancerDescription) {
        it instanceof Map
        loadBalancerName == this.upsertGoogleLoadBalancerConfigWithPortRange.loadBalancerName
        region == this.upsertGoogleLoadBalancerConfigWithPortRange.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPortRange.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPortRange.portRange
        ipProtocol == this.upsertGoogleLoadBalancerConfigWithPortRange.ipProtocol
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
      with(operations[0].upsertGoogleLoadBalancerDescription) {
        it instanceof Map
        loadBalancerName == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.loadBalancerName
        region == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.region
        credentials == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.credentials
        portRange == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.portRange
        with(healthCheck) {
          port == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.port
          requestPath == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.requestPath
          timeoutSec == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.timeoutSec
          checkIntervalSec == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.checkIntervalSec
          healthyThreshold == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.healthyThreshold
          unhealthyThreshold == this.upsertGoogleLoadBalancerConfigWithPortRangeAndHealthCheck.healthCheck.unhealthyThreshold
        }
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
      result.outputs."kato.last.task.id" == taskId
      result.outputs."upsert.account" == upsertGoogleLoadBalancerConfig.credentials
  }
}
