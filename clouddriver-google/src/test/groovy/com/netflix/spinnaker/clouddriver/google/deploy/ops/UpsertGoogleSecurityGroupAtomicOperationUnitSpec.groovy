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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting
import com.google.api.client.testing.json.MockJsonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Firewall
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleSecurityGroupAtomicOperationUnitSpec extends Specification {
  private static final SECURITY_GROUP_NAME = "spinnaker-sg-1"
  private static final DESCRIPTION = "Some firewall description..."
  private static final NETWORK_NAME = "default"
  private static final SOURCE_RANGE = "192.0.0.0/8"
  private static final SOURCE_TAG = "some-source-tag"
  private static final IP_PROTOCOL = "tcp"
  private static final PORT_RANGE = "8070-8080"
  private static final TARGET_TAG = "some-target-tag"
  private static final ORIG_TARGET_TAG = "some-other-target-tag"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should insert new firewall rule with generated target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewallsInsertMock = Mock(Compute.Firewalls.Insert)
      GoogleJsonResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleSecurityGroupDescription(
          securityGroupName: SECURITY_GROUP_NAME,
          description: DESCRIPTION,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          accountName: ACCOUNT_NAME,
          credentials: credentials
      )
      @Subject def operation = new UpsertGoogleSecurityGroupAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> {throw notFoundException}

      // Insert the new firewall rule.
      1 * firewallsMock.insert(PROJECT_NAME, {
          it.name == SECURITY_GROUP_NAME &&
          it.description == DESCRIPTION &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags?.get(0).startsWith("$SECURITY_GROUP_NAME-")
      }) >> firewallsInsertMock
  }

  void "should insert new firewall rule with specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewallsInsertMock = Mock(Compute.Firewalls.Insert)
      GoogleJsonResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleSecurityGroupDescription(
          securityGroupName: SECURITY_GROUP_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          targetTags: [TARGET_TAG],
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleSecurityGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> {throw notFoundException}

      // Insert the new firewall rule.
      1 * firewallsMock.insert(PROJECT_NAME, {
          it.name == SECURITY_GROUP_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsInsertMock
  }

  void "should update existing firewall rule and leave target tag unset"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: SECURITY_GROUP_NAME)
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleSecurityGroupDescription(securityGroupName: SECURITY_GROUP_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleSecurityGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, SECURITY_GROUP_NAME, {
          it.name == SECURITY_GROUP_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          !it.targetTags
      }) >> firewallsUpdateMock
  }

  void "should update existing firewall rule and set specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: SECURITY_GROUP_NAME)
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleSecurityGroupDescription(securityGroupName: SECURITY_GROUP_NAME,
        network: NETWORK_NAME,
        sourceRanges: [SOURCE_RANGE],
        sourceTags: [SOURCE_TAG],
        allowed: [
            [
                ipProtocol: IP_PROTOCOL,
                portRanges: [PORT_RANGE]
            ]
        ],
        targetTags: [TARGET_TAG],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleSecurityGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, SECURITY_GROUP_NAME, {
        it.name == SECURITY_GROUP_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsUpdateMock
  }

  void "should update existing firewall rule and override existing target tag with specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: SECURITY_GROUP_NAME, targetTags: [ORIG_TARGET_TAG])
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleSecurityGroupDescription(securityGroupName: SECURITY_GROUP_NAME,
        network: NETWORK_NAME,
        sourceRanges: [SOURCE_RANGE],
        sourceTags: [SOURCE_TAG],
        allowed: [
            [
                ipProtocol: IP_PROTOCOL,
                portRanges: [PORT_RANGE]
            ]
        ],
        targetTags: [TARGET_TAG],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleSecurityGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, SECURITY_GROUP_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, SECURITY_GROUP_NAME, {
        it.name == SECURITY_GROUP_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsUpdateMock
  }
}
