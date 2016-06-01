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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.DeleteGoogleLoadBalancerAtomicOperation.MAX_NUM_TARGET_POOL_RETRIES
import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.DeleteGoogleLoadBalancerAtomicOperation.TARGET_POOL_RETRY_INTERVAL_SECONDS

class DeleteGoogleLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final LOAD_BALANCER_NAME = "default"
  private static final REGION = "us-central1"
  private static final FORWARDING_RULE_DELETE_OP_NAME = "delete-forwarding-rule"
  private static final TARGET_POOL_URL = "project/target-pool"
  private static final TARGET_POOL_NAME = "target-pool"
  private static final TARGET_POOL_DELETE_OP_NAME = "delete-target-pool"
  private static final HEALTH_CHECK_URL = "project/health-check"
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final HEALTH_CHECK_DELETE_OP_NAME = "delete-health-check"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete a Network Load Balancer with health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def globalOperations = Mock(Compute.GlobalOperations)
      def healthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPoolsDelete = Mock(Compute.TargetPools.Delete)
      def targetPoolsDeleteOp = new Operation(
          name: TARGET_POOL_DELETE_OP_NAME,
          status: "DONE")
      def targetPool = new TargetPool(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: "DONE")
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      1 * computeMock.httpHealthChecks() >> healthChecks
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp
      1 * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      1 * targetPoolsDelete.execute() >> targetPoolsDeleteOp
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp
      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_DELETE_OP_NAME) >> targetPoolOperationGet
      1 * targetPoolOperationGet.execute() >> targetPoolsDeleteOp
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete a Network Load Balancer even if it lacks any health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPoolsDelete = Mock(Compute.TargetPools.Delete)
      def targetPoolsDeleteOp = new Operation(
          name: TARGET_POOL_DELETE_OP_NAME,
          status: "DONE")
      def targetPool = new TargetPool()
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      0 * computeMock.httpHealthChecks()
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp
      1 * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      1 * targetPoolsDelete.execute() >> targetPoolsDeleteOp
      2 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_DELETE_OP_NAME) >> targetPoolOperationGet
      1 * targetPoolOperationGet.execute() >> targetPoolsDeleteOp
  }

  void "should fail to delete a Network Load Balancer that does not exist"() {
    setup:
      def computeMock = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> null
      thrown GoogleResourceNotFoundException
  }

  void "should fail if failed to delete a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesPendingDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "PENDING")
      def forwardingRulesFailingDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE",
          error: new Operation.Error(errors: [new Operation.Error.Errors(message: "error")]))
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPool = new TargetPool()
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesPendingDeleteOp
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesFailingDeleteOp
      thrown GoogleOperationException
  }

  void "should fail if timed out while deleting a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesPendingDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "PENDING")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPool = new TargetPool()
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          deleteOperationTimeoutSeconds: 0,
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesPendingDeleteOp
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesPendingDeleteOp
      thrown GoogleOperationTimedOutException
  }

  void "should wait on slow deletion of forwarding rule and successfully delete Network Load Balancer"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOpPending = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "PENDING")
      def forwardingRulesDeleteOpDone = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPoolsDelete = Mock(Compute.TargetPools.Delete)
      def targetPoolsDeleteOp = new Operation(
          name: TARGET_POOL_DELETE_OP_NAME,
          status: "DONE")
      def targetPool = new TargetPool()
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      0 * computeMock.httpHealthChecks()
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOpPending
      1 * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      1 * targetPoolsDelete.execute() >> targetPoolsDeleteOp
      4 * computeMock.regionOperations() >> regionOperations
      3 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      2 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOpPending
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOpDone
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_DELETE_OP_NAME) >> targetPoolOperationGet
      1 * targetPoolOperationGet.execute() >> targetPoolsDeleteOp
  }

  void "should retry deletion of target pool when 400/resourceNotReady is returned and should eventually succeed"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def targetPoolOperationGet = Mock(Compute.RegionOperations.Get)
      def globalOperations = Mock(Compute.GlobalOperations)
      def healthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPoolsDelete = Mock(Compute.TargetPools.Delete)
      def targetPoolsDeleteOp = new Operation(
          name: TARGET_POOL_DELETE_OP_NAME,
          status: "DONE")
      def targetPool = new TargetPool(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: "DONE")
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      def errorMessage = "The resource '$TARGET_POOL_URL' is not ready"
      def errorInfo = new GoogleJsonError.ErrorInfo(
          domain: "global",
          message: errorMessage,
          reason: "resourceNotReady")
      def details = new GoogleJsonError(
          code: 400,
          errors: [errorInfo],
          message: errorMessage)
      def httpResponseExceptionBuilder = new HttpResponseException.Builder(
          400,
          "Bad Request",
          new HttpHeaders()).setMessage("400 Bad Request")
      def googleJsonResponseException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)
      def threadSleeperMock = Mock(DeleteGoogleLoadBalancerAtomicOperation.ThreadSleeper)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.threadSleeper = threadSleeperMock
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp

    then:
      // Throw "400/resourceNotReady" 4 times.
      4 * computeMock.targetPools() >> targetPools
      4 * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      4 * targetPoolsDelete.execute() >> { throw googleJsonResponseException }
      4 * threadSleeperMock.sleep(TARGET_POOL_RETRY_INTERVAL_SECONDS)

    then:
      // Now succeed.
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      1 * targetPoolsDelete.execute() >> targetPoolsDeleteOp
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_POOL_DELETE_OP_NAME) >> targetPoolOperationGet
      1 * targetPoolOperationGet.execute() >> targetPoolsDeleteOp

    then:
      1 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should retry deletion of target pool when 400/resourceNotReady is returned and should eventually give up"() {
    setup:
      def computeMock = Mock(Compute)
      def regionOperations = Mock(Compute.RegionOperations)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: "DONE")
      def forwardingRule = new ForwardingRule(target: TARGET_POOL_URL)
      def targetPools = Mock(Compute.TargetPools)
      def targetPoolsGet = Mock(Compute.TargetPools.Get)
      def targetPoolsDelete = Mock(Compute.TargetPools.Delete)
      def targetPool = new TargetPool(healthChecks: [HEALTH_CHECK_URL])
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      def errorMessage = "The resource '$TARGET_POOL_URL' is not ready"
      def errorInfo = new GoogleJsonError.ErrorInfo(
          domain: "global",
          message: errorMessage,
          reason: "resourceNotReady")
      def details = new GoogleJsonError(
          code: 400,
          errors: [errorInfo],
          message: errorMessage)
      def httpResponseExceptionBuilder = new HttpResponseException.Builder(
          400,
          "Bad Request",
          new HttpHeaders()).setMessage("400 Bad Request")
      def googleJsonResponseException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)
      def threadSleeperMock = Mock(DeleteGoogleLoadBalancerAtomicOperation.ThreadSleeper)
      @Subject def operation = new DeleteGoogleLoadBalancerAtomicOperation(description)
      operation.threadSleeper = threadSleeperMock
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      1 * computeMock.targetPools() >> targetPools
      1 * targetPools.get(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsGet
      1 * targetPoolsGet.execute() >> targetPool
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> forwardingRulesDeleteOp
      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, FORWARDING_RULE_DELETE_OP_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRulesDeleteOp

    then:
      // Throw "400/resourceNotReady" MAX_NUM_TARGET_POOL_RETRIES times and sleep all but the last iteration.
      MAX_NUM_TARGET_POOL_RETRIES * computeMock.targetPools() >> targetPools
      MAX_NUM_TARGET_POOL_RETRIES * targetPools.delete(PROJECT_NAME, REGION, TARGET_POOL_NAME) >> targetPoolsDelete
      MAX_NUM_TARGET_POOL_RETRIES * targetPoolsDelete.execute() >> { throw googleJsonResponseException }
      (MAX_NUM_TARGET_POOL_RETRIES - 1) * threadSleeperMock.sleep(TARGET_POOL_RETRY_INTERVAL_SECONDS)

    then:
      // Then give up and propagate the final exception.
      thrown GoogleJsonResponseException
  }
}
