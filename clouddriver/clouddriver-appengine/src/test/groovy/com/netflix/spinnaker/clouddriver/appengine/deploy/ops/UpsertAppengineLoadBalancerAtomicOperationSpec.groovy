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
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertAppengineLoadBalancerAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final LOAD_BALANCER_NAME = "default"
  private static final ORIGINAL_SERVER_GROUP = "app-stack-detail-v000"
  private static final REPLACEMENT_SERVER_GROUP = "app-stack-detail-v001"
  private static final REGION = "us-central"
  private static final PROJECT = "myapp"

  private static final LOAD_BALANCER_IN_CACHE = new AppengineLoadBalancer(
    name: LOAD_BALANCER_NAME,
    split: new AppengineTrafficSplit(allocations: [(ORIGINAL_SERVER_GROUP): 1])
  )

  @Shared
  AppengineSafeRetry safeRetry
  def registry = new DefaultRegistry()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = AppengineSafeRetry.withoutDelay()
  }

  @Unroll
  void "can update Appengine service using shardBy type and allocation from upsert description"() {
    setup:
      def appengineLoadBalancerProviderMock = Mock(AppengineLoadBalancerProvider)

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

      def migrateTraffic = false
      def descriptionSplit = new AppengineTrafficSplit(
        allocations: inputAllocations,
        shardBy: ShardBy.IP
      )

      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        credentials: credentials,
        loadBalancerName: LOAD_BALANCER_NAME,
        migrateTraffic: migrateTraffic,
        split: descriptionSplit
      )

      @Subject def operation = new UpsertAppengineLoadBalancerAtomicOperation(description)
      operation.appengineLoadBalancerProvider = appengineLoadBalancerProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * appengineLoadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> LOAD_BALANCER_IN_CACHE

      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(migrateTraffic) >> patchMock
      1 * patchMock.execute()

    where:
      inputAllocations                || expectedService
      [(REPLACEMENT_SERVER_GROUP): 1] || new Service(split: new TrafficSplit(allocations: [(REPLACEMENT_SERVER_GROUP): 1], shardBy: ShardBy.IP))
      [(REPLACEMENT_SERVER_GROUP): 1,
       "no-allocation": 0]            || new Service(split: new TrafficSplit(allocations: [(REPLACEMENT_SERVER_GROUP): 1], shardBy: ShardBy.IP))
  }

  void "can update Appengine service with only shardBy value in description, uses existing service split allocation"() {
    setup:
      def appengineLoadBalancerProviderMock = Mock(AppengineLoadBalancerProvider)

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

      def migrateTraffic = false
      def split = new AppengineTrafficSplit(shardBy: ShardBy.IP)

      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        credentials: credentials,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: split,
        migrateTraffic: migrateTraffic)

      @Subject def operation = new UpsertAppengineLoadBalancerAtomicOperation(description)
      operation.appengineLoadBalancerProvider = appengineLoadBalancerProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

      def expectedService = new Service(split: new TrafficSplit(
        allocations: LOAD_BALANCER_IN_CACHE.split.allocations,
        shardBy: split.shardBy.toString())
      )

    when:
      operation.operate([])

    then:
      1 * appengineLoadBalancerProviderMock.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> LOAD_BALANCER_IN_CACHE

      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.patch(PROJECT, LOAD_BALANCER_NAME, expectedService) >> patchMock
      1 * patchMock.setUpdateMask("split") >> patchMock
      1 * patchMock.setMigrateTraffic(migrateTraffic) >> patchMock
      1 * patchMock.execute()
  }
}
