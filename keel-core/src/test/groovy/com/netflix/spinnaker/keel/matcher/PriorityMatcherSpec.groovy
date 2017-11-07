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
package com.netflix.spinnaker.keel.matcher

import com.netflix.spinnaker.keel.IntentPriority
import com.netflix.spinnaker.keel.policy.PriorityPolicy
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import spock.lang.Specification
import spock.lang.Unroll

class PriorityMatcherSpec extends Specification {

  @Unroll
  def "should filter intents by priority"() {
    given:
    def intent = new TestIntent(
      new TestIntentSpec("1", [:]),
      [new PriorityPolicy(IntentPriority.HIGH)]
    )

    expect:
    matches == new PriorityMatcher(level, scope).match(intent)

    where:
    level                 | scope                         || matches
    IntentPriority.NORMAL | PriorityMatcherScope.EQUAL    || false
    IntentPriority.NORMAL | PriorityMatcherScope.EQUAL_GT || false
    IntentPriority.NORMAL | PriorityMatcherScope.EQUAL_LT || true
  }
}
