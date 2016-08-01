/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant

class LinearToParallelMigrationSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)
  def clock = Mock(Clock)

  @Subject
  def migration = new LinearToParallelMigration(clock: clock, pipelineDAO: pipelineDAO)

  @Unroll
  def "should set refId and requisiteStageRefIds when parallel = false"() {
    given:
    def pipeline = new Pipeline([
        stages: [
            [name: "stage1"],
            [name: "stage2"]
        ]
    ]) + additionalPipelineContext


    when:
    migration.run()

    then:
    pipeline.parallel == true
    pipeline.stages == [
        [name: "stage1", refId: "0", requisiteStageRefIds: []],
        [name: "stage2", refId: "1", requisiteStageRefIds: ["0"]]

    ]
    1 * pipelineDAO.all() >> { return [pipeline] }

    where:
    additionalPipelineContext || _
    [:]                       || _
    [parallel: false]         || _
  }

  @Unroll
  def "should only be valid until Sept 1st 2016"() {
    given:
    _ * clock.instant() >> { Instant.ofEpochMilli(Date.parse("yyyy-MM-dd", now).getTime()) }

    expect:
    migration.isValid() == isValid

    where:
    now          || isValid
    "2016-08-31" || true
    "2016-09-01" || false
    "2016-09-02" || false
  }
}
