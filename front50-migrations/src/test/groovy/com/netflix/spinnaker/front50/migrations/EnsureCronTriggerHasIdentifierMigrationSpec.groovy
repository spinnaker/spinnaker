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

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EnsureCronTriggerHasIdentifierMigrationSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)

  @Subject
  def migration = new EnsureCronTriggerHasIdentifierMigration(pipelineDAO)

  @Unroll
  def "should set cron trigger identifier"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      triggers   : [
        [type: "cron", id: "original-id", expression: "1"],
        [type: "cron", expression: "2"],
        [type: "cron", id: "", expression: "3"]
      ]
    ])

    pipeline.id = "pipeline-1"

    when:
    migration.run()
    migration.run() // subsequent migration run should be a no-op

    then:
    pipeline.get("triggers").find { it.expression == "1" }.id == "original-id"
    pipeline.get("triggers").find { it.expression == "2" }.id.length() > 1
    pipeline.get("triggers").find { it.expression == "3" }.id.length() > 1

    2 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("pipeline-1", _)
    0 * _

    where:
    additionalPipelineContext || _
    [:]                       || _
    [parallel: false]         || _
  }
}
