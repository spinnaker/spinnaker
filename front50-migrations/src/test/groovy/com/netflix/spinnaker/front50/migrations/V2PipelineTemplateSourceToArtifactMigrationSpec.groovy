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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.Specification
import spock.lang.Subject

class V2PipelineTemplateSourceToArtifactMigrationSpec extends Specification {

  def pipelineDAO = Mock(PipelineDAO)
  def objectMapper = new ObjectMapper()

  @Subject
  def migration = new V2PipelineTemplateSourceToArtifactMigration(pipelineDAO, objectMapper)

  def "should migrate template source to artifact reference"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      schema     : "v2",
      template   : [
        source: "spinnaker://templateId"
      ]
    ])

    pipeline.id = "pipeline-1"

    when:
    migration.run()
    migration.run() // subsequent migration run should be a no-op

    then:
    pipeline.template.reference == "spinnaker://templateId"
    pipeline.template.artifactAccount == "front50ArtifactCredentials"
    pipeline.template.type == "front50/pipelineTemplate"

    2 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("pipeline-1", _)
    0 * _
  }

  def "should not migrate non-Front50 template source"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      schema     : "v2",
      template   : [
        source: "file://templateId"
      ]
    ])

    pipeline.id = "pipeline-1"

    when:
    migration.run()

    then:
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update("pipeline-1", _)
    0 * _
  }
}
