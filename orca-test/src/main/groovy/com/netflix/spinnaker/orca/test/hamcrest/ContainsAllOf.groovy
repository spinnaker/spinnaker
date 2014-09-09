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
    def difference = Maps.difference(item, expected)
    return difference.entriesOnlyOnRight().isEmpty() && difference.entriesDiffering().isEmpty()
  }

  @Override
  void describeTo(Description description) {

  }
}
