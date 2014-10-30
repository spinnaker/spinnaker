/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.google.common.collect.ImmutableMap
import spock.lang.Specification
import spock.lang.Subject

class PipelineStageContextSpec extends Specification {

  @Subject map = new PipelineStageContext()

  void "should not allow null values to be written to parent map"() {
    given:
    map.put("foo", null)

    expect:
    !map.foo
  }

  void "should be able to get an ImmutableMap from a PipelineContext, sans keys with null values"() {
    given:
    map.put("foo", "bar")

    and:
    map.put("bar", "baz")

    and:
    map.put("baz", null)

    expect:
    ImmutableMap.copyOf(map).keySet() == ['foo', 'bar'] as Set
  }
}
