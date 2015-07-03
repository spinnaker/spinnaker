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

package com.netflix.spinnaker.kato.gce.deploy.ops.loadbalancer

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.HttpHealthCheckList
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceAggregatedList
import com.google.api.services.compute.model.InstancesScopedList
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.google.api.services.compute.model.TargetPoolList
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleNetworkLoadBalancerDescription
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleNetworkLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final NETWORK_LOAD_BALANCER_NAME = "default"
  private static final TARGET_POOL_NAME = "$NETWORK_LOAD_BALANCER_NAME-targetpool"
  private static final REGION = "us-central1"
  private static final INSTANCE_1 = "instance-1"
  private static final INSTANCE_2 = "instance-2"
  private static final INSTANCE_3 = "instance-3"
  private static final INSTANCE_1_URL =
    "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/us-central1-a/instances/$INSTANCE_1"
  private static final INSTANCE_2_URL =
    "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/us-central1-a/instances/$INSTANCE_2"
  private static final INSTANCE_3_URL =
    "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/us-central1-a/instances/$INSTANCE_3"
  private static final IP_PROTOCOL_TCP = "TCP"
  private static final IP_PROTOCOL_UDP = "UDP"
  private static final IP_ADDRESS = "1.1.1.1"
  private static final PORT_RANGE = "8080-8080"
  private static final HEALTH_CHECK_NAME = "$NETWORK_LOAD_BALANCER_NAME-healthcheck"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final TARGET_POOL_OP_NAME = "target-pool-op"
  private static final ADD_HEALTH_CHECK_OP_NAME = "add-health-check-op"
  private static final REMOVE_HEALTH_CHECK_OP_NAME = "remove-health-check-op"
  private static final DELETE_FORWARDING_RULE_OP_NAME = "delete-forwarding-rule-op"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create a network load balancer with health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL)])
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal =
        new ForwardingRuleList(items: [new ForwardingRule(name: NETWORK_LOAD_BALANCER_NAME + "-other")])
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          healthCheck: [port: Constants.DEFAULT_PORT],
          instances: [INSTANCE_1],
          ipAddress: IP_ADDRESS,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == Constants.DEFAULT_PORT && it.checkIntervalSec == 5}) >>
        httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances == [INSTANCE_1_URL]}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION,
        {it.IPAddress == IP_ADDRESS && it.IPProtocol == IP_PROTOCOL_TCP && it.portRange == Constants.DEFAULT_PORT_RANGE}) >>
        forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * computeMock.regionOperations() >> regionOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }

  void "should create a network load balancer with port range and health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL)])
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [])
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          healthCheck: [port: Constants.DEFAULT_PORT],
          instances: [INSTANCE_1],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == Constants.DEFAULT_PORT && it.checkIntervalSec == 5}) >>
        httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, {it.instances == [INSTANCE_1_URL]}) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION,
        {it.IPAddress == IP_ADDRESS && it.IPProtocol == IP_PROTOCOL_TCP && it.portRange == PORT_RANGE}) >>
        forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * computeMock.regionOperations() >> regionOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }

  void "should create a network load balancer without health checks if none are specified"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [])
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal
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

  void "should create a network load balancer with the specified IP protocol if it is supported"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionTargetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsInsert = Mock(Compute.TargetPools.Insert)
      def targetPoolsInsertOp = new Operation(
          targetLink: "target-pool",
          name: TARGET_POOL_OP_NAME,
          status: DONE)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [])
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def insertOp = new Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          region: REGION,
          ipProtocol: IP_PROTOCOL_UDP,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal
      0 * computeMock.httpHealthChecks()
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.insert(PROJECT_NAME, REGION, _) >> targetPoolsInsert
      1 * targetPoolsInsert.execute() >> targetPoolsInsertOp
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION,
        {it.IPAddress == null && it.IPProtocol == IP_PROTOCOL_UDP && it.portRange == Constants.DEFAULT_PORT_RANGE}) >>
        forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> insertOp

      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_OP_NAME) >> regionTargetPoolOperationGet
      1 * regionTargetPoolOperationGet.execute() >> targetPoolsInsertOp
  }

  void "should neither create anything new, nor edit anything existing, if a forwarding rule with the same name already exists"() {
    setup:
      def computeMock = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME
        )
      ])
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal
  }

  void "should update health check if specified properties differ from existing"() {
    setup:
      def computeMock = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
          new ForwardingRule(
            name: NETWORK_LOAD_BALANCER_NAME,
            target: TARGET_POOL_NAME,
            IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
            portRange: Constants.DEFAULT_PORT_RANGE
          )
        ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal =
        new TargetPoolList(items: [
          new TargetPool(
            name: TARGET_POOL_NAME,
            healthChecks: [HEALTH_CHECK_NAME]
          )
        ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
          new HttpHealthCheck(
            name: HEALTH_CHECK_NAME,
            checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
            healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
            unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
            port: Constants.DEFAULT_PORT,
            timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
            requestPath: Constants.DEFAULT_REQUEST_PATH
          )
        ])
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksUpdate = Mock(Compute.HttpHealthChecks.Update)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        healthCheck: [
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC + 1,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC + 1,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        ],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Update health check to reflect updated properties.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.update(PROJECT_NAME, HEALTH_CHECK_NAME, _) >> httpHealthChecksUpdate
      1 * httpHealthChecksUpdate.execute()
  }

  void "should neither create anything new, nor edit anything existing, if a forwarding rule, target pool and health check with the same properties already exist"() {
    setup:
      def computeMock = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        healthCheck: [
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        ],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal
  }

  void "should delete and recreate forwarding rule with same name if specified properties differ from existing"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: IP_PROTOCOL_UDP,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        targetLink: "delete-forwarding-rule",
        name: DELETE_FORWARDING_RULE_OP_NAME,
        status: DONE)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        portRange: PORT_RANGE,
        healthCheck: [
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        ],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Delete existing forwarding rule.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.delete(PROJECT_NAME, REGION, NETWORK_LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      // Poll for completion of forwarding rule delete.
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, DELETE_FORWARDING_RULE_OP_NAME) >> regionOperationsGet
      1 * regionOperationsGet.execute() >> forwardingRulesDeleteOp

      // Create new forwarding rule.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION,
        {it.name == NETWORK_LOAD_BALANCER_NAME && it.IPProtocol == IP_PROTOCOL_TCP && it.portRange == PORT_RANGE}) >>
        forwardingRulesInsert
      1 * forwardingRulesInsert.execute()
  }

  void "should add health check and update target pool if existing target pool does not have specified health check"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def regionAddHealthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME
        )
      ])
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
        targetLink: "health-check",
        name: HEALTH_CHECK_OP_NAME,
        status: DONE)
      def addHealthCheck = Mock(Compute.TargetPools.AddHealthCheck)
      def addHealthCheckOp = new Operation(
        targetLink: "add-health-check",
        name: ADD_HEALTH_CHECK_OP_NAME,
        status: DONE)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        healthCheck: [:],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Create new http health check.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME,
        {it.checkIntervalSec == Constants.DEFAULT_CHECK_INTERVAL_SEC &&
          it.healthyThreshold == Constants.DEFAULT_HEALTHY_THRESHOLD &&
          it.port == Constants.DEFAULT_PORT}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

      // Poll for completion of insert http health check.
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp

      // Update target pool to reflect new http health check.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.addHealthCheck(PROJECT_NAME, REGION, TARGET_POOL_NAME, _) >> addHealthCheck
      1 * addHealthCheck.execute() >> addHealthCheckOp

      // Poll for completion of target pool update.
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, ADD_HEALTH_CHECK_OP_NAME) >> regionAddHealthCheckOperationGet
      1 * regionAddHealthCheckOperationGet.execute() >> addHealthCheckOp
  }

  void "should remove health check and update target pool if existing target pool has health check and none is desired"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionRemoveHealthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def removeHealthCheck = Mock(Compute.TargetPools.RemoveHealthCheck)
      def removeHealthCheckOp = new Operation(
        targetLink: "remove-health-check",
        name: REMOVE_HEALTH_CHECK_OP_NAME,
        status: DONE)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Update target pool to disassociate http health check.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.removeHealthCheck(PROJECT_NAME, REGION, TARGET_POOL_NAME, _) >> removeHealthCheck
      1 * removeHealthCheck.execute() >> removeHealthCheckOp

      // Poll for completion of target pool update.
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, REMOVE_HEALTH_CHECK_OP_NAME) >> regionRemoveHealthCheckOperationGet
      1 * regionRemoveHealthCheckOperationGet.execute() >> removeHealthCheckOp

      // Delete extraneous http health check.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> httpHealthChecksDelete
      1 * httpHealthChecksDelete.execute()
  }

  void "should add missing instances to target pool"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [
            new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL),
            new Instance(name: INSTANCE_2, selfLink: INSTANCE_2_URL),
            new Instance(name: INSTANCE_3, selfLink: INSTANCE_3_URL)
          ]),
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          instances: [INSTANCE_2_URL],
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def targetPoolsAddInstance = Mock(Compute.TargetPools.AddInstance)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        instances: [INSTANCE_1, INSTANCE_2, INSTANCE_3],
        healthCheck: [:],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query instance urls to replace instance local names.
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Add missing instances to target pool.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.addInstance(PROJECT_NAME, REGION, TARGET_POOL_NAME,
        {it.instances.size == 2 &&
          it.instances[0].instance == INSTANCE_1_URL &&
          it.instances[1].instance == INSTANCE_3_URL}) >> targetPoolsAddInstance
      1 * targetPoolsAddInstance.execute()
  }

  void "should remove extraneous instances from target pool"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [
            new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL),
            new Instance(name: INSTANCE_2, selfLink: INSTANCE_2_URL),
            new Instance(name: INSTANCE_3, selfLink: INSTANCE_3_URL)
          ]),
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          instances: [INSTANCE_1_URL, INSTANCE_2_URL, INSTANCE_3_URL],
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def targetPoolsRemoveInstance = Mock(Compute.TargetPools.RemoveInstance)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        instances: [INSTANCE_3],
        healthCheck: [:],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query instance urls to replace instance local names.
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Remove extraneous instances from target pool.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.removeInstance(PROJECT_NAME, REGION, TARGET_POOL_NAME,
        {it.instances.size == 2 &&
          it.instances[0].instance == INSTANCE_1_URL &&
          it.instances[1].instance == INSTANCE_2_URL}) >> targetPoolsRemoveInstance
      1 * targetPoolsRemoveInstance.execute()
  }

  void "should both add missing instances to target pool and remove extraneous instances from target pool"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [
            new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL),
            new Instance(name: INSTANCE_2, selfLink: INSTANCE_2_URL),
            new Instance(name: INSTANCE_3, selfLink: INSTANCE_3_URL)
          ]),
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          instances: [INSTANCE_1_URL, INSTANCE_2_URL],
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def targetPoolsAddInstance = Mock(Compute.TargetPools.AddInstance)
      def targetPoolsRemoveInstance = Mock(Compute.TargetPools.RemoveInstance)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        instances: [INSTANCE_2, INSTANCE_3],
        healthCheck: [:],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query instance urls to replace instance local names.
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal

      // Add missing instances to target pool.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.addInstance(PROJECT_NAME, REGION, TARGET_POOL_NAME,
        {it.instances.size == 1 && it.instances[0].instance == INSTANCE_3_URL}) >> targetPoolsAddInstance
      1 * targetPoolsAddInstance.execute()

      // Remove extraneous instances from target pool.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.removeInstance(PROJECT_NAME, REGION, TARGET_POOL_NAME,
        {it.instances.size == 1 && it.instances[0].instance == INSTANCE_1_URL}) >> targetPoolsRemoveInstance
      1 * targetPoolsRemoveInstance.execute()
  }

  void "should neither add instances to target pool nor remove instances from target pool if specified set matches existing set"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/us-central1-a":
          new InstancesScopedList(instances: [
            new Instance(name: INSTANCE_1, selfLink: INSTANCE_1_URL),
            new Instance(name: INSTANCE_2, selfLink: INSTANCE_2_URL),
            new Instance(name: INSTANCE_3, selfLink: INSTANCE_3_URL)
          ]),
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesListReal = new ForwardingRuleList(items: [
        new ForwardingRule(
          name: NETWORK_LOAD_BALANCER_NAME,
          target: TARGET_POOL_NAME,
          IPProtocol: Constants.DEFAULT_IP_PROTOCOL,
          portRange: Constants.DEFAULT_PORT_RANGE
        )
      ])
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsList = Mock(Compute.TargetPools.List)
      def targetPoolsListReal = new TargetPoolList(items: [
        new TargetPool(
          name: TARGET_POOL_NAME,
          instances: [INSTANCE_1_URL, INSTANCE_2_URL, INSTANCE_3_URL],
          healthChecks: [HEALTH_CHECK_NAME]
        )
      ])
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def httpHealthChecksListReal = new HttpHealthCheckList(items: [
        new HttpHealthCheck(
          name: HEALTH_CHECK_NAME,
          checkIntervalSec: Constants.DEFAULT_CHECK_INTERVAL_SEC,
          healthyThreshold: Constants.DEFAULT_HEALTHY_THRESHOLD,
          unhealthyThreshold: Constants.DEFAULT_UNHEALTHY_THRESHOLD,
          port: Constants.DEFAULT_PORT,
          timeoutSec: Constants.DEFAULT_TIMEOUT_SEC,
          requestPath: Constants.DEFAULT_REQUEST_PATH
        )
      ])
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
        region: REGION,
        instances: [INSTANCE_1, INSTANCE_2, INSTANCE_3],
        healthCheck: [:],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleNetworkLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      // Query instance urls to replace instance local names.
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      // Query existing forwarding rules.
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> forwardingRulesListReal

      // Query existing target pools.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.list(PROJECT_NAME, REGION) >> targetPoolsList
      1 * targetPoolsList.execute() >> targetPoolsListReal

      // Query existing health checks.
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> httpHealthChecksListReal
  }
}
