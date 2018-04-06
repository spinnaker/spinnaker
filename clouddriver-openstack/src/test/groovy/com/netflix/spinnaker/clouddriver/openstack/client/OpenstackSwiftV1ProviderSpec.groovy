/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import org.openstack4j.api.storage.ObjectStorageObjectService
import org.openstack4j.api.storage.ObjectStorageService
import org.openstack4j.core.transport.HttpResponse
import org.openstack4j.model.common.DLPayload
import org.springframework.http.HttpStatus
import spock.lang.Unroll

class OpenstackSwiftV1ProviderSpec extends OpenstackClientProviderSpec {

  def swift
  def objectsService
  def payload
  def response
  def container = 'test-container'
  def name = 'some/path/to/object'
  def userData = '#!/bin/sh...some user data script'

  def setup() {
    swift = Mock(ObjectStorageService)
    mockClient.objectStorage() >> swift
    objectsService = Mock(ObjectStorageObjectService)
    swift.objects() >> objectsService
    payload = Mock(DLPayload)
    response = Mock(HttpResponse)
  }

  def "reads object from swift"() {
    when:
    def object = provider.readSwiftObject(region, container, name)

    then:
    object == userData
    1 * objectsService.download(container, name) >> payload
    1 * payload.getHttpResponse() >> response
    1 * response.getStatus() >> 200
    1 * response.getEntity(String) >> userData
  }

  def "did not get a payload for swift object"() {
    when:
    provider.readSwiftObject(region, container, name)

    then:
    thrown(OpenstackResourceNotFoundException)
    1 * objectsService.download(container, name) >> null
  }

  @Unroll
  def "bad status from response #status"() {
    when:
    provider.readSwiftObject(region, container, name)

    then:
    thrown(OpenstackProviderException)
    1 * objectsService.download(container, name) >> payload
    1 * payload.getHttpResponse() >> response
    2 * response.getStatus() >> status

    where:
    status << [100, 201, 204, 302, 400, 418, 500]
  }

  def "unable to get entity from response"() {
    when:
    String actual = provider.readSwiftObject(region, container, name)

    then:
    !actual
    noExceptionThrown()
    1 * objectsService.download(container, name) >> payload
    1 * payload.getHttpResponse() >> response
    1 * response.getStatus() >> 200
    1 * response.getEntity(String) >> null
  }
}
