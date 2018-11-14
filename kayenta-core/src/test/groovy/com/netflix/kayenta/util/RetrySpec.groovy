/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.kayenta.util

import spock.lang.Specification
import spock.lang.Unroll;

class RetrySpec extends Specification {
  @Unroll
  def "should retry until success or #maxRetries attempts is reached"() {
    given:
    def retry = Spy(Retry) {
      Math.min(maxRetries - 1, failures) * sleep(10000) >> { /* do nothing */ }
    }

    int attemptCounter = 0;

    when:
    def exceptionMessage
    try {
      retry.retry({
        if (attemptCounter++ < failures) {
          throw new IllegalStateException("Failed after " + attemptCounter + " attempts");
        }
      }, maxRetries, 10000)
    } catch (Exception e) {
      exceptionMessage = e.message
    }

    then:
    attemptCounter == expectedAttempts
    exceptionMessage == expectedExceptionMessage

    where:
    failures || maxRetries || expectedAttempts || expectedExceptionMessage
    3        || 10         || 4                || null
    11       || 10         || 10               || "Failed after 10 attempts"
  }

  def "should sleep exponentially"() {
    given:
    def retry = Spy(Retry) {
      1 * sleep(10000) >> { /* do nothing */ }
      1 * sleep(20000) >> { /* do nothing */ }
      1 * sleep(40000) >> { /* do nothing */ }
      1 * sleep(80000) >> { /* do nothing */ }
    }

    int attemptCounter = 0;

    when:
    retry.exponential({
      if (attemptCounter++ < failures) {
        throw new IllegalStateException("Failed after " + attemptCounter + " attempts");
      }
    }, maxRetries, 10000)

    then:
    attemptCounter == expectedAttempts

    where:
    failures || maxRetries || expectedAttempts
    4        || 10         || 5
  }
}
