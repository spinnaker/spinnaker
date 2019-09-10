/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.pipeline.expressions.functions

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.pipeline.expressions.functions.ArtifactExpressionFunctionProvider.triggerResolvedArtifact
import static com.netflix.spinnaker.orca.pipeline.expressions.functions.ArtifactExpressionFunctionProvider.triggerResolvedArtifactByType
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline


class ArtifactExpressionFunctionProviderSpec extends Specification {
  @Shared
  def pipeline1 = pipeline {
    def matchArtifact1 = new Artifact(type: "docker/image", "name": "artifact1")
    def boundArtifact = new Artifact(type: "docker/image", "name": "artifact1", "reference": "artifact1")

    trigger = new DefaultTrigger("manual", "artifact1")
    trigger.resolvedExpectedArtifacts = [
      new ExpectedArtifact(matchArtifact: matchArtifact1, boundArtifact: boundArtifact),
    ]
  }

  def "triggerResolvedArtifact returns resolved trigger artifact by name"() {
    when:
    def artifact = triggerResolvedArtifact(pipeline1, "artifact1")

    then:
    artifact.type == "docker/image"
    artifact.name == "artifact1"
  }

  def "triggerResolvedArtifactByType returns resolved trigger artifact by type"() {
    when:
    def artifact = triggerResolvedArtifactByType(pipeline1, "docker/image")

    then:
    artifact.type == "docker/image"
    artifact.name == "artifact1"
  }


  def "triggerResolvedArtifact throws when artifact is not found"() {
    when:
    triggerResolvedArtifact(pipeline1, "somename")

    then:
    thrown(SpelHelperFunctionException)
  }

  def "triggerResolvedArtifactByType throws if artifact is not found"() {
    when:
    triggerResolvedArtifactByType(pipeline1, "s3/object")

    then:
    thrown(SpelHelperFunctionException)
  }
}
