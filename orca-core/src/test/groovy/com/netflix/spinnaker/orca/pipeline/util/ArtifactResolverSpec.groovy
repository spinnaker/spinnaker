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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ArtifactResolverSpec extends Specification {
  def makeArtifactResolver() {
    return new ArtifactResolver(new ObjectMapper(), Mock(ExecutionRepository))
  }

  def "should find upstream artifacts in small pipeline"() {
    when:
    def desired = execution.getStages().find { it.name == "desired" }
    def artifactResolver = makeArtifactResolver()

    then:
    def artifacts = artifactResolver.getArtifacts(desired)
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
          outputs.artifacts = [new Artifact(type: "1")]
        }
        stage {
          name = "upstream stage"
          type = "stage2"
          refId = "2"
          requisiteStageRefIds = ["1"]
          outputs.artifacts = [new Artifact(type: "2"), new Artifact(type: "extra")]
        }
        stage {
          name = "desired"
          requisiteStageRefIds = ["2"]
        }
      }
  }

  def "should find upstream artifacts only" () {
    when:
    def desired = execution.getStages().find { it.name == "desired" }
    def artifactResolver = makeArtifactResolver()

    then:
    def artifacts = artifactResolver.getArtifacts(desired)
    artifacts.size == 1
    artifacts.find { it.type == "1" } != null

    where:
    execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.artifacts = [new Artifact(type: "1")]
      }
      stage {
        name = "upstream stage"
        type = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [new Artifact(type: "2")]
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["1"]
      }
    }
  }

  def "should find artifacts from trigger and upstream stages" () {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.artifacts = [new Artifact(type: "1")]
      }
      stage {
        name = "desired"
        requisiteStageRefIds = ["1"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [new Artifact(type: "trigger")])

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactResolver = makeArtifactResolver()

    then:
    def artifacts = artifactResolver.getArtifacts(desired)
    artifacts.size == 2
    artifacts.find { it.type == "1" } != null
    artifacts.find { it.type == "trigger" } != null
  }

  def "should find no artifacts" () {
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
    def artifactResolver = makeArtifactResolver()

    then:
    def artifacts = artifactResolver.getArtifacts(desired)
    artifacts.size == 0
  }

  def "should find a bound artifact from upstream stages" () {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.resolvedExpectedArtifacts = [
            new ExpectedArtifact(id: "1", boundArtifact: new Artifact(type: "correct")),
            new ExpectedArtifact(id: "2", boundArtifact: new Artifact(type: "incorrect"))
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
    def artifactResolver = makeArtifactResolver()

    then:
    def artifact = artifactResolver.getBoundArtifactForId(desired, "1")
    artifact != null
    artifact.type == "correct"
  }

  def "should find a bound artifact from a trigger" () {
    when:
    def execution = pipeline {
      stage {
        name = "upstream stage"
        type = "stage1"
        refId = "1"
        outputs.resolvedExpectedArtifacts = [
            new ExpectedArtifact(id: "2", boundArtifact: new Artifact(type: "incorrect"))
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
    execution.trigger.resolvedExpectedArtifacts = [new ExpectedArtifact(id: "1", boundArtifact: new Artifact(type: "correct"))]

    def desired = execution.getStages().find { it.name == "desired" }
    def artifactResolver = makeArtifactResolver()

    then:
    def artifact = artifactResolver.getBoundArtifactForId(desired, "1")
    artifact != null
    artifact.type == "correct"
  }

  @Unroll
  def "should find a matching artifact for #expected"() {
    when:
    def artifactResolver = makeArtifactResolver()
    def artifact = artifactResolver.resolveSingleArtifact(expected, existing, true)

    then:
    artifact == desired

    where:
    expected                                                                            | existing                                                                                             || desired
    new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/image"))             | [new Artifact(type: "docker/image"), new Artifact(type: "amazon/ami")]                               || new Artifact(type: "docker/image")
    new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*"))                | [new Artifact(type: "docker/image"), new Artifact(type: "amazon/ami")]                               || new Artifact(type: "docker/image")
    new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*", name: "image")) | [new Artifact(type: "docker/image", name: "bad"), new Artifact(type: "docker/image", name: "image")] || new Artifact(type: "docker/image", name: "image")
  }

  @Unroll
  def "should fail find a matching artifact for #expected"() {
    when:
    def artifactResolver = makeArtifactResolver()
    def artifact = artifactResolver.resolveSingleArtifact(expected, existing, true)

    then:
    artifact == null

    where:
    expected                                                                           | existing
    new ExpectedArtifact(matchArtifact: new Artifact(type: "image/image"))             | [new Artifact(type: "docker/image"), new Artifact(type: "amazon/ami")]
    new ExpectedArtifact(matchArtifact: new Artifact(type: "flocker/.*"))              | [new Artifact(type: "docker/image"), new Artifact(type: "amazon/ami")]
    new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*", name: "none")) | [new Artifact(type: "docker/image", name: "bad"), new Artifact(type: "docker/image", name: "image")]
  }

  def "should find all artifacts from an execution, in reverse order" () {
    when:
    def execution = pipeline {
      stage {
        refId = "1"
        outputs.artifacts = [new Artifact(type: "1")]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        outputs.artifacts = [new Artifact(type: "2")]
      }
      stage {
        // This stage does not emit an artifact
        requisiteStageRefIds = ["2"]
      }
    }
    execution.trigger = new DefaultTrigger("webhook", null, "user", [:], [new Artifact(type: "trigger")])

    def artifactResolver = makeArtifactResolver()

    then:
    def artifacts = artifactResolver.getAllArtifacts(execution)
    artifacts.size == 3
    artifacts*.type == ["2", "1", "trigger"]
  }

  @Unroll
  def "should resolve expected artifacts from pipeline for #expectedArtifacts using #available and prior #prior"() {
    when:
    def artifactResolver = makeArtifactResolver()
    def bound = artifactResolver.resolveExpectedArtifacts(expectedArtifacts, available, prior, true)

    then:
    bound.size() == expectedBound.size()
    bound.findAll({ a -> expectedBound.contains(a) }).size() == bound.size()

    where:
    expectedArtifacts                                                                                                                                                                                                                                   | available                                                   | prior                                || expectedBound
    [new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*"))]                                                                                                                                                                              | [new Artifact(type: "docker/image")]                        | null                                 || [new Artifact(type: "docker/image")]
    [new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*"), useDefaultArtifact: true, defaultArtifact: new Artifact(type: "google/image"))]                                                                                               | [new Artifact(type: "bad")]                                 | null                                 || [new Artifact(type: "google/image")]
    [new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*"), usePriorArtifact: true)]                                                                                                                                                      | [new Artifact(type: "bad")]                                 | [new Artifact(type: "docker/image")] || [new Artifact(type: "docker/image")]
    [new ExpectedArtifact(matchArtifact: new Artifact(type: "google/.*"), usePriorArtifact: true), new ExpectedArtifact(matchArtifact: new Artifact(type: "docker/.*"), useDefaultArtifact: true, defaultArtifact: new Artifact(type: "docker/image"))] | [new Artifact(type: "bad"), new Artifact(type: "more bad")] | [new Artifact(type: "google/image")] || [new Artifact(type: "docker/image"), new Artifact(type: "google/image")]
  }
}
