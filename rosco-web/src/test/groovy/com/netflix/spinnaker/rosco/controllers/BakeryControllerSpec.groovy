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

package com.netflix.spinnaker.rosco.controllers

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptExecution
import com.netflix.spinnaker.rosco.rush.api.ScriptId
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import retrofit.RetrofitError
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class BakeryControllerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "some-region"
  private static final String SCRIPT_ID = "123"
  private static final String CREDENTIALS = "some-credentials"
  private static final String IMAGE_NAME = "some-image"

  void 'create bake issues script command and returns status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def rushServiceMock = Mock(RushService)
      def runScriptObservable = Observable.from(new ScriptId(id: SCRIPT_ID))
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * rushServiceMock.runScript(new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME, command: "packer build ...")) >> runScriptObservable
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
  }

  void 'create bake throws exception on provider that lacks registered bake handler'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock)

    when:
      bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unknown provider type 'gce'."
  }

  void 'lookup status converts execution status to bake state and bake result'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def rushServiceMock = Mock(RushService)
      def executionStatusToBakeStateMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeStateMap)
      def executionStatusToBakeResultMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeResultMap)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  rushService: rushServiceMock,
                                                  executionStatusToBakeStateMap: executionStatusToBakeStateMapMock,
                                                  executionStatusToBakeResultMap: executionStatusToBakeResultMapMock)

    when:
      def bakeStatus = bakeryController.lookupStatus(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> Observable.from(new ScriptExecution(id: SCRIPT_ID, status: "PREPARING"))
      1 * executionStatusToBakeStateMapMock.convertExecutionStatusToBakeState("PREPARING") >> BakeStatus.State.PENDING
      1 * executionStatusToBakeResultMapMock.convertExecutionStatusToBakeResult("PREPARING") >> null
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING, result: null)
  }

  void 'lookup status converts successful execution status to bake state and bake result'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def rushServiceMock = Mock(RushService)
      def executionStatusToBakeStateMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeStateMap)
      def executionStatusToBakeResultMapMock = Mock(RoscoConfiguration.ExecutionStatusToBakeResultMap)

    @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  rushService: rushServiceMock,
                                                  executionStatusToBakeStateMap: executionStatusToBakeStateMapMock,
                                                  executionStatusToBakeResultMap: executionStatusToBakeResultMapMock)

    when:
      def bakeStatus = bakeryController.lookupStatus(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> Observable.from(new ScriptExecution(id: SCRIPT_ID, status: "SUCCESSFUL"))
      1 * executionStatusToBakeStateMapMock.convertExecutionStatusToBakeState("SUCCESSFUL") >> BakeStatus.State.COMPLETED
      1 * executionStatusToBakeResultMapMock.convertExecutionStatusToBakeResult("SUCCESSFUL") >> BakeStatus.Result.SUCCESS
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING, result: BakeStatus.Result.SUCCESS)
  }

  void 'lookup status throws exception when script execution cannot be found'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def rushServiceMock = Mock(RushService)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  rushService: rushServiceMock)

    when:
      bakeryController.lookupStatus(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.scriptDetails(SCRIPT_ID) >> { throw RetrofitError.unexpectedError("http://rush...", new Throwable()) }
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve status for '123'."
  }

  void 'lookup bake returns scraped ami id and image name'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def rushServiceMock = Mock(RushService)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  rushService: rushServiceMock)

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >>
        Observable.from([logsContent: "First line.\nSecond line."])
      1 * cloudProviderBakeHandlerRegistryMock.findProducer("First line.") >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.scrapeCompletedBakeResults(REGION, SCRIPT_ID, "First line.\nSecond line.")
  }

  void 'lookup bake throws exception when script execution cannot be found'() {
    setup:
      def rushServiceMock = Mock(RushService)

    @Subject
      def bakeryController = new BakeryController(baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  rushService: rushServiceMock)

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >>
        { throw RetrofitError.unexpectedError("http://rush...", new Throwable()) }
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve bake details for '123'."
  }

  void 'lookup bake throws exception when script execution logs are empty or malformed'() {
    setup:
      def rushServiceMock = Mock(RushService)

    @Subject
      def bakeryController = new BakeryController(baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  rushService: rushServiceMock)

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >>
        Observable.from([:])
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve bake details for '123'."

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >>
        Observable.from([logsContent: null])
      e = thrown()
      e.message == "Unable to retrieve bake details for '123'."

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * rushServiceMock.getLogs(SCRIPT_ID, new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME)) >>
        Observable.from([logsContent: ''])
      e = thrown()
      e.message == "Unable to retrieve bake details for '123'."
  }
}
