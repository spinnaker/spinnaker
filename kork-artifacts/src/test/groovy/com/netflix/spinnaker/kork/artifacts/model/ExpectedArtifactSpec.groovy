/*
 * Copyright 2018 Google, Inc.
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
package com.netflix.spinnaker.kork.artifacts.model

import spock.lang.Shared
import spock.lang.Specification

class ExpectedArtifactSpec extends Specification {
  private static final EXPECTED_STRING = 'abc.*'
  private static final MATCH_STRING = 'abcd'
  private static final NO_MATCH_STRING = 'zzz'

  @Shared typeFactory = { String input ->
    Artifact.builder().type(input).build()
  }

  @Shared nameFactory = { String input ->
    Artifact.builder().name(input).build()
  }

  @Shared versionFactory = { String input ->
    Artifact.builder().version(input).build()
  }

  @Shared locationFactory = { String input ->
    Artifact.builder().location(input).build()
  }

  @Shared referenceFactory = { String input ->
    Artifact.builder().reference(input).build()
  }

  @Shared uuidFactory = { String input ->
    Artifact.builder().uuid(input).build()
  }

  @Shared provenanceFactory = { String input ->
    Artifact.builder().provenance(input).build()
  }

  def "test regex matching"() {
    when:
    def expectedArtifact = ExpectedArtifact.builder().id("test").matchArtifact(Artifact.builder().name(expectedName).build()).build()
    def incomingArtifact = Artifact.builder().name(incomingName).build()

    then:
    expectedArtifact.matches(incomingArtifact) == result

    where:
    expectedName | incomingName | result
    'abc'        | 'abcde'      | false
    'abc.*'      | 'abcde'      | true
    'bc.*'       | 'abcde'      | false
    '.*bc.*'     | 'abcde'      | true
    'abcde$'     | 'abcde'      | true
    '^abcde$'    | 'abcde'      | true
    'abc'        | null         | false
    'abc'        | ''           | false
    ''           | 'abcde'      | true
    null         | 'abcde'      | true
  }

  def "each considered field must match"() {
    when:
    def expectedArtifact = ExpectedArtifact.builder().id("test").matchArtifact(factory(MATCH_STRING)).build()

    then:
    expectedArtifact.matches(factory(MATCH_STRING))
    !expectedArtifact.matches(factory(NO_MATCH_STRING))

    where:
    factory << [typeFactory, nameFactory, versionFactory, locationFactory, referenceFactory]
  }

  def "uuid and provenance do not need to match"() {
    when:
    def expectedArtifact = ExpectedArtifact.builder().matchArtifact(factory(MATCH_STRING)).build()

    then:
    expectedArtifact.matches(factory(MATCH_STRING))
    expectedArtifact.matches(factory(NO_MATCH_STRING))

    where:
    factory << [uuidFactory, provenanceFactory]
  }
}
