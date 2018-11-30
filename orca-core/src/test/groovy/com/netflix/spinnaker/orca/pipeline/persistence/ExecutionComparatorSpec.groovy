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
package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.pipeline.model.Execution
import spock.lang.Specification

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.*
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.NATURAL
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.REVERSE_BUILD_TIME
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.START_TIME_OR_ID

class ExecutionComparatorSpec extends Specification {

  def "sorting naturally orders by ID"() {
    given:
    List<Execution> executions = [
      new Execution(PIPELINE, "3", "foo"),
      new Execution(PIPELINE, "1", "foo"),
      new Execution(PIPELINE, "2", "foo")
    ]

    when:
    executions.sort(NATURAL)

    then:
    assert executions*.id == ["3", "2", "1"]
  }

  def "sort by START_TIME_OR_ID"() {
    given:
    List<Execution> executions = [
      new Execution(PIPELINE, "3", "foo").with { startTime = 1000L; it },
      new Execution(PIPELINE, "1", "foo").with { startTime = 1000L; it },
      new Execution(PIPELINE, "2", "foo").with { startTime = 1500L; it },
      new Execution(PIPELINE, "4", "foo")
    ]

    when:
    executions.sort(START_TIME_OR_ID)

    then:
    assert executions*.id == ["4", "2", "3", "1"]
  }

  def "sort by REVERSE_BUILD_TIME"() {
    given:
    List<Execution> executions = [
      new Execution(PIPELINE, "3", "foo").with { buildTime = 1000L; it },
      new Execution(PIPELINE, "1", "foo").with { buildTime = 1000L; it },
      new Execution(PIPELINE, "2", "foo").with { buildTime = 1500L; it },
      new Execution(PIPELINE, "4", "foo")
    ]

    when:
    executions.sort(REVERSE_BUILD_TIME)

    then:
    assert executions*.id == ["2", "3", "1", "4"]
  }
}
