/*
 * Copyright 2026 Ericsson AB
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
package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.MonitorBuildJobTask
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class GitlabCiStageSpec extends Specification {

  def "should resolve the stage type to gitlabCi"() {
    expect:
    new GitlabCiStage().type == "gitlabCi"
  }

  @Unroll
  def "should wait for completion if set in the stage context"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master           : "ericsson-gitlab",
        job              : "my-group/my-project",
        buildNumber      : 4,
        waitForCompletion: waitForCompletion
      ]
    }

    when:
    def tasks = gitlabCiStage.buildTaskGraph(stage)

    then:
    (tasks.findAll { it.implementingClass == MonitorBuildJobTask }.size() == 1) == didWaitForCompletion

    where:
    waitForCompletion || didWaitForCompletion
    true              || true
    "true"            || true
    false             || false
    "false"           || false
  }

  def "should wait for completion when waitForCompletion is absent"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master     : "ericsson-gitlab",
        job        : "my-group/my-project",
        buildNumber: 4
      ]
    }

    when:
    def tasks = gitlabCiStage.buildTaskGraph(stage)

    then:
    tasks.findAll { it.implementingClass == MonitorBuildJobTask }.size() == 1
  }

  def "should bind artifacts when expected artifacts are defined"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master           : "ericsson-gitlab",
        job              : "my-group/my-project",
        buildNumber      : 4,
        expectedArtifacts: [
          [
            matchArtifact: [
              type: "docker/image"
            ]
          ],
        ]
      ]
    }

    when:
    def tasks = gitlabCiStage.buildTaskGraph(stage)

    then:
    tasks.findAll { it.implementingClass == BindProducedArtifactsTask }.size() == 1
  }

  def "should not bind artifacts when no expected artifacts are defined"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master     : "ericsson-gitlab",
        job        : "my-group/my-project",
        buildNumber: 4
      ]
    }

    when:
    def tasks = gitlabCiStage.buildTaskGraph(stage)

    then:
    tasks.findAll { it.implementingClass == BindProducedArtifactsTask }.size() == 0
  }
}
