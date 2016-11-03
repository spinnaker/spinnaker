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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleSslLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SSL_LOAD_BALANCER_NAME = "default"
  private static final TARGET_SSL_PROXY_URL = "projects/$PROJECT_NAME/global/targetSslProxies/target-ssl-proxy"
  private static final TARGET_SSL_PROXY_NAME = "target-ssl-proxy"
  private static final BACKEND_SERVICE_URL = "project/backend-service"
  private static final BACKEND_SERVICE_NAME = "backend-service"
  private static final HEALTH_CHECK_URL = "project/health-check"
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final FORWARDING_RULE_DELETE_OP_NAME = "delete-forwarding-rule"
  private static final TARGET_SSL_PROXY_DELETE_OP_NAME = "delete-target-ssl-proxy"
  private static final BACKEND_SERVICE_DELETE_OP_NAME = "delete-backend-service"
  private static final HEALTH_CHECK_DELETE_OP_NAME = "delete-health-check"
  private static final PENDING = "PENDING"
  private static final DONE = "DONE"

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    SafeRetry.SAFE_RETRY_INTERVAL_MILLIS = 1
  }

  void "should delete ssl load balancer"() {
    setup:
      def computeMock = Mock(Compute)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def forwardingRule = new ForwardingRule(target: TARGET_SSL_PROXY_URL, name: SSL_LOAD_BALANCER_NAME)

      def targetSslProxies = Mock(Compute.TargetSslProxies)
      def targetSslProxiesGet = Mock(Compute.TargetSslProxies.Get)
      def targetSslProxiesDel = Mock(Compute.TargetSslProxies.Delete)
      def targetSslProxy = new TargetSslProxy(service: BACKEND_SERVICE_URL)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendServicesDelete = Mock(Compute.BackendServices.Delete)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksGet = Mock(Compute.HealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HealthChecks.Delete)
      def healthCheck = new HealthCheck(name: HEALTH_CHECK_NAME)

      def globalForwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: DONE)
      def targetSslProxiesDeleteOp = new Operation(
        name: TARGET_SSL_PROXY_DELETE_OP_NAME,
        status: DONE)
      def backendServicesDeleteOp = new Operation(
        name: BACKEND_SERVICE_DELETE_OP_NAME,
        status: DONE)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: DONE)

      def globalOperations = Mock(Compute.GlobalOperations)
      def targetSslProxiesOperationGet = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet = Mock(Compute.GlobalOperations.Get)
      def healthChecksOperationGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: SSL_LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleSslLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock)

    when:
      operation.operate([])

    then:
      3 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      1 * globalForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * globalForwardingRules.get(PROJECT_NAME, SSL_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule

      3 * computeMock.targetSslProxies() >> targetSslProxies
      2 * targetSslProxies.get(PROJECT_NAME, TARGET_SSL_PROXY_NAME) >> targetSslProxiesGet
      2 * targetSslProxiesGet.execute() >> targetSslProxy

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck

      1 * globalForwardingRules.delete(PROJECT_NAME, SSL_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesDeleteOp

      1 * targetSslProxies.delete(PROJECT_NAME, TARGET_SSL_PROXY_NAME) >> targetSslProxiesDel
      1 * targetSslProxiesDel.execute() >> targetSslProxiesDeleteOp

      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp

      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      3 * computeMock.globalOperations() >> globalOperations

      1 * globalOperations.get(PROJECT_NAME, TARGET_SSL_PROXY_DELETE_OP_NAME) >> targetSslProxiesOperationGet
      1 * targetSslProxiesOperationGet.execute() >> targetSslProxiesDeleteOp

      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp

      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should fail to delete ssl load balancer that doesn't exist"() {
    setup:
      def computeMock = Mock(Compute)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: SSL_LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleSslLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock)

    when:
      operation.operate([])

    then:
      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      1 * globalForwardingRulesList.execute() >> [items: []]
      thrown GoogleResourceNotFoundException
  }

  void "should fail to delete a ssl load balancer if we fail to delete a resource"() {
    setup:
      def computeMock = Mock(Compute)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def forwardingRule = new ForwardingRule(target: TARGET_SSL_PROXY_URL, name: SSL_LOAD_BALANCER_NAME)

      def targetSslProxies = Mock(Compute.TargetSslProxies)
      def targetSslProxiesGet = Mock(Compute.TargetSslProxies.Get)
      def targetSslProxiesDel = Mock(Compute.TargetSslProxies.Delete)
      def targetSslProxy = new TargetSslProxy(service: BACKEND_SERVICE_URL)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksGet = Mock(Compute.HealthChecks.Get)
      def healthCheck = new HealthCheck(name: HEALTH_CHECK_NAME)

      def globalForwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: DONE)
      def targetSslProxiesDeleteOp = new Operation(
        name: TARGET_SSL_PROXY_DELETE_OP_NAME,
        status: PENDING)
      GCEUtil.deleteGlobalListener(computeMock, PROJECT_NAME, SSL_LOAD_BALANCER_NAME) >> targetSslProxiesDeleteOp

      def globalOperations = Mock(Compute.GlobalOperations)
      def targetSslProxiesOperationGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        deleteOperationTimeoutSeconds: 0,
        loadBalancerName: SSL_LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleSslLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock)

    when:
      operation.operate([])

    then:
      3 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      1 * globalForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * globalForwardingRules.get(PROJECT_NAME, SSL_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule

      3 * computeMock.targetSslProxies() >> targetSslProxies
      2 * targetSslProxies.get(PROJECT_NAME, TARGET_SSL_PROXY_NAME) >> targetSslProxiesGet
      2 * targetSslProxiesGet.execute() >> targetSslProxy

      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck

      1 * globalForwardingRules.delete(PROJECT_NAME, SSL_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesDeleteOp

      1 * targetSslProxies.delete(PROJECT_NAME, TARGET_SSL_PROXY_NAME) >> targetSslProxiesDel
      1 * targetSslProxiesDel.execute() >> targetSslProxiesDeleteOp

      1 * computeMock.globalOperations() >> globalOperations

      1 * globalOperations.get(PROJECT_NAME, TARGET_SSL_PROXY_DELETE_OP_NAME) >> targetSslProxiesOperationGet
      1 * targetSslProxiesOperationGet.execute() >> targetSslProxiesDeleteOp
      thrown GoogleOperationTimedOutException
  }

  void "should fail if server group still attached"() {
    setup:
      def computeMock = Mock(Compute)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
      def forwardingRule = new ForwardingRule(target: TARGET_SSL_PROXY_URL, name: SSL_LOAD_BALANCER_NAME)

      def targetSslProxies = Mock(Compute.TargetSslProxies)
      def targetSslProxiesGet = Mock(Compute.TargetSslProxies.Get)
      def targetSslProxy = new TargetSslProxy(service: BACKEND_SERVICE_URL)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL], backends: [new Backend()])

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: SSL_LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleSslLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock)

    when:
      operation.operate([])

    then:
      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      1 * globalForwardingRulesList.execute() >> [items: [forwardingRule]]

      2 * computeMock.targetSslProxies() >> targetSslProxies
      2 * targetSslProxies.get(PROJECT_NAME, TARGET_SSL_PROXY_NAME) >> targetSslProxiesGet
      2 * targetSslProxiesGet.execute() >> targetSslProxy

      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      thrown IllegalStateException
  }
}
