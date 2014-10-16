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

import groovy.transform.CompileStatic
import com.google.common.collect.Maps
import org.hamcrest.Description
import org.hamcrest.Factory
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeDiagnosingMatcher

/**
 * A Hamcrest matcher for maps that should contain some particular mappings and may optionally contain others that are
 * irrelevant to the assertion. All keys present in the *expected* map must be present in the *actual* map and have the
 * same values. Extra keys in the *actual* map are ignored.
 *
 * @param < K >
 * @param < V >
 */
@CompileStatic
class ContainsAllOf<K, V> extends TypeSafeDiagnosingMatcher<Map<? extends K, ? extends V>> {

  @Factory
  static <K, V> Matcher<Map<? extends K, ? extends V>> containsAllOf(Map<? extends K, ? extends V> expected) {
    new ContainsAllOf(expected)
  }

  private final Map<? extends K, ? extends V> expected

  ContainsAllOf(Map<? extends K, ? extends V> expected) {
    this.expected = expected
  }

  @Override
  protected boolean matchesSafely(Map<? extends K, ? extends V> item, Description mismatchDescription) {
    def isMatch = true

    def difference = Maps.difference(item, expected)
    def onlyOnRight = difference.entriesOnlyOnRight()
    def differing = difference.entriesDiffering()

    if (!onlyOnRight.isEmpty()) {
      isMatch = false
      mismatchDescription.appendText("Missing the keys: ")
                         .appendValue(onlyOnRight.keySet())
    }

    if (!differing.isEmpty()) {
      isMatch = false
      if (onlyOnRight.isEmpty()) {
        mismatchDescription.appendText("W")
      } else {
        mismatchDescription.appendText(" and w")
      }
      mismatchDescription.appendText("ith different values: ")
                         .appendValue(differing)
    }

    return isMatch
  }

  @Override
  void describeTo(Description description) {
    description.appendText("a map containing all of ")
               .appendValue(expected)
  }
}
