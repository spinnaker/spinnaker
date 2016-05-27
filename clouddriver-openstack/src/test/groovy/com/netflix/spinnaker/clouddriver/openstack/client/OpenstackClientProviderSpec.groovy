/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import org.openstack4j.api.OSClient
import org.openstack4j.model.common.ActionResponse
import spock.lang.Specification

class OpenstackClientProviderSpec extends Specification {

  private static final String OPERATION = "TestOperation"
  private provider

  def setup() {
    provider = new OpenstackClientV2Provider(Mock(OSClient.OSClientV2))
  }

  def "handle request succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()

    when:
    def response = provider.handleRequest(OPERATION) { success }

    then:
    success == response
    noExceptionThrown()
  }

  def "handle request fails with failed action request"() {
    setup:
    def failed = ActionResponse.actionFailed("foo", 500)

    when:
    provider.handleRequest(OPERATION) { failed }

    then:
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message.contains("foo")
    ex.message.contains("500")
    ex.message.contains(OPERATION)
  }

  def "handle request fails with closure throwing exception"() {
    setup:
    def exception = new Exception("foo")

    when:
    provider.handleRequest(OPERATION) { throw exception }

    then:
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.cause == exception
    ex.message.contains("foo")
    ex.message.contains(OPERATION)
  }
}
