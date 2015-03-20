/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleNetworkLoadBalancerDescription
import spock.lang.Specification
import spock.lang.Subject

class CreateGoogleNetworkLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final NETWORK_LOAD_BALANCER_NAME = "default"
  private static final REGION = "us-central1"
  private static final HEALTH_CHECK_PORT = 80
  private static final INSTANCE = "inst"
  private static final IP_ADDRESS = "1.1.1.1"
  private static final PORT_RANGE = "8080-8080"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final TARGET_POOL_OP_NAME = "target-pool-op"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create a Network Load Balancer with health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new com.google.api.services.compute.model.Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new com.google.api.services.compute.model.Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new com.google.api.services.compute.model.Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          healthCheck: [port: HEALTH_CHECK_PORT],
          instances: [INSTANCE],
          ipAddress: IP_ADDRESS,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new CreateGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == HEALTH_CHECK_PORT && it.checkIntervalSec == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances.size() == 1 && it.instances.get(0) == INSTANCE}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, {it.IPAddress == IP_ADDRESS && it.portRange == null}) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * computeMock.regionOperations() >> regionOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }

  void "should create a Network Load Balancer with port range and health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new com.google.api.services.compute.model.Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new com.google.api.services.compute.model.Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new com.google.api.services.compute.model.Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          healthCheck: [port: HEALTH_CHECK_PORT],
          instances: [INSTANCE],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new CreateGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == HEALTH_CHECK_PORT && it.checkIntervalSec == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances.size() == 1 && it.instances.get(0) == INSTANCE}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, {it.IPAddress == IP_ADDRESS && it.portRange == PORT_RANGE}) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * computeMock.regionOperations() >> regionOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }

  void "should create a Network Load Balancer without health checks if none are specified"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new com.google.api.services.compute.model.Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new com.google.api.services.compute.model.Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new CreateGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      0 * computeMock.httpHealthChecks()
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, _) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }
}
