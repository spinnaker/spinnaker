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

package com.netflix.spinnaker.rosco.executor

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.jobs.JobExecutor
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BakePollerSpec extends Specification {

  private static final String REGION = "some-region"
  private static final String JOB_ID = "123"
  private static final String AMI_ID = "ami-3cf4a854"
  private static final String IMAGE_NAME = "some-image"
  private static final String LOGS_CONTENT = "Some logs content..."

  @Unroll
  void 'scheduled update queries job executor and stores status and logs when incomplete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def incompleteBakeStatus = new BakeStatus(id: JOB_ID,
                                                resource_id: JOB_ID,
                                                state: bakeState,
                                                result: bakeResult,
                                                logsContent: LOGS_CONTENT)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> incompleteBakeStatus
      1 * bakeStoreMock.updateBakeStatus(incompleteBakeStatus)
      numStatusLookups * bakeStoreMock.retrieveRegionById(JOB_ID) >> REGION
      numStatusLookups * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> incompleteBakeStatus

    where:
      bakeState                 | bakeResult                | numStatusLookups
      BakeStatus.State.RUNNING  | null                      | 0
      BakeStatus.State.CANCELED | BakeStatus.Result.FAILURE | 1
  }

  void 'scheduled update queries job executor and stores status, details and logs when complete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def completeBakeStatus = new BakeStatus(id: JOB_ID,
                                              resource_id: JOB_ID,
                                              state: bakeState,
                                              result: bakeResult,
                                              logsContent: "$LOGS_CONTENT\n$LOGS_CONTENT")
      def bakeDetails = new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> completeBakeStatus
      1 * bakeStoreMock.retrieveCloudProviderById(JOB_ID) >> BakeRequest.CloudProviderType.gce.toString()
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      2 * bakeStoreMock.retrieveRegionById(JOB_ID) >> REGION  // 1 for metrics
      1 * cloudProviderBakeHandlerMock.scrapeCompletedBakeResults(REGION, JOB_ID, "$LOGS_CONTENT\n$LOGS_CONTENT") >> bakeDetails
      1 * bakeStoreMock.updateBakeDetails(bakeDetails)
      1 * bakeStoreMock.updateBakeStatus(completeBakeStatus)
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> completeBakeStatus

    where:
      bakeState                  | bakeResult
      BakeStatus.State.COMPLETED | BakeStatus.Result.SUCCESS
  }

  void 'scheduled update stores error and status when running job status cannot be retrieved'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> null
      1 * bakeStoreMock.storeBakeError(JOB_ID, "Unable to retrieve status for '$JOB_ID'.")
      1 * bakeStoreMock.cancelBakeById(JOB_ID)
      1 * bakeStoreMock.retrieveRegionById(JOB_ID) >> REGION
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> new BakeStatus()
  }

}
