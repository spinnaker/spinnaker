/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema

import spock.lang.Specification
import spock.lang.Unroll

/**
 * MapMergeSpec.
 */
class MapMergeSpec extends Specification {

  @Unroll
  def "test merging cases #description"() {
    expect:
    MapMerge.merge(orig, override) == expected

    where:
    description           | orig                               | override                   | expected
    "null"                | null                               | null                       | [:]
    "empty"               | [:]                                | [:]                        | [:]
    "empty orig"          | [:]                                | [a: 'b']                   | [a: 'b']
    "unique keys"         | [a: 'b']                           | [c: 'd']                   | [a: 'b', c: 'd']
    "simple merge"        | [a: 'b', c: 'd']                   | [c: 'e']                   | [a: 'b', c: 'e']
    "collection replace"  | [a: ['b', 'c']]                    | [a: ['d', 'e']]            | [a: ['d', 'e']]
    "map merge"           | [a: [b: 'c', d: 'e', f: [g: 'h']]] | [a: [d: 'E', f: [i: 'j']]] | [a: [b: 'c', d: 'E', f: [g: 'h', i: 'j']]]
    "null removes simple" | [a: 'b', c: 'd']                   | [a: null]                  | [c: 'd']
    "null removes map"    | [a: [b: [c: 'd'], e: 'f']]         | [a: [b: null]]             | [a: [e: 'f']]
  }

  @Unroll
  def "error condition handling #description"() {
    when:
    MapMerge.merge(orig, override)

    then:
    def exception = thrown(IllegalStateException)
    exception.message == expected

    where:
    description                      | orig            | override | expected
    "map with non map"               | [a: [b: 'c']]   | [a: "b"] | "Attempt to merge Map with String"
    "collection with non collection" | [a: ['a', 'b']] | [a: 'b'] | "Attempt to replace Collection with String"
  }
}
