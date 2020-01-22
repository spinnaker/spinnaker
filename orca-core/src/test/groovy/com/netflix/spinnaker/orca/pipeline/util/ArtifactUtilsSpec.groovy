/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import rx.Observable
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ArtifactUtilsSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  def pipelineId = "abc"

  def expectedExecutionCriteria = {
    def criteria = new ExecutionRepository.ExecutionCriteria()
    criteria.setPageSize(1)
    return criteria
  }()

  def executionRepository = Stub(ExecutionRepository) {
    // only a call to retrievePipelinesForPipelineConfigId() with these argument values is expected
    retrievePipelinesForPipelineConfigId(pipelineId, expectedExecutionCriteria) >> Observable.empty()
    // any other interaction is unexpected
    0 * _
  }

  def makeArtifactUtils() {
    return makeArtifactUtilsWithStub(executionRepository)
  }

  def makeArtifactUtilsWithStub(ExecutionRepository executionRepositoryStub) {
    return new ArtifactUtils(new ObjectMapper(), executionRepositoryStub,
      new ContextParameterProcessor())
  }

  def "should resolve expressions in stage-inlined artifacts"() {
    setup:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
      }
    }

    execution.trigger = new DefaultTrigger('manual')
    execution.trigger.other['buildNumber'] = 100
    execution.trigger.artifacts.add(Artifact.builder().type('http/file').name('build/libs/my-jar-100.jar').build())

    when:
    def artifact = makeArtifactUtils().getBoundArtifactForStage(execution.stages[0], null, Artifact.builder()
      .type('http/file')
      .name('build/libs/my-jar-${trigger[\'buildNumber\']}.jar')
      .build())

    then:
    artifact.name == 'build/libs/my-jar-100.jar'
  }

  def "should find upstream artifacts in small pipeline"() {
    when:
    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifacts = artifactUtils.getArtifacts(desired)
    artifacts.size == 3
    artifacts.find { it.type == "1" } != null
    artifacts.find { it.type == "2" } != null
    artifacts.find { it.type == "extra" } != null

    where:
    execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        name = "upstream stage"
        type = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [Artifact.builder().type("2").build(), Artifact.builder().type("extra").build()]
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["2"]
      }
    }
  }

  def "should find upstream artifacts only"() {
    when:
    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifacts = artifactUtils.getArtifacts(desired)
    artifacts.size == 1
    artifacts.find { it.type == "1" } != null

    where:
    execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        name = "upstream stage"
        type = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [Artifact.builder().type("2").build()]
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["1"]
      }
    }
  }

  def "should find artifacts from trigger and upstream stages"() {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["1"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [Artifact.builder().type("trigger").build()])

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifacts = artifactUtils.getArtifacts(desired)
    artifacts.size == 2
    artifacts.find { it.type == "1" } != null
    artifacts.find { it.type == "trigger" } != null
  }

  def "should find no artifacts"() {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["1"]
      }
    }

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifacts = artifactUtils.getArtifacts(desired)
    artifacts.size == 0
  }

  def "should find a bound artifact from upstream stages"() {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.resolvedExpectedArtifacts = [
          ExpectedArtifact.builder().id("1").boundArtifact(Artifact.builder().type("correct").build()).build(),
          ExpectedArtifact.builder().id("2").boundArtifact(Artifact.builder().type("incorrect").build()).build()
        ]
      }
      stage {
        name = "desired"
        type = "stage3"
        refId = "3"
        requisiteStageRefIds = ["1"]
      }
    }

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifact = artifactUtils.getBoundArtifactForId(desired, "1")
    artifact != null
    artifact.type == "correct"
  }

  def "should find a bound artifact from a trigger"() {
    given:
    def correctArtifact = Artifact.builder().type("correct").build()
    def incorrectArtifact = Artifact.builder().type("incorrect").build()

    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.resolvedExpectedArtifacts = [
          ExpectedArtifact.builder().id("2").boundArtifact(incorrectArtifact).build()
        ]
      }
      stage {
        name = "desired"
        type = "stage3"
        refId = "3"
        requisiteStageRefIds = ["1"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook")
    execution.trigger.resolvedExpectedArtifacts = [ExpectedArtifact.builder().id("1").boundArtifact(correctArtifact).build()]

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactUtils = makeArtifactUtils()

    then:
    def artifact = artifactUtils.getBoundArtifactForId(desired, "1")
    artifact != null
    artifact.type == "correct"
  }

  def "should find all artifacts from an execution, in reverse order"() {
    when:
    def execution = pipeline {
      stage {
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [Artifact.builder().type("2").build()]
      }
      stage {
        // This stage does not emit an artifact
        requisiteStageRefIds = ["2"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [Artifact.builder().type("trigger").build()])

    def artifactUtils = makeArtifactUtils()

    then:
    def artifacts = artifactUtils.getAllArtifacts(execution)
    artifacts.size == 3
    artifacts*.type == ["2", "1", "trigger"]
  }

  def "should find artifacts from a specific pipeline"() {
    when:
    def execution = pipeline {
      id: pipelineId
      status: ExecutionStatus.SUCCEEDED
      stage {
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [Artifact.builder().type("2").build()]
      }
      stage {
        // This stage does not emit an artifacts
        requisiteStageRefIds = ["2"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [Artifact.builder().type("trigger").build()])

    def executionCriteria = new ExecutionRepository.ExecutionCriteria()
    executionCriteria.setStatuses(ExecutionStatus.SUCCEEDED)

    def executionTerminalCriteria = new ExecutionRepository.ExecutionCriteria()
    executionTerminalCriteria.setStatuses(ExecutionStatus.TERMINAL)

    def executionRepositoryStub = Stub(ExecutionRepository) {
      // only a call to retrievePipelinesForPipelineConfigId() with these argument values is expected
      retrievePipelinesForPipelineConfigId(pipelineId, executionCriteria) >> Observable.just(execution)
      retrievePipelinesForPipelineConfigId(pipelineId, executionTerminalCriteria) >> Observable.empty()
      // any other interaction is unexpected
      0 * _
    }

    def artifactUtils = makeArtifactUtilsWithStub(executionRepositoryStub)

    then:
    def artifacts = artifactUtils.getArtifactsForPipelineId(pipelineId, executionCriteria)
    artifacts.size == 3
    artifacts*.type == ["2", "1", "trigger"]

    def emptyArtifacts = artifactUtils.getArtifactsForPipelineId(pipelineId, executionTerminalCriteria)
    emptyArtifacts == []
  }

  def "should find artifacts without a specific stage ref"() {
    when:
    def execution = pipeline {
      id: pipelineId
      stage {
        refId = "1"
        outputs.artifacts = [Artifact.builder().type("1").build()]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [Artifact.builder().type("2").build()]
      }
      stage {
        // This stage does not emit an artifacts
        requisiteStageRefIds = ["2"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [Artifact.builder().type("trigger").build()])

    def executionRepositoryStub = Stub(ExecutionRepository) {
      // only a call to retrievePipelinesForPipelineConfigId() with these argument values is expected
      retrievePipelinesForPipelineConfigId(pipelineId, expectedExecutionCriteria) >> Observable.just(execution)
      // any other interaction is unexpected
      0 * _
    }

    def artifactUtils = makeArtifactUtilsWithStub(executionRepositoryStub)

    then:
    def artifacts = artifactUtils.getArtifactsForPipelineIdWithoutStageRef(pipelineId, "2", expectedExecutionCriteria)
    artifacts.size == 2
    artifacts*.type == ["1", "trigger"]
  }

  def "resolveArtifacts sets the bound artifact on an expected artifact"() {
    given:
    def matchArtifact = Artifact.builder().type("docker/.*").build()
    def expectedArtifact = ExpectedArtifact.builder().matchArtifact(matchArtifact).build()
    def receivedArtifact = Artifact.builder().name("my-artifact").type("docker/image").build()
    def pipeline = [
      id: "abc",
      trigger: [:],
      expectedArtifacts: [expectedArtifact],
      receivedArtifacts: [receivedArtifact],
    ]
    def artifactUtils = makeArtifactUtils()

    when:
    artifactUtils.resolveArtifacts(pipeline)
    List<ExpectedArtifact> resolvedArtifacts = objectMapper.convertValue(
      pipeline.trigger.resolvedExpectedArtifacts,
      new TypeReference<List<ExpectedArtifact>>() {})

    then:
    resolvedArtifacts.size() == 1
    resolvedArtifacts.get(0).getBoundArtifact() == receivedArtifact
  }

  def "resolveArtifacts adds received artifacts to the trigger, skipping duplicates"() {
    given:
    def matchArtifact = Artifact.builder().name("my-pipeline-artifact").type("docker/.*").build()
    def expectedArtifact = ExpectedArtifact.builder().matchArtifact(matchArtifact).build()
    def receivedArtifact = Artifact.builder().name("my-pipeline-artifact").type("docker/image").build()
    def triggerArtifact = Artifact.builder().name("my-trigger-artifact").type("docker/image").build()
    def bothArtifact = Artifact.builder().name("my-both-artifact").type("docker/image").build()
    def pipeline = [
      id: "abc",
      trigger: [
          artifacts: [triggerArtifact, bothArtifact]
      ],
      expectedArtifacts: [expectedArtifact],
      receivedArtifacts: [receivedArtifact, bothArtifact],
    ]
    def artifactUtils = makeArtifactUtils()

    when:
    artifactUtils.resolveArtifacts(pipeline)

    then:
    List<Artifact> triggerArtifacts = extractTriggerArtifacts(pipeline.trigger)
    triggerArtifacts.size() == 3
    triggerArtifacts == [receivedArtifact, bothArtifact, triggerArtifact]
  }

  def "resolveArtifacts is idempotent"() {
    given:
    def matchArtifact = Artifact.builder().name("my-pipeline-artifact").type("docker/.*").build()
    def expectedArtifact = ExpectedArtifact.builder().matchArtifact(matchArtifact).build()
    def receivedArtifact = Artifact.builder().name("my-pipeline-artifact").type("docker/image").build()
    def triggerArtifact = Artifact.builder().name("my-trigger-artifact").type("docker/image").build()
    def bothArtifact = Artifact.builder().name("my-both-artifact").type("docker/image").build()
    def pipeline = [
      id: "abc",
      trigger: [
        artifacts: [triggerArtifact, bothArtifact]
      ],
      expectedArtifacts: [expectedArtifact],
      receivedArtifacts: [receivedArtifact, bothArtifact],
    ]
    def artifactUtils = makeArtifactUtils()

    when:
    artifactUtils.resolveArtifacts(pipeline)
    List<Artifact> initialArtifacts = extractTriggerArtifacts(pipeline.trigger)
    artifactUtils.resolveArtifacts(pipeline)
    List<Artifact> finalArtifacts = extractTriggerArtifacts(pipeline.trigger)

    then:
    initialArtifacts == finalArtifacts
  }

  private List<Artifact> extractTriggerArtifacts(Map<String, Object> trigger) {
    return objectMapper.convertValue(trigger.artifacts, new TypeReference<List<Artifact>>(){});
  }
}
