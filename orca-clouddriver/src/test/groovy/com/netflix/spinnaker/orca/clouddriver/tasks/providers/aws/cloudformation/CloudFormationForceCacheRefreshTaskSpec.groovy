/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_OK
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import java.time.Instant

class CloudFormationForceCacheRefreshTaskSpec extends Specification {
  static final String CREDENTIALS = "aws-account"
  static final List<String> REGIONS = ["eu-west-1"]
  static final String PROVIDER = "aws"
  static final String REFRESH_TYPE = "CloudFormation"
  static final String STACK_NAME = "StackName"

  def defaultContext = [
      credentials: CREDENTIALS,
      regions: REGIONS,
      stackName: STACK_NAME
  ]
  def refreshDetails = [
      credentials: CREDENTIALS,
      region:  REGIONS,
      stackName: STACK_NAME
  ]
  def cacheService = Mock(CloudDriverCacheService)
  def cacheStatusService = Mock(CloudDriverCacheStatusService)
  def objectMapper = new ObjectMapper()
  def registry = new DefaultRegistry()
  def now = Instant.now()

  @Subject task = new CloudFormationForceCacheRefreshTask(registry, cacheService, cacheStatusService, objectMapper)

  void "returns RUNNING when the refresh request is accepted but not processed"() {
    given:
    def stage = mockStage(defaultContext)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, CloudFormationForceCacheRefreshTask.REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING
  }

  void "returns SUCCEED when the request is immediately processed"() {
    given:
    def stage = mockStage(defaultContext)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, CloudFormationForceCacheRefreshTask.REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_OK)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  void "waits for a pending refresh"(){
    given:
    def stage = mockStage(defaultContext)
    def context = defaultContext

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.getContext()
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [pendingRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  private StageExecutionImpl mockStage(Object context) {
    def stage = stage()
    stage.setContext(context)
    stage.setStartTime(now.toEpochMilli())
    return stage
  }

  private Response mockResponse(int status ) {
    def oortResponse
    if (status == HTTP_ACCEPTED) {
      oortResponse = "{\"cachedIdentifiersByType\" : {\"stacks\" : [ \"aws:stacks:delivery-dev:eu-west-1:arn:aws:cloudformation:eu-west-1:549172523946:stack/StackName/676a9750-3baa-11eb-80fb-061510aab3dd\" ] } }".stripIndent()
      return new Response('', status, 'OK', [], new TypedString(oortResponse))
    } else {
      return new Response( "", status, "", [], null)
    }
  }


  private Map pendingRefresh(Map refreshDetails) {
    return [
        details: [
            account:  CREDENTIALS,
            id: STACK_NAME,
            region: "eu-west-1"
            ],
        processedCount: 0,
        processedTime: -1,
        cacheTime: now.plusMillis(10).toEpochMilli()
    ]
  }

  private Map processedRefresh(Map refreshDetails) {
    return [
        details       : [
            account: CREDENTIALS,
            id: STACK_NAME,
            region: "eu-west-1"
        ] ,
        processedCount: 1,
        processedTime : now.plusMillis(5000).toEpochMilli(),
        cacheTime     : now.plusMillis(10).toEpochMilli()
    ]
  }
}
