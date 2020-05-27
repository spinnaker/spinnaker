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

package com.netflix.spinnaker.front50

import spock.lang.Specification
import spock.lang.Unroll

class UntypedUtilsSpec extends Specification {

  @Unroll
  def "get and set property"() {
    given:
    def obj = new SomeObject()

    when:
    UntypedUtils.setProperty(obj, "property", value)

    then:
    value == UntypedUtils.getProperty(obj, "property")

    where:
    value << [null, "hello"]
  }

  def "get properties"() {
    given:
    def obj = new SomeObject(property: 1, property2: 2, property3: 3)

    expect:
    UntypedUtils.getProperties(obj) == [
      property: "1",
      property2: "2",
      property3: "3"
    ]
  }

  def "has property"() {
    given:
    def obj = new SomeObject()

    expect:
    UntypedUtils.hasProperty(obj, "property")
    !UntypedUtils.hasProperty(obj, "nope")
  }

  class SomeObject {
    def property
    def property2
    def property3
  }
}
