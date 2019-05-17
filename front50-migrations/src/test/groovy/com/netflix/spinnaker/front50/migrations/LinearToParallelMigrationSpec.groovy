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
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class LinearToParallelMigrationSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)
  def pipelineStrategyDAO = Mock(PipelineStrategyDAO)
  def clock = Mock(Clock)

  @Subject
  def migration = new LinearToParallelMigration(
      clock: clock, pipelineDAO: pipelineDAO, pipelineStrategyDAO: pipelineStrategyDAO
  )

  @Unroll
  def "should set refId and requisiteStageRefIds when parallel = false"() {
    given:
    def pipeline = new Pipeline([
        stages: [
            [name: "stage1"],
            [name: "stage2"]
        ]
    ]) + additionalPipelineContext
    pipeline.id = "pipeline-1"

    def pipelineStrategy = new Pipeline([
        stages: [
            [name: "stage1"],
            [name: "stage2"]
        ]
    ]) + additionalPipelineContext
    pipelineStrategy.id = "pipelineStrategy-1"

    when:
    migration.run()
    migration.run() // subsequent migration run should be a no-op

    then:
    pipeline.parallel == true
    pipeline.stages == [
        [name: "stage1", refId: "0", requisiteStageRefIds: []],
        [name: "stage2", refId: "1", requisiteStageRefIds: ["0"]]

    ]
    pipelineStrategy.parallel == true
    pipelineStrategy.stages == [
        [name: "stage1", refId: "0", requisiteStageRefIds: []],
        [name: "stage2", refId: "1", requisiteStageRefIds: ["0"]]

    ]

    2 * pipelineDAO.all() >> { return [pipeline] }
    2 * pipelineStrategyDAO.all() >> { return [pipelineStrategy] }
    1 * pipelineDAO.update("pipeline-1", _)
    1 * pipelineStrategyDAO.update("pipelineStrategy-1", _)
    0 * _

    where:
    additionalPipelineContext || _
    [:]                       || _
    [parallel: false]         || _
  }

  @Unroll
  def "should only be valid until November 1st 2016"() {
    given:
    _ * clock.instant() >> { LocalDate.parse(now).atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant() }

    expect:
    migration.isValid() == isValid

    where:
    now          || isValid
    "2016-10-31" || true
    "2016-11-01" || false
    "2016-11-02" || false
  }
}
