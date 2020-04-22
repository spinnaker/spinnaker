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

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleInternalLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REGION = "us-central1"
  private static final INTERNAL = "INTERNAL"
  private static final LOAD_BALANCER_NAME = "default"
  private static final FORWARDING_RULE_DELETE_OP_NAME = "delete-forwarding-rule"
  private static final BS_DELETE_OP = "delete-bs"
  private static final BS_NAME = "bs"
  private static final BS_URL = "/projects/$PROJECT_NAME/region/$REGION/backendServices/$BS_NAME"
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final HTTP_HC_URL = "/projects/$PROJECT_NAME/global/httpHealthChecks/$HEALTH_CHECK_NAME"
  private static final HTTPS_HC_URL = "/projects/$PROJECT_NAME/global/httpsHealthChecks/$HEALTH_CHECK_NAME"
  private static final HC_URL = "/projects/$PROJECT_NAME/global/healthChecks/$HEALTH_CHECK_NAME"
  private static final HEALTH_CHECK_DELETE_OP_NAME = "delete-health-check"

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared
  def registry = new DefaultRegistry()
  @Shared
  SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = SafeRetry.withoutDelay()
  }

  void "should delete an Internal Load Balancer with http health check"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def healthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: "DONE")
      def forwardingRule = new ForwardingRule(backendService: BS_URL, name: LOAD_BALANCER_NAME, region: REGION)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
        name: BS_DELETE_OP,
        status: "DONE")
      def backendService = new BackendService(
        name: BS_NAME,
        healthChecks: [HTTP_HC_URL]
      )

      def healthChecks = Mock(Compute.HttpHealthChecks)
      def healthChecksGet = Mock(Compute.HttpHealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: "DONE")
      def healthCheck = new HttpHealthCheck(name: HEALTH_CHECK_NAME)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BS_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.delete(PROJECT_NAME, REGION, BS_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp

      2 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BS_DELETE_OP) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesDeleteOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete an Internal Load Balancer with https health check"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def healthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: "DONE")
      def forwardingRule = new ForwardingRule(backendService: BS_URL, name: LOAD_BALANCER_NAME, region: REGION)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
        name: BS_DELETE_OP,
        status: "DONE")
      def backendService = new BackendService(
        name: BS_NAME,
        healthChecks: [HTTPS_HC_URL]
      )

      def healthChecks = Mock(Compute.HttpsHealthChecks)
      def healthChecksGet = Mock(Compute.HttpsHealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HttpsHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: "DONE")
      def healthCheck = new HttpsHealthCheck(name: HEALTH_CHECK_NAME)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BS_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.delete(PROJECT_NAME, REGION, BS_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp

      2 * computeMock.httpsHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BS_DELETE_OP) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesDeleteOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete an Internal Load Balancer with non-http(s) health check"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def healthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: "DONE")
      def forwardingRule = new ForwardingRule(backendService: BS_URL, name: LOAD_BALANCER_NAME, region: REGION)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
        name: BS_DELETE_OP,
        status: "DONE")
      def backendService = new BackendService(
        name: BS_NAME,
        healthChecks: [HC_URL]
      )

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksGet = Mock(Compute.HealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: "DONE")
      def healthCheck = new HealthCheck(name: HEALTH_CHECK_NAME)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BS_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.delete(PROJECT_NAME, REGION, BS_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp

      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BS_DELETE_OP) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesDeleteOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete a Internal Load Balancer with shared http health check"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: "DONE")
      def forwardingRule = new ForwardingRule(backendService: BS_URL, name: LOAD_BALANCER_NAME, region: REGION)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
        name: BS_DELETE_OP,
        status: "DONE")
      def backendService = new BackendService(
        name: BS_NAME,
        healthChecks: [HTTP_HC_URL]
      )

      def healthChecks = Mock(Compute.HttpHealthChecks)
      def healthChecksGet = Mock(Compute.HttpHealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthCheck = new HttpHealthCheck(name: HEALTH_CHECK_NAME)

      // Create HC in use exception.
      def errorMessage = "The resource '$HEALTH_CHECK_NAME' is already in use by another resource."
      def errorInfo = new GoogleJsonError.ErrorInfo(
        domain: "global",
        message: errorMessage,
        reason: "resourceInUseByAnotherResource")
      def details = new GoogleJsonError(
        code: 400,
        errors: [errorInfo],
        message: errorMessage)
      def httpResponseExceptionBuilder = new HttpResponseException.Builder(
        400,
        "Bad Request",
        new HttpHeaders()).setMessage("400 Bad Request")
      def googleJsonResponseException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BS_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.delete(PROJECT_NAME, REGION, BS_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp

      11 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck
      10 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      10 * healthChecksDelete.execute() >> { throw googleJsonResponseException }

      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BS_DELETE_OP) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesDeleteOp
  }

  void "should delete an Internal Load Balancer with shared http health check"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: "DONE")
      def forwardingRule = new ForwardingRule(backendService: BS_URL, name: LOAD_BALANCER_NAME, region: REGION)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendService = new BackendService(
        name: BS_NAME,
        healthChecks: [HTTP_HC_URL]
      )

      def healthChecks = Mock(Compute.HttpHealthChecks)
      def healthChecksGet = Mock(Compute.HttpHealthChecks.Get)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: "DONE")
      def healthCheck = new HttpHealthCheck(name: HEALTH_CHECK_NAME)

      // Create HC in use exception.
      def errorMessage = "The resource '$HEALTH_CHECK_NAME' is already in use by another resource."
      def errorInfo = new GoogleJsonError.ErrorInfo(
        domain: "global",
        message: errorMessage,
        reason: "resourceInUseByAnotherResource")
      def details = new GoogleJsonError(
        code: 400,
        errors: [errorInfo],
        message: errorMessage)
      def httpResponseExceptionBuilder = new HttpResponseException.Builder(
        400,
        "Bad Request",
        new HttpHeaders()).setMessage("400 Bad Request")
      def googleJsonResponseException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp

      11 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BS_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      10 * backendServices.delete(PROJECT_NAME, REGION, BS_NAME) >> backendServicesDelete
      10 * backendServicesDelete.execute() >> { throw googleJsonResponseException }

      11 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksGet
      1 * healthChecksGet.execute() >> healthCheck
      10 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      10 * healthChecksDelete.execute() >> { throw googleJsonResponseException } // If we can't delete the BS, we can't delete HC it's using.

      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
  }

  void "should fail to delete an Internal Load Balancer when forwarding rule can't be found"() {
    setup:
      def computeMock = Mock(Compute)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        loadBalancerType: INTERNAL,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller = new GoogleOperationPoller(
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
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> [items: []]
      thrown GoogleResourceNotFoundException
  }
}
