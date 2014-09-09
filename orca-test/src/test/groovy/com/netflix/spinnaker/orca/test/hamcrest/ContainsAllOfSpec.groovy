/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.test.hamcrest

import spock.lang.Specification
import static ContainsAllOf.containsAllOf
import static org.hamcrest.CoreMatchers.not
import static spock.util.matcher.HamcrestSupport.that

class ContainsAllOfSpec extends Specification {

  def "should accept exact same map"() {
    expect:
    that actual, containsAllOf(expected)

    where:
    actual           | _
    [a: "a", b: "b"] | _

    expected = (Map) actual.clone()
  }

  def "should accept map with more keys"() {
    expect:
    that actual, containsAllOf(expected)

    where:
    actual                   | expected
    [a: "a", b: "b", c: "c"] | [a: "a", b: "b"]
  }

  def "should reject incomplete set of keys"() {
    expect:
    that actual, not(containsAllOf(expected))

    where:
    actual   | expected
    [a: "a"] | [a: "a", b: "b"]
  }

  def "should reject map with same keys but different values"() {
    expect:
    that actual, not(containsAllOf(expected))

    where:
    actual           | expected
    [a: "a", b: "b"] | [a: "A", b: "B"]
  }

}
