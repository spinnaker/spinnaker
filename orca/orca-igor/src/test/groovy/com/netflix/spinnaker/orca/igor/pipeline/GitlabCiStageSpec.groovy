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

  def "should default the property file to the gitlab-ci sentinel when absent"() {
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
    gitlabCiStage.buildTaskGraph(stage)

    then:
    // GitLab CI has no real property file; the sentinel lets Igor scrape SPINNAKER_PROPERTY_*
    // lines from the log without the user supplying a meaningless value (mirrors Travis).
    stage.context.propertyFile == "gitlab-ci"
  }

  @Unroll
  def "should default the property file to the gitlab-ci sentinel when blank (#description)"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master      : "ericsson-gitlab",
        job         : "my-group/my-project",
        buildNumber : 4,
        propertyFile: propertyFile
      ]
    }

    when:
    gitlabCiStage.buildTaskGraph(stage)

    then:
    // The Deck stage sends an empty-string propertyFile (present-but-blank), so a plain
    // putIfAbsent would not override it -- blank must be treated as "not supplied".
    stage.context.propertyFile == "gitlab-ci"

    where:
    propertyFile || description
    ""           || "empty string"
    null         || "explicit null"
  }

  def "should not override a user-supplied property file"() {
    given:
    def gitlabCiStage = new GitlabCiStage()

    def stage = stage {
      type = "gitlabCi"
      context = [
        master      : "ericsson-gitlab",
        job         : "my-group/my-project",
        buildNumber : 4,
        propertyFile: "custom.properties"
      ]
    }

    when:
    gitlabCiStage.buildTaskGraph(stage)

    then:
    stage.context.propertyFile == "custom.properties"
  }
}
