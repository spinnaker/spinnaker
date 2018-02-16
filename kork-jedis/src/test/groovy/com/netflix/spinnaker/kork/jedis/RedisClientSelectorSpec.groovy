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
package com.netflix.spinnaker.kork.jedis

import com.netflix.spinnaker.kork.jedis.exception.RedisClientNotFound
import spock.lang.Specification

class RedisClientSelectorSpec extends Specification {

  def "should return client by name"() {
    given:
    def primaryClient = Mock(RedisClientDelegate) {
      name() >> "primaryFoo"
    }
    def previousClient = Mock(RedisClientDelegate) {
      name() >> "previousFoo"
    }

    def subject = new RedisClientSelector([primaryClient, previousClient])

    expect:
    subject.primary("foo") == primaryClient
    subject.previous("foo").get() == previousClient
  }

  def "should throw exception if primary client cannot be found"() {
    given:
    def primaryClient = Mock(RedisClientDelegate) {
      name() >> "primaryFoo"
    }
    def previousClient = Mock(RedisClientDelegate) {
      name() >> "previousFoo"
    }

    def subject = new RedisClientSelector([primaryClient, previousClient])

    when:
    subject.primary("bar")

    then:
    thrown(RedisClientNotFound)
  }

  def "should fallback to default if available"() {
    given:
    def defaultPrimaryClient = Mock(RedisClientDelegate) {
      name() >> "primaryDefault"
    }
    def defaultPreviousClient = Mock(RedisClientDelegate) {
      name() >> "previousDefault"
    }

    def subject = new RedisClientSelector([defaultPrimaryClient, defaultPreviousClient])

    expect:
    subject.primary("foo") == defaultPrimaryClient
    subject.previous("foo").get() == defaultPreviousClient
  }
}
