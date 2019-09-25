/*
 * Copyright 2019 Netflix, Inc.
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
 */
package com.netflix.spinnaker.clouddriver.titus

import spock.lang.Specification
import spock.lang.Unroll

class TitusExceptionSpec extends Specification {

  @Unroll
  def "should set retryable=#retryable on message=#message"() {
    given:
    Exception downstream = new RuntimeException(message)

    expect:
    new TitusException("Something bad happened", downstream).retryable == retryable

    where:
    message                                  || retryable
    "INVALID_ARGUMENT: Image does not exist" || false
    "Something else entirely"                || true
  }
}
