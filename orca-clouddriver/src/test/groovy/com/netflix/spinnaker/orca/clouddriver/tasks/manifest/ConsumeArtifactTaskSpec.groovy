/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.ConsumeArtifactTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class ConsumeArtifactTaskSpec extends Specification {

  def oortService = Mock(OortService)
  def artifactResolver = Stub(ArtifactResolver) {
    getBoundArtifactForId(*_) >> new Artifact()
  }

  @Subject
  ConsumeArtifactTask task = new ConsumeArtifactTask(
    artifactResolver,
    oortService,
    new RetrySupport()
  )

  def "parses JSON artifact into task outputs"() {
    given:
    def stage = new Stage(Stub(Execution), "consumeArtifact", [
      artifactId: "12345",
      artifactAccount: "test",
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.fetchArtifact(*_) >> new Response('http://oort.com', 200, 'Okay', [], new TypedString(response))
    0 * oortService._
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs == expected

    where:
    response                           | expected
    '{"foo": "bar"}'                   | ['foo': 'bar']
    '{"foo": "bar", "tobenull": null}' | ['foo': 'bar']
  }
}
