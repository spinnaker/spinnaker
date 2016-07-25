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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.model.common.ActionResponse
import spock.lang.Specification

/**
 *
 */
class OpenstackRequestHandlerSpec extends Specification {

  OpenstackRequestHandler provider = new Provider()

  def "handle request succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()

    when:
    def response = provider.handleRequest { success }

    then:
    success == response
    noExceptionThrown()
  }

  def "handle request fails with failed action request"() {
    setup:
    def failed = ActionResponse.actionFailed("foo", 500)

    when:
    provider.handleRequest { failed }

    then:
    Exception ex = thrown(OpenstackProviderException)
    ex.message.contains("foo")
    ex.message.contains("500")
  }

  def "handle request fails with closure throwing exception"() {
    setup:
    def exception = new Exception("foo")

    when:
    provider.handleRequest { throw exception }

    then:
    Exception ex = thrown(OpenstackProviderException)
    ex.cause == exception
    ex.cause.message.contains("foo")
  }

  def "handle request non-action response"() {
    setup:
    def object = new Object()

    when:
    def response = provider.handleRequest { object }

    then:
    object == response
    noExceptionThrown()
  }

  def "handle request null response"() {
    when:
    def response = provider.handleRequest { null }

    then:
    response == null
    noExceptionThrown()
  }

  static class Provider implements OpenstackRequestHandler {}

}
