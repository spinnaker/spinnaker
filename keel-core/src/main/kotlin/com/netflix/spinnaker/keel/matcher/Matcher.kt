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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentPriority
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.policy.PriorityPolicy

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
interface Matcher {
  fun match(intent: Intent<IntentSpec>): Boolean
}

@JsonTypeName("All")
class AllMatcher : Matcher {
  override fun match(intent: Intent<IntentSpec>): Boolean {
    return true
  }
}

@JsonTypeName("Application")
class ApplicationMatcher(val expected: String) : Matcher {
  override fun match(intent: Intent<IntentSpec>) =
    when (intent.spec) {
      is ApplicationAwareIntentSpec -> intent.spec.application  == expected
      else -> false
    }
}

enum class PriorityMatcherScope {
  EQUAL, EQUAL_GT, EQUAL_LT
}

// TODO rz - allow defaulting intents if the priority policy isn't present
@JsonTypeName("Priority")
class PriorityMatcher(
  private val level: IntentPriority,
  private val scope: PriorityMatcherScope
) : Matcher {
  override fun match(intent: Intent<IntentSpec>)
    = intent.policies
        .filterIsInstance<PriorityPolicy>()
        .filter { p ->
          when (scope) {
            PriorityMatcherScope.EQUAL -> p.priority == level
            PriorityMatcherScope.EQUAL_GT -> p.priority >= level
            PriorityMatcherScope.EQUAL_LT -> p.priority <= level
          }
        }
        .count() > 0
}
