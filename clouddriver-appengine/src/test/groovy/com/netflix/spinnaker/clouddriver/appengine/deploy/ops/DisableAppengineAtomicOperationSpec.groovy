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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.Appengine
import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.TrafficSplit
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DisableAppengineAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final SERVER_GROUP_NAME = 'app-stack-detail-v000'
  private static final REGION = 'us-central'
  private static final LOAD_BALANCER_NAME = 'default'
  private static final PROJECT = 'my-gcp-project'

  @Shared
  AppengineSafeRetry safeRetry
  def registry = new DefaultRegistry()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = AppengineSafeRetry.withoutDelay()
  }

  @Unroll
  void "split builder handles precision levels for shardBy types"() {
    when:
      def outputSplit = DisableAppengineAtomicOperation.buildTrafficSplitWithoutServerGroup(
        new AppengineTrafficSplit(allocations: inputAllocations, shardBy: shardType),
        serverGroupToDisable
      )

    then:
      outputSplit.allocations == expectedOutputAllocations

    where:
      inputAllocations     | serverGroupToDisable | shardType        || expectedOutputAllocations
      ["a": 0.5, "b": 0.5] | "a"                  | ShardBy.COOKIE   || ["b": 1.0]
      ["a": 0.5, "b": 0.5] | "b"                  | ShardBy.COOKIE   || ["a": 1.0]
      ["a": 0.5,
       "b": 0.25,
       "c": 0.25]          | "a"                  | ShardBy.COOKIE   || ["b": 0.5, "c": 0.5]
      ["a": 0.5,
       "b": 0.25,
       "c": 0.25]          | "c"                  | ShardBy.COOKIE   || ["a": 0.667, "b": 0.333]
      ["a": 0.5,
       "b": 0.25,
       "c": 0.25]          | "c"                  | ShardBy.IP       || ["a": 0.67, "b": 0.33]
      ["a": 0.4,
       "b": 0.4,
       "c": 0.2]           | "a"                  | ShardBy.COOKIE   || ["b": 0.667, "c": 0.333]
      ["a": 0.4,
       "b": 0.4,
       "c": 0.2]           | "a"                  | ShardBy.IP       || ["b": 0.67, "c": 0.33]
      ["a": 0.125,
       "b": 0.125,
       "c": 0.125,
       "d": 0.125,
       "e": 0.125,
       "f": 0.125,
       "g": 0.125,
       "h": 0.125]         | "a"                  | ShardBy.COOKIE   || ["b": 0.143,
                                                                         "c": 0.143,
                                                                         "d": 0.143,
                                                                         "e": 0.143,
                                                                         "f": 0.143,
                                                                         "g": 0.143,
                                                                         "h": 0.142]
      ["a": 0.33,
       "b": 0.17,
       "c": 0.5]           | "a"                  | ShardBy.COOKIE   || ["b": 0.254, "c": 0.746]
      ["a": 0.33,
       "b": 0.17,
       "c": 0.5]           | "a"                  | ShardBy.IP       || ["b": 0.26, "c": 0.74]
      ["a": 0.06,
       "b": 0.04,
       "c": 0.9]           | "a"                  | ShardBy.IP       || ["b": 0.05, "c": 0.95]
      ["a": 0.04,
       "b": 0.02,
       "c": 0.02,
       "d": 0.92]          | "a"                  | ShardBy.IP       || ["b": 0.03, "c": 0.02, "d": 0.95]
  }

  void "operation can disable a server group"() {
    setup:
      def clusterProviderMock = Mock(AppengineClusterProvider)
      def loadBalancerProviderMock = Mock(AppengineLoadBalancerProvider)

      def appengineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def getMock = Mock(Appengine.Apps.Services.Get)
      def patchMock = Mock(Appengine.Apps.Services.Patch)

      def credentials = new AppengineNamedAccountCredentials.Builder()
        .credentials(Mock(AppengineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appengineMock)
        .build()

      def description = new EnableDisableAppengineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials,
        migrateTraffic: false
      )

      def serverGroup = new AppengineServerGroup(name: SERVER_GROUP_NAME, loadBalancers: [LOAD_BALANCER_NAME])
      def loadBalancer = new AppengineLoadBalancer(
        name: LOAD_BALANCER_NAME,
        split: new AppengineTrafficSplit(
          allocations: [(SERVER_GROUP_NAME): 0.5, "will-get-allocation-of-1": 0.5],
          shardBy: ShardBy.COOKIE
        )
      )

      def ancestorService = new Service(
        split: new TrafficSplit(
          allocations: [(SERVER_GROUP_NAME): 0.5, "will-get-allocation-of-1": 0.5],
          shardBy: ShardBy.COOKIE.toString()
        )
      )

      def expectedService = new Service(
        split: new TrafficSplit(allocations: ["will-get-allocation-of-1": 1.0], shardBy: ShardBy.COOKIE)
      )

      @Subject def operation = new DisableAppengineAtomicOperation(description)
      operation.appengineClusterProvider = clusterProviderMock
      operation.appengineLoadBalancerProvider = loadBalancerProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * clusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup
      1 * loadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancer

      // Mocks live service look-up.
      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.get(PROJECT, LOAD_BALANCER_NAME) >> getMock
      1 * getMock.execute() >> ancestorService

      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(false) >> patchMock
      1 * patchMock.execute()
  }

  void "no-op if server group has no allocation"() {
    setup:
      def clusterProviderMock = Mock(AppengineClusterProvider)
      def loadBalancerProviderMock = Mock(AppengineLoadBalancerProvider)

      def appengineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def getMock = Mock(Appengine.Apps.Services.Get)

      def credentials = new AppengineNamedAccountCredentials.Builder()
        .credentials(Mock(AppengineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appengineMock)
        .build()

      def description = new EnableDisableAppengineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials,
        migrateTraffic: false
      )

      def serverGroup = new AppengineServerGroup(name: SERVER_GROUP_NAME, loadBalancers: [LOAD_BALANCER_NAME])
      def ancestorService = new Service(
        split: new TrafficSplit(
          allocations: ["has-allocation-of-1": 1],
          shardBy: ShardBy.COOKIE.toString()
        )
      )

      @Subject def operation = new DisableAppengineAtomicOperation(description)
      operation.appengineClusterProvider = clusterProviderMock
      operation.appengineLoadBalancerProvider = loadBalancerProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * clusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      // Mocks live service look-up.
      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.get(PROJECT, LOAD_BALANCER_NAME) >> getMock
      1 * getMock.execute() >> ancestorService
  }
}
