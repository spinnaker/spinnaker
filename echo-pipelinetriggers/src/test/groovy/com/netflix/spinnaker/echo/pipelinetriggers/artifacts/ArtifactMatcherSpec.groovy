/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts

import spock.lang.Specification

class ArtifactMatcherSpec extends Specification {

  def matchPayload = [
    "one": "one",
    "two": "two",
    "three": "three"
  ]

  def noMatchPayload = [
      "four": "four",
      "five": "five"
  ]

  def contstraints = [
    "one": "one"
  ]

  def shortConstraint = [
    "one": "o"
  ]

  def constraintsOR = [
      "one": ["uno", "one"]
  ]

  def payloadWithList = [
      "one": ["one"]
  ]

  def stringifiedListConstraints = [
    "one": "['uno', 'one']"
  ]

  def "matches when constraint is partial word"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(shortConstraint, matchPayload)

    then:
    result
  }

  def "matches exact string"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(contstraints, matchPayload)

    then:
    result
  }

  def "no match when constraint word not present"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(contstraints, noMatchPayload)

    then:
    !result
  }

  def "matches when payload value is in a list of constraint strings"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(constraintsOR, matchPayload)

    then:
    result
  }

  def "no match when val not present in list of constraint strings"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(constraintsOR, noMatchPayload)

    then:
    !result
  }

  def "matches when val is in stringified list of constraints"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, matchPayload)

    then:
    result
  }

  def "matches when payload contains list and constraint is a stringified list"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, payloadWithList)

    then:
    result
  }

  def "matches when payload is a list list and constraints are a list"() {
    when:
    boolean result = ArtifactMatcher.isConstraintInPayload(constraintsOR, payloadWithList)

    then:
    result
  }
}
