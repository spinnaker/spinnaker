/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.HTTPHealthCheck
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.Operation
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.converters.UpsertGoogleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.UpsertGoogleHttpLoadBalancerTestConstants.*

class UpsertGoogleSslLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final PROXY_OP_NAME = "proxy-op"
  private static final FORWARDING_RULE_OP_NAME = "forwarding-rule-op"
  private static final CERT = "ye-olde-cert"
  private static final DONE = "DONE"

  @Shared GoogleHealthCheck hc
  @Shared def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared def registry = new DefaultRegistry()
  @Shared SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    hc = [
      "name"              : "basic-check",
      "healthCheckType"   : "HTTP",
      "requestPath"       : "/",
      "port"              : 80,
      "checkIntervalSec"  : 1,
      "timeoutSec"        : 1,
      "healthyThreshold"  : 1,
      "unhealthyThreshold": 1
    ]
    safeRetry = SafeRetry.withoutDelay()
  }

  void "should create ssl load balancer if no infrastructure present."() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalOperationGet = Mock(Compute.GlobalOperations.Get)

    def forwardingRules = Mock(Compute.GlobalForwardingRules)
    def forwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
    def forwardingRuleOp = new Operation(
      targetLink: LOAD_BALANCER_NAME,
      name: FORWARDING_RULE_OP_NAME,
      status: DONE
    )

    def targetProxies = Mock(Compute.TargetSslProxies)
    def targetProxiesInsert = Mock(Compute.TargetSslProxies.Insert)
    def proxyOp = new Operation(
      targetLink: "",
      name: PROXY_OP_NAME,
      status: DONE
    )

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.BackendServices)
    def backendServicesGet = Mock(Compute.BackendServices.Get)
    def backendServicesInsert = Mock(Compute.BackendServices.Insert)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      "loadBalancerName": LOAD_BALANCER_NAME,
      "portRange"       : PORT_RANGE,
      "region"          : 'global',
      "backendService"  : [
        "name"           : DEFAULT_SERVICE,
        "backends"       : [],
        "healthCheck"    : hc,
        "sessionAffinity": "NONE",
      ],
      "certificate"     : CERT,
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleSslLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
    operation.operate([])

    then:
    2 * computeMock.globalForwardingRules() >> forwardingRules
    1 * forwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> forwardingRulesGet
    1 * forwardingRulesGet.execute() >> null
    1 * forwardingRules.insert(PROJECT_NAME, _) >> forwardingRulesInsert
    1 * forwardingRulesInsert.execute() >> forwardingRuleOp

    1 * computeMock.targetSslProxies() >> targetProxies
    1 * targetProxies.insert(PROJECT_NAME, _) >> targetProxiesInsert
    1 * targetProxiesInsert.execute() >> proxyOp

    2 * computeMock.healthChecks() >> healthChecks
    1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
    1 * healthChecksGet.execute() >> null
    1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
    1 * healthChecksInsert.execute() >> healthChecksInsertOp

    2 * computeMock.backendServices() >> backendServices
    1 * backendServices.get(PROJECT_NAME, DEFAULT_SERVICE) >> backendServicesGet
    1 * backendServicesGet.execute() >> null
    1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
    1 * backendServicesInsert.execute() >> backendServicesInsertOp

    4 * computeMock.globalOperations() >> globalOperations
    1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> healthChecksInsertOp
    1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> backendServicesInsertOp
    1 * globalOperations.get(PROJECT_NAME, PROXY_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> proxyOp
    1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> forwardingRuleOp
  }

  void "should update backend service if it exists"() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalOperationGet = Mock(Compute.GlobalOperations.Get)

    def forwardingRules = Mock(Compute.GlobalForwardingRules)
    def forwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
    def forwardingRuleOp = new Operation(
      targetLink: LOAD_BALANCER_NAME,
      name: FORWARDING_RULE_OP_NAME,
      status: DONE
    )

    def targetProxies = Mock(Compute.TargetSslProxies)
    def targetProxiesInsert = Mock(Compute.TargetSslProxies.Insert)
    def proxyOp = new Operation(
      targetLink: "",
      name: PROXY_OP_NAME,
      status: DONE
    )

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.BackendServices)
    def backendServicesGet = Mock(Compute.BackendServices.Get)
    def backendServicesUpdate = Mock(Compute.BackendServices.Update)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      "loadBalancerName": LOAD_BALANCER_NAME,
      "portRange"       : PORT_RANGE,
      "region"          : 'global',
      "backendService"  : [
        "name"           : DEFAULT_SERVICE,
        "backends"       : [],
        "healthCheck"    : hc,
        "sessionAffinity": "NONE",
      ],
      "certificate"     : CERT,
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleSslLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
    operation.operate([])

    then:
    2 * computeMock.globalForwardingRules() >> forwardingRules
    1 * forwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> forwardingRulesGet
    1 * forwardingRulesGet.execute() >> null
    1 * forwardingRules.insert(PROJECT_NAME, _) >> forwardingRulesInsert
    1 * forwardingRulesInsert.execute() >> forwardingRuleOp

    1 * computeMock.targetSslProxies() >> targetProxies
    1 * targetProxies.insert(PROJECT_NAME, _) >> targetProxiesInsert
    1 * targetProxiesInsert.execute() >> proxyOp

    2 * computeMock.healthChecks() >> healthChecks
    1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
    1 * healthChecksGet.execute() >> null
    1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
    1 * healthChecksInsert.execute() >> healthChecksInsertOp

    2 * computeMock.backendServices() >> backendServices
    1 * backendServices.get(PROJECT_NAME, DEFAULT_SERVICE) >> backendServicesGet
    1 * backendServicesGet.execute() >> new BackendService(name: DEFAULT_SERVICE, healthChecks: [], sessionAffinity: 'NONE')
    1 * backendServices.update(PROJECT_NAME, _, _) >> backendServicesUpdate
    1 * backendServicesUpdate.execute() >> backendServicesInsertOp

    4 * computeMock.globalOperations() >> globalOperations
    1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> healthChecksInsertOp
    1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> backendServicesInsertOp
    1 * globalOperations.get(PROJECT_NAME, PROXY_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> proxyOp
    1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> forwardingRuleOp
  }

  void "should update health check if it exists"() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalOperationGet = Mock(Compute.GlobalOperations.Get)

    def forwardingRules = Mock(Compute.GlobalForwardingRules)
    def forwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
    def forwardingRuleOp = new Operation(
      targetLink: LOAD_BALANCER_NAME,
      name: FORWARDING_RULE_OP_NAME,
      status: DONE
    )

    def targetProxies = Mock(Compute.TargetSslProxies)
    def targetProxiesInsert = Mock(Compute.TargetSslProxies.Insert)
    def proxyOp = new Operation(
      targetLink: "",
      name: PROXY_OP_NAME,
      status: DONE
    )

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksUpdate = Mock(Compute.HealthChecks.Update)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.BackendServices)
    def backendServicesGet = Mock(Compute.BackendServices.Get)
    def backendServicesUpdate = Mock(Compute.BackendServices.Update)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      "loadBalancerName": LOAD_BALANCER_NAME,
      "portRange"       : PORT_RANGE,
      "region"          : 'global',
      "backendService"  : [
        "name"           : DEFAULT_SERVICE,
        "backends"       : [],
        "healthCheck"    : hc,
        "sessionAffinity": "NONE",
      ],
      "certificate"     : CERT,
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleSslLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
    operation.operate([])

    then:
    2 * computeMock.globalForwardingRules() >> forwardingRules
    1 * forwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> forwardingRulesGet
    1 * forwardingRulesGet.execute() >> null
    1 * forwardingRules.insert(PROJECT_NAME, _) >> forwardingRulesInsert
    1 * forwardingRulesInsert.execute() >> forwardingRuleOp

    1 * computeMock.targetSslProxies() >> targetProxies
    1 * targetProxies.insert(PROJECT_NAME, _) >> targetProxiesInsert
    1 * targetProxiesInsert.execute() >> proxyOp

    2 * computeMock.healthChecks() >> healthChecks
    1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
    1 * healthChecksGet.execute() >> new HealthCheck(name: 'basic-check', checkIntervalSec: 11, httpHealthCheck: new HTTPHealthCheck(port: 80, requestPath: '/'))
    1 * healthChecks.update(PROJECT_NAME, _, _) >> healthChecksUpdate
    1 * healthChecksUpdate.execute() >> healthChecksInsertOp

    2 * computeMock.backendServices() >> backendServices
    1 * backendServices.get(PROJECT_NAME, DEFAULT_SERVICE) >> backendServicesGet
    1 * backendServicesGet.execute() >> new BackendService(name: DEFAULT_SERVICE, healthChecks: [], sessionAffinity: 'NONE')
    1 * backendServices.update(PROJECT_NAME, _, _) >> backendServicesUpdate
    1 * backendServicesUpdate.execute() >> backendServicesInsertOp

    4 * computeMock.globalOperations() >> globalOperations
    1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> healthChecksInsertOp
    1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> backendServicesInsertOp
    1 * globalOperations.get(PROJECT_NAME, PROXY_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> proxyOp
    1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_OP_NAME) >> globalOperationGet
    1 * globalOperationGet.execute() >> forwardingRuleOp
  }
}
