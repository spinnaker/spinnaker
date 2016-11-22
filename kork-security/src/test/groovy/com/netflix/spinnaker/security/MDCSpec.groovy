/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.security

import org.slf4j.MDC
import spock.lang.Specification

class MDCSpec extends Specification {
  def "should not propagate context across threads"() {
    given:
    MDC.put("test", "string")

    when:
    def childThreadValue
    def t = Thread.start {
      childThreadValue = MDC.get("test")
    }
    t.join()

    then:
    MDC.get("test") == "string"
    childThreadValue == null
  }
}
