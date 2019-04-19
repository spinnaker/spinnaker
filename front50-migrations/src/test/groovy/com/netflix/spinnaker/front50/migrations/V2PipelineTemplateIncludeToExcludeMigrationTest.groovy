/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.Specification
import spock.lang.Subject

class V2PipelineTemplateIncludeToExcludeMigrationTest extends Specification {
  def pipelineDAO = Mock(PipelineDAO)

  @Subject
  def migration = new V2PipelineTemplateIncludeToExcludeMigration(pipelineDAO)

  def "should trigger migration from inherit to exclude"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      schema     : "v2",
      inherit    : inherit,
      exclude    : exclude,
      template   : [
        source: "spinnaker://templateId"
      ]
    ])

    pipeline.id = "pipeline-1"

    when:
    migration.run()
    migration.run() // subsequent migration run should be a no-op

    then:
    if (expectedExclude == null) { // Can't sort null lists, so don't if the expected is null.
      pipeline.exclude == expectedExclude
    } else {
      Collections.sort(pipeline.exclude) == Collections.sort(expectedExclude)
    }

    2 * pipelineDAO.all() >> { return [pipeline] }
    nUpdates * pipelineDAO.update("pipeline-1", _)
    0 * _

    where:
    inherit              | exclude      | nUpdates | expectedExclude
    null                 | null         | 0        | null
    null                 | []           | 0        | []
    null                 | ["anything"] | 0        | ["anything"]
    []                   | []           | 0        | []
    ["triggers"]         | ["anything"] | 0        | ["anything"]
    []                   | null         | 1        | ["triggers", "notifications", "parameters"]
    ["triggers"]         | null         | 1        | ["notifications", "parameters"]
    ["blah", "triggers"] | null         | 1        | ["notifications", "parameters"]
  }
}
