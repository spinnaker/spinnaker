/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.tasks.artifacts

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.FindArtifactFromExecutionTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import spock.lang.Specification
import spock.lang.Subject

class FindArtifactFromExecutionTaskSpec extends Specification {
  def PIPELINE = "my pipeline"
  def EXECUTION_CRITERIA = new ExecutionRepository.ExecutionCriteria().setStatuses(Collections.singletonList("SUCCEEDED"))
  def ARTIFACT_A = new Artifact(type: "kubernetes/replicaSet")
  def ARTIFACT_B = new Artifact(type: "kubernetes/configMap")

  ArtifactResolver artifactResolver = Mock(ArtifactResolver)
  Execution execution = Mock(Execution)

  @Subject
  FindArtifactFromExecutionTask task = new FindArtifactFromExecutionTask(artifactResolver)

  def "finds a single artifact"() {
    given:
    def expectedArtifacts = [new ExpectedArtifact(matchArtifact: ARTIFACT_A)]
    def pipelineArtifacts = [ARTIFACT_A, ARTIFACT_B]
    Set resolvedArtifacts = [ARTIFACT_A]
    def stage = new Stage(execution, "findArtifactFromExecution", [
      executionOptions: [
        succeeded: true
      ],
      expectedArtifacts: expectedArtifacts,
      pipeline: PIPELINE
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * artifactResolver.getArtifactsForPipelineId(PIPELINE, EXECUTION_CRITERIA) >> pipelineArtifacts
    1 * artifactResolver.resolveExpectedArtifacts(expectedArtifacts, pipelineArtifacts, null, false) >> resolvedArtifacts
    result.context.resolvedExpectedArtifacts == expectedArtifacts
    result.context.artifacts == resolvedArtifacts
  }

  def "finds multiple artifacts"() {
    given:
    def expectedArtifacts = [new ExpectedArtifact(matchArtifact: ARTIFACT_A), new ExpectedArtifact(matchArtifact: ARTIFACT_B)]
    def pipelineArtifacts = [ARTIFACT_A, ARTIFACT_B]
    Set resolvedArtifacts = [ARTIFACT_A, ARTIFACT_B]
    def stage = new Stage(execution, "findArtifactFromExecution", [
      executionOptions: [
        succeeded: true
      ],
      expectedArtifacts: expectedArtifacts,
      pipeline: PIPELINE
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * artifactResolver.getArtifactsForPipelineId(PIPELINE, EXECUTION_CRITERIA) >> pipelineArtifacts
    1 * artifactResolver.resolveExpectedArtifacts(expectedArtifacts, pipelineArtifacts, null, false) >> resolvedArtifacts
    result.context.resolvedExpectedArtifacts == expectedArtifacts
    result.context.artifacts == resolvedArtifacts
  }
}
