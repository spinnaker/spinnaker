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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Operation
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleSecurityGroupAtomicOperationUnitSpec extends Specification {
  private static final SECURITY_GROUP_NAME = "spinnaker-test-sg"
  private static final ACCOUNT_NAME = "prod"
  private static final PROJECT_NAME = "my-project"
  private static final DELETE_FIREWALL_OP_NAME = "delete-firewall-op"
  private static final DONE = "DONE"
  @Shared
  SafeRetry safeRetry

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0, jitterMultiplier: 0)
  }

  void "should delete firewall rule"() {
    setup:
      def registry = new DefaultRegistry()
      def computeMock = Mock(Compute)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsDelete = Mock(Compute.Firewalls.Delete)
      def globalOperationsMock = Mock(Compute.GlobalOperations)
      def globalOperationsGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleSecurityGroupDescription(securityGroupName: SECURITY_GROUP_NAME,
                                                                 accountName: ACCOUNT_NAME,
                                                                 credentials: credentials)
      def firewallsDeleteOperation = new Operation(targetLink: "firewall",
        name: DELETE_FIREWALL_OP_NAME,
        status: DONE)

      @Subject def operation = new DeleteGoogleSecurityGroupAtomicOperation(description)
      operation.registry = registry
      operation.safeRetry = safeRetry
      operation.googleOperationPoller = new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry)

    when:
      operation.operate([])

    then:
      1 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.delete(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsDelete
      1 * firewallsDelete.execute() >> firewallsDeleteOperation

      1 * computeMock.globalOperations() >> globalOperationsMock
      1 * globalOperationsMock.get(PROJECT_NAME, DELETE_FIREWALL_OP_NAME) >> globalOperationsGet
      1 * globalOperationsGet.execute() >> firewallsDeleteOperation
  }
}
