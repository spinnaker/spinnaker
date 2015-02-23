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

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create a Network Load Balancer with health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
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
      1 * httpHealthChecksInsert.execute() >> insertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances.size() == 1 && it.instances.get(0) == INSTANCE}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> insertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, {it.IPAddress == IP_ADDRESS && it.portRange == null}) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp
  }

  void "should create a Network Load Balancer with port range and health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
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
      1 * httpHealthChecksInsert.execute() >> insertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances.size() == 1 && it.instances.get(0) == INSTANCE}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> insertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, {it.IPAddress == IP_ADDRESS && it.portRange == PORT_RANGE}) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp
  }

  void "should create a Network Load Balancer without health checks if non are specified"() {
    setup:
      def computeMock = Mock(Compute)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
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
      1 * targetPoolsInsert.execute() >> insertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp
  }
}
