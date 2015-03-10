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

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptId
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BakeryControllerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "some-region"
  private static final String SCRIPT_ID = "123"
  private static final String EXISTING_SCRIPT_ID = "456"
  private static final String CREDENTIALS = "some-credentials"
  private static final String AMI_ID = "ami-3cf4a854"
  private static final String IMAGE_NAME = "some-image"
  private static final String BAKE_KEY = "bake:gce:ubuntu:$PACKAGE_NAME"
  private static final String LOGS_CONTENT = "Some logs content..."

  void 'create bake issues script command and returns new status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def runScriptObservable = Observable.from(new ScriptId(id: SCRIPT_ID))
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * rushServiceMock.runScript(new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME, command: "packer build ...")) >> runScriptObservable
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, SCRIPT_ID) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
  }

  void 'create bake polls for status when lock cannot be acquired'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      4 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
  }

  void 'create bake polls for status when lock cannot be acquired, but tries for lock again if status cannot be obtained'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def runScriptObservable = Observable.from(new ScriptId(id: SCRIPT_ID))
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      (10.._) * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * rushServiceMock.runScript(new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME, command: "packer build ...")) >> runScriptObservable
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, SCRIPT_ID) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
  }

  void 'create bake polls for status when lock cannot be acquired, tries for lock again if status cannot be obtained, and throws exception if that fails'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

    @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      (10.._) * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      IllegalArgumentException e = thrown()
      e.message == "Unable to acquire lock and unable to determine id of lock holder for bake key 'bake:gce:ubuntu:kato'."
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

  @Unroll
  void 'create bake returns existing status when prior bake is pending or running'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: bakeState)
      bakeStatus == new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: bakeState)

    where:
      bakeState << [BakeStatus.State.PENDING, BakeStatus.State.RUNNING]
  }

  void 'create bake returns existing status when prior bake is completed and successful'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: BakeStatus.State.COMPLETED, result: BakeStatus.Result.SUCCESS)
      bakeStatus == new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: BakeStatus.State.COMPLETED, result: BakeStatus.Result.SUCCESS)
  }

  void 'create bake issues script command and returns new status when prior bake is completed and failure'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def runScriptObservable = Observable.from(new ScriptId(id: SCRIPT_ID))
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: BakeStatus.State.COMPLETED, result: BakeStatus.Result.FAILURE)
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * rushServiceMock.runScript(new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME, command: "packer build ...")) >> runScriptObservable
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, SCRIPT_ID) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
  }

  @Unroll
  void 'create bake issues script command and returns new status when prior bake is suspended or cancelled'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def rushServiceMock = Mock(RushService)
      def runScriptObservable = Observable.from(new ScriptId(id: SCRIPT_ID))
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock,
                                                  rushService: rushServiceMock)

    when:
      def bakeStatus = bakeryController.createBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> new BakeStatus(id: EXISTING_SCRIPT_ID, resource_id: EXISTING_SCRIPT_ID, state: bakeState)
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> "packer build ..."
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * rushServiceMock.runScript(new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME, command: "packer build ...")) >> runScriptObservable
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, SCRIPT_ID) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING)

    where:
      bakeState << [BakeStatus.State.SUSPENDED, BakeStatus.State.CANCELLED]
  }

  void 'lookup status queries bake store and returns bake status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def bakeStatus = bakeryController.lookupStatus(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.retrieveBakeStatusById(SCRIPT_ID) >> new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING, result: null)
      bakeStatus == new BakeStatus(id: SCRIPT_ID, resource_id: SCRIPT_ID, state: BakeStatus.State.PENDING, result: null)
  }

  void 'lookup status throws exception when script execution cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupStatus(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.retrieveBakeStatusById(SCRIPT_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve status for '123'."
  }

  void 'lookup bake queries bake store and returns bake details'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock)

    when:
      def bakeDetails = bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.retrieveBakeDetailsById(SCRIPT_ID) >> new Bake(id: SCRIPT_ID, ami: AMI_ID, image_name: IMAGE_NAME)
      bakeDetails == new Bake(id: SCRIPT_ID, ami: AMI_ID, image_name: IMAGE_NAME)
  }

  void 'lookup bake throws exception when script execution cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
      def bakeryController = new BakeryController(baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupBake(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.retrieveBakeDetailsById(SCRIPT_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve bake details for '123'."
  }

  void 'lookup logs queries bake store and returns logs content'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      def response = bakeryController.lookupLogs(REGION, SCRIPT_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(SCRIPT_ID) >> [logsContent: LOGS_CONTENT]
      response == LOGS_CONTENT
  }

  void 'lookup logs throws exception when script execution logs are empty or malformed'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupLogs(REGION, SCRIPT_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(SCRIPT_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, SCRIPT_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(SCRIPT_ID) >> [:]
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, SCRIPT_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(SCRIPT_ID) >> [logsContent: null]
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, SCRIPT_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(SCRIPT_ID) >> [logsContent: '']
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."
  }

  void 'delete bake updates bake store and returns status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock)

    when:
      def response = bakeryController.deleteBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.deleteBakeByKey(BAKE_KEY) >> true
      response == "Deleted bake '$BAKE_KEY'."
  }

  void 'delete bake throws exception when bake key cannot be found'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  baseScriptRequest: new ScriptRequest(credentials: CREDENTIALS, image: IMAGE_NAME),
                                                  bakeStore: bakeStoreMock)

    when:
      bakeryController.deleteBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.deleteBakeByKey(BAKE_KEY) >> false
      IllegalArgumentException e = thrown()
      e.message == "Unable to locate bake with key '$BAKE_KEY'."
  }

  void 'cancel bake updates bake store and returns status'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      def response = bakeryController.cancelBake(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.cancelBakeById(SCRIPT_ID) >> true
      response == "Cancelled bake '$SCRIPT_ID'."
  }

  void 'cancel bake throws exception when bake id cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.cancelBake(REGION, SCRIPT_ID)

    then:
      1 * bakeStoreMock.cancelBakeById(SCRIPT_ID) >> false
      IllegalArgumentException e = thrown()
      e.message == "Unable to locate incomplete bake with id '$SCRIPT_ID'."
  }

}
