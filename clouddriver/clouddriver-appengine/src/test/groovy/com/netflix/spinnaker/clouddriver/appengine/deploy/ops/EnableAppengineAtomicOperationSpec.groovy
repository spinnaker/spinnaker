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

class EnableAppengineAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final SERVER_GROUP_NAME = 'app-stack-detail-v000'
  private static final REGION = 'us-central'
  private static final LOAD_BALANCER_NAME = 'default'
  private static final PROJECT = 'my-gcp-project'

  @Shared
  AppengineSafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = AppengineSafeRetry.withoutDelay()
  }

  void "enable operation should set a server group's allocation to 1"() {
    setup:
      def clusterProviderMock = Mock(AppengineClusterProvider)
      def loadBalancerProviderMock = Mock(AppengineLoadBalancerProvider)

      def appengineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
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
          allocations: ["soon-to-have-no-allocation-1": 0.5, "soon-to-have-no-allocation-2": 0.5],
          shardBy: ShardBy.COOKIE
        )
      )

      def expectedService = new Service(
        split: new TrafficSplit(allocations: [(SERVER_GROUP_NAME): 1.0], shardBy: ShardBy.COOKIE)
      )

      @Subject def operation = new EnableAppengineAtomicOperation(description)
      operation.appengineClusterProvider = clusterProviderMock
      operation.appengineLoadBalancerProvider = loadBalancerProviderMock
      operation.registry = new DefaultRegistry()
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * clusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup
      1 * loadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancer

      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(false) >> patchMock
      1 * patchMock.execute()
  }
}
