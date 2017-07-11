/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.exceptions

import groovy.transform.InheritConstructors
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse;

class GenericExceptionHandlersSpec extends Specification {
  @Subject
  def genericExceptionHandlers = new GenericExceptionHandlers()

  def request = Mock(HttpServletRequest)
  def response = Mock(HttpServletResponse)

  @Unroll
  def "should favor exception message when present (otherwise use ResponseStatus.reason())"() {
    when:
    genericExceptionHandlers.handleException(exception, response, request)

    then:
    1 * response.sendError(404, expectedReason)

    where:
    exception                     || expectedReason
    new MyException()             || "Default Reason!"
    new MyException("")           || "Default Reason!"
    new MyException("  ")         || "Default Reason!"
    new MyException("My Message") || "My Message"
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Default Reason!")
  class MyException extends RuntimeException {
    MyException() {
      super()
    }

    MyException(String message) {
      super(message)
    }
  }
}
