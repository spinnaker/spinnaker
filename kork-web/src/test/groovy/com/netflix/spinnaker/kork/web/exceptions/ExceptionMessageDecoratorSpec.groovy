/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.kork.web.exceptions

import spock.lang.Shared
import spock.lang.Specification


class ExceptionMessageDecoratorSpec extends Specification {

  @Shared
  String messageToBeAppended = "Message to be appended."

  AccessDeniedExceptionMessage accessDeniedExceptionMessage = new AccessDeniedExceptionMessage(messageToBeAppended)
  ExceptionMessageProvider exceptionMessageProvider = new ExceptionMessageProvider([accessDeniedExceptionMessage])
  ExceptionMessageDecorator exceptionMessageDecorator = new ExceptionMessageDecorator(exceptionMessageProvider)

  def "Returns a message that is the original exception message and the message provided from the appender"() {
    when:
    String userMessage = exceptionMessageDecorator.decorate(reason, originalMessage)

    then:
    userMessage == expectedResult

    where:
    reason                                               || originalMessage    || expectedResult
    new LocalAccessDeniedException("Access is denied")   || "Access is denied" || "Access is denied" + "\n" + messageToBeAppended
    "authorization"                                      || "Access is denied" || "Access is denied" + "\n" + messageToBeAppended
    new RuntimeException("Runtime exception")            || "Runtime exception"|| "Runtime exception"
    "unsupported"                                        || "Error message"    || "Error message"
  }
}
