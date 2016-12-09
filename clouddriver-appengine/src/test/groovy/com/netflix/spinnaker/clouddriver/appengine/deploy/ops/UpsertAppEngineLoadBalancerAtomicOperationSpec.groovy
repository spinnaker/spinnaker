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
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class UpsertAppEngineLoadBalancerAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final LOAD_BALANCER_NAME = "default"
  private static final SERVER_GROUP_NAME_1 = "app-stack-detail-v000"
  private static final SERVER_GROUP_NAME_2 = "app-stack-detail-v001"
  private static final REGION = "us-central"
  private static final PROJECT = "myapp"

  private static final LOAD_BALANCER_IN_CACHE = new AppEngineLoadBalancer(
    name: LOAD_BALANCER_NAME,
    split: new AppEngineTrafficSplit(allocations: [(SERVER_GROUP_NAME_1): 1])
  )

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "can update AppEngine service using shardBy type and allocation from upsert description"() {
    setup:
      def appEngineLoadBalancerProviderMock = Mock(AppEngineLoadBalancerProvider)

      def appEngineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def patchMock = Mock(Appengine.Apps.Services.Patch)

      def credentials = new AppEngineNamedAccountCredentials.Builder()
        .credentials(Mock(AppEngineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appEngineMock)
        .build()

      def migrateTraffic = false
      def descriptionSplit = new AppEngineTrafficSplit(
        allocations: [(SERVER_GROUP_NAME_2): 1],
        shardBy: ShardBy.IP
      )

      def description = new UpsertAppEngineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        credentials: credentials,
        loadBalancerName: LOAD_BALANCER_NAME,
        migrateTraffic: migrateTraffic,
        split: descriptionSplit
      )

      @Subject def operation = new UpsertAppEngineLoadBalancerAtomicOperation(description)
      operation.appEngineLoadBalancerProvider = appEngineLoadBalancerProviderMock

      def expectedService = new Service(
        split: new TrafficSplit(allocations: descriptionSplit.allocations,
        shardBy: descriptionSplit.shardBy)
      )

    when:
      operation.operate([])

    then:
      1 * appEngineLoadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> LOAD_BALANCER_IN_CACHE


      1 * appEngineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(migrateTraffic) >> patchMock
      1 * patchMock.execute()
  }

  void "can update AppEngine service with only shardBy value in description, uses existing service split allocation"() {
    setup:
      def appEngineLoadBalancerProviderMock = Mock(AppEngineLoadBalancerProvider)

      def appEngineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def patchMock = Mock(Appengine.Apps.Services.Patch)

      def credentials = new AppEngineNamedAccountCredentials.Builder()
        .credentials(Mock(AppEngineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appEngineMock)
        .build()

      def migrateTraffic = false
      def split = new AppEngineTrafficSplit(shardBy: ShardBy.IP)

      def description = new UpsertAppEngineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        credentials: credentials,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: split,
        migrateTraffic: migrateTraffic)

      @Subject def operation = new UpsertAppEngineLoadBalancerAtomicOperation(description)
      operation.appEngineLoadBalancerProvider = appEngineLoadBalancerProviderMock

      def expectedService = new Service(split: new TrafficSplit(
        allocations: LOAD_BALANCER_IN_CACHE.split.allocations,
        shardBy: split.shardBy.toString())
      )

    when:
      operation.operate([])

    then:
      1 * appEngineLoadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> LOAD_BALANCER_IN_CACHE

      1 * appEngineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(migrateTraffic) >> patchMock
      1 * patchMock.execute()
  }
}
