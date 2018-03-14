/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.netflix.spinnaker.orca.pipeline.model.GitTrigger
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger.BuildInfo

class AppEngineBranchFinderSpec extends Specification {
  @Unroll
  def "(git trigger) should resolve branch in trigger if it matches regex (if provided). If no regex is provided, the branch from the trigger will be used."() {
    given:
    def trigger = new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", triggerBranch, "orca")

    def operation = [
      trigger: [
        hash   : "c681a6af-1096-4727-ac9e-70d3b2460228",
        source : "github",
        project: "spinnaker",
        slug   : "orca",
        branch : operationBranchRegex,
      ]
    ]

    expect:
    AppEngineBranchFinder.fromGitTrigger(operation, trigger) == result

    where:
    triggerBranch | operationBranchRegex || result
    "test-branch" | "test-\\w+"          || "test-branch"
    "test-branch" | null                 || "test-branch"
  }

  def "(git trigger) should throw appropriate error if method cannot resolve a branch"() {
    given:
    def trigger = new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca")

    def operation = [
      trigger      : [
        hash   : "c681a6af-1096-4727-ac9e-70d3b2460228",
        source : "github",
        project: "spinnaker",
        slug   : "orca",
        branch : "[0-9]+",
      ],
      repositoryUrl: "https://github.com/spinnaker/orca.git"
    ]

    when:
    AppEngineBranchFinder.fromGitTrigger(operation, trigger)

    then:
    IllegalStateException e = thrown(IllegalStateException)
    e.message == "No branch found for repository https://github.com/spinnaker/orca.git in trigger context."
  }

  @Unroll
  def "(jenkins trigger) should resolve branch, using regex (if provided) to narrow down options"() {
    given:
    def trigger = new JenkinsTrigger("Jenkins", "poll_git_repo", 1, null)
    trigger.buildInfo = new BuildInfo("poll_git_repo", 1, "http://jenkins", [], scm, false, "SUCCESS")

    def operation = [
      trigger: [
        master            : "Jenkins",
        job               : "poll_git_repo",
        matchBranchOnRegex: matchBranchOnRegex
      ]
    ]

    expect:
    AppEngineBranchFinder.fromJenkinsTrigger(operation, trigger) == result

    where:
    scm                                           | matchBranchOnRegex || result
    [[branch: "branch"]]                          | null               || "branch"
    [[branch: "branch"], [branch: "test-branch"]] | "test-\\w+"        || "test-branch"
  }

  @Unroll
  def "(jenkins trigger) should throw appropriate error if method cannot resolve exactly one branch"() {
    given:
    def trigger = new JenkinsTrigger("Jenkins", "poll_git_repo", 1, null)
    trigger.buildInfo = new BuildInfo("poll_git_repo", 1, "http://jenkins", [], scm, false, "SUCCESS")

    def operation = [
      trigger      : [
        master            : "Jenkins",
        job               : "poll_git_repo",
        matchBranchOnRegex: matchBranchOnRegex
      ],
      repositoryUrl: "https://github.com/spinnaker/orca.git"
    ]

    when:
    AppEngineBranchFinder.fromJenkinsTrigger(operation, trigger)

    then:
    IllegalStateException e = thrown(IllegalStateException)
    e.message == message

    where:
    scm                                           | matchBranchOnRegex || message
    [[branch: "branch"], [branch: "test-branch"]] | null               || "Cannot resolve branch from options: branch, test-branch."
    [[branch: "branch"], [branch: "test-branch"]] | "\\w*-?branch"     || "Cannot resolve branch from options: branch, test-branch."
    [[branch: "no-match"]]                        | "\\w*-?branch"     || "No branch found for repository https://github.com/spinnaker/orca.git in trigger context."
  }
}
