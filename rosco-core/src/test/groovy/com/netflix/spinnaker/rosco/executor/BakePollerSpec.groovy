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

import com.google.gson.Gson
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptExecution
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import retrofit.RetrofitError
import retrofit.client.Header
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.mime.TypedByteArray
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BakePollerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "some-region"
  private static final String SCRIPT_ID = "123"
  private static final String CREDENTIALS = "some-credentials"
  private static final String AMI_ID = "ami-3cf4a854"
  private static final String IMAGE_NAME = "some-image"
  private static final String LOGS_CONTENT = "Some logs content..."

  @Unroll
  void 'scheduled update queries scripting engine and stores status and logs when incomplete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def executionStatusToBakeStateMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeStateMap)
      def executionStatusToBakeResultMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeResultMap)
      def scriptDetailsObservable = Observable.from(new ScriptExecution(id: SCRIPT_ID, status: executionStatus))
      def logsContentMapObservable = Observable.from([logsContent: LOGS_CONTENT])

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                      rushService: rushServiceMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      executionStatusToBakeStateMap: executionStatusToBakeStateMapMock,
                                      executionStatusToBakeResultMap: executionStatusToBakeResultMapMock)

    when:
      bakePoller.updateBakeStatusAndLogs(SCRIPT_ID)

    then:
      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> scriptDetailsObservable
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >> logsContentMapObservable

      1 * executionStatusToBakeStateMapMock.convertExecutionStatusToBakeState(executionStatus) >> bakeState
      1 * executionStatusToBakeResultMapMock.convertExecutionStatusToBakeResult(executionStatus) >> bakeResult


      1 * bakeStoreMock.updateBakeStatus(new BakeStatus(id: SCRIPT_ID,
                                                        resource_id: SCRIPT_ID,
                                                        state: bakeState,
                                                        result: bakeResult),
                                         [logsContent: LOGS_CONTENT])

    where:
      executionStatus  | bakeState                  | bakeResult
      "PREPARING"      | BakeStatus.State.PENDING   | null
      "FETCHING_IMAGE" | BakeStatus.State.PENDING   | null
      "RUNNING"        | BakeStatus.State.RUNNING   | null
      "FAILED"         | BakeStatus.State.CANCELLED | null
  }

  void 'scheduled update queries scripting engine and stores status, details and logs when complete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def executionStatusToBakeStateMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeStateMap)
      def executionStatusToBakeResultMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeResultMap)
      def scriptDetailsObservable = Observable.from(new ScriptExecution(id: SCRIPT_ID, status: executionStatus))
      def logsContentMapObservable = Observable.from([logsContent: "$LOGS_CONTENT\n$LOGS_CONTENT"])

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                      rushService: rushServiceMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      executionStatusToBakeStateMap: executionStatusToBakeStateMapMock,
                                      executionStatusToBakeResultMap: executionStatusToBakeResultMapMock)

    when:
      bakePoller.updateBakeStatusAndLogs(SCRIPT_ID)

    then:
      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> scriptDetailsObservable
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >> logsContentMapObservable

      1 * executionStatusToBakeStateMapMock.convertExecutionStatusToBakeState(executionStatus) >> bakeState
      1 * cloudProviderBakeHandlerRegistryMock.findProducer(LOGS_CONTENT) >> cloudProviderBakeHandlerMock
      1 * bakeStoreMock.retrieveRegionById(SCRIPT_ID) >> REGION

      1 * cloudProviderBakeHandlerMock.scrapeCompletedBakeResults(REGION, SCRIPT_ID, "$LOGS_CONTENT\n$LOGS_CONTENT") >> new Bake(id: SCRIPT_ID, ami: AMI_ID, image_name: IMAGE_NAME)
      1 * bakeStoreMock.updateBakeDetails(new Bake(id: SCRIPT_ID, ami: AMI_ID, image_name: IMAGE_NAME))

      1 * executionStatusToBakeResultMapMock.convertExecutionStatusToBakeResult(executionStatus) >> bakeResult
      1 * bakeStoreMock.updateBakeStatus(new BakeStatus(id: SCRIPT_ID,
                                                        resource_id: SCRIPT_ID,
                                                        state: bakeState,
                                                        result: bakeResult),
                                         [logsContent: "$LOGS_CONTENT\n$LOGS_CONTENT"])

    where:
      executionStatus | bakeState                  | bakeResult
      "SUCCESSFUL"    | BakeStatus.State.COMPLETED | BakeStatus.Result.SUCCESS
  }

  void 'scheduled update stores error and status when scripting engine throws exception'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def executionStatusToBakeStateMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeStateMap)
      def executionStatusToBakeResultMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeResultMap)
      def retrofitErrorTypedInput = new TypedByteArray(null, "\"Some Rush error...\"".bytes)
      def retrofitErrorResponse = new Response("http://some-rush-engine/...", 500, "Some Rush reason...", new ArrayList<Header>(), retrofitErrorTypedInput)
      def retrofitError = RetrofitError.httpError("http://some-rush-engine/...", retrofitErrorResponse, new GsonConverter(new Gson()), String.class)

      retrofitError.body

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                      rushService: rushServiceMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      executionStatusToBakeStateMap: executionStatusToBakeStateMapMock,
                                      executionStatusToBakeResultMap: executionStatusToBakeResultMapMock)

    when:
      bakePoller.updateBakeStatusAndLogs(SCRIPT_ID)

    then:

      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> { throw retrofitError }
      1 * bakeStoreMock.storeBakeError(SCRIPT_ID, "\"Some Rush error...\"")
      1 * bakeStoreMock.updateBakeStatus(new BakeStatus(id: SCRIPT_ID,
                                                        resource_id: SCRIPT_ID,
                                                        state: BakeStatus.State.CANCELLED,
                                                        result: BakeStatus.Result.FAILURE))
  }

}
