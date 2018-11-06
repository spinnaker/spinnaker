/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.GitEventHandler
import com.netflix.spinnaker.echo.test.RetrofitStubs
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GitEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = new ObjectMapper()

  @Subject
  def eventHandler = new GitEventHandler(registry, objectMapper)

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    event                       | trigger
    createGitEvent("stash")     | enabledStashTrigger
    createGitEvent("bitbucket") | enabledBitBucketTrigger
  }

  def "attaches stash trigger to the pipeline"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledStashTrigger.type
    matchingPipelines[0].trigger.project == enabledStashTrigger.project
    matchingPipelines[0].trigger.slug == enabledStashTrigger.slug
    matchingPipelines[0].trigger.hash == event.content.hash

    where:
    event = createGitEvent("stash")
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledStashTrigger, disabledStashTrigger)
  }

  def "attaches bitbucket trigger to the pipeline"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledBitBucketTrigger.type
    matchingPipelines[0].trigger.project == enabledBitBucketTrigger.project
    matchingPipelines[0].trigger.slug == enabledBitBucketTrigger.slug
    matchingPipelines[0].trigger.hash == event.content.hash

    where:
    event = createGitEvent("bitbucket")
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledBitBucketTrigger, disabledBitBucketTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == pipelines.size()

    where:
    event = createGitEvent("stash")
    pipelines = (1..2).collect {
      Pipeline.builder()
        .application("application")
        .name("pipeline$it")
        .id("id")
        .triggers([enabledStashTrigger])
        .build()
    }
  }

  @Unroll
  def "does not trigger #description pipelines"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger              | description
    disabledStashTrigger | "disabled stash trigger"
    nonJenkinsTrigger    | "non-Jenkins"

    pipeline = createPipelineWith(trigger)
    event = createGitEvent("stash")
  }

  @Unroll
  def "does not trigger #description pipelines for stash"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                       | description
    disabledStashTrigger                          | "disabled stash trigger"
    enabledStashTrigger.withSlug("notSlug")       | "different slug"
    enabledStashTrigger.withSource("github")      | "different source"
    enabledStashTrigger.withProject("notProject") | "different project"
    enabledStashTrigger.withBranch("notMaster")   | "different branch"

    pipeline = createPipelineWith(trigger)
    event = createGitEvent("stash")
  }

  @Unroll
  def "does not trigger #description pipelines for bitbucket"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                           | description
    disabledBitBucketTrigger                          | "disabled bitbucket trigger"
    enabledBitBucketTrigger.withSlug("notSlug")       | "different slug"
    enabledBitBucketTrigger.withSource("github")      | "different source"
    enabledBitBucketTrigger.withProject("notProject") | "different project"
    enabledBitBucketTrigger.withBranch("notMaster")   | "different branch"

    pipeline = createPipelineWith(trigger)
    event = createGitEvent("bitbucket")
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled stash trigger with missing #field"() {
    given:
    def pipelines = [badPipeline, goodPipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == goodPipeline.id

    where:
    trigger                               | field
    enabledStashTrigger.withSlug(null)    | "slug"
    enabledStashTrigger.withProject(null) | "project"
    enabledStashTrigger.withSource(null)  | "source"

    event = createGitEvent("stash")
    goodPipeline = createPipelineWith(enabledStashTrigger)
    badPipeline = createPipelineWith(trigger)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled bitbucket trigger with missing #field"() {
    given:
    def pipelines = [badPipeline, goodPipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == goodPipeline.id

    where:
    trigger                                   | field
    enabledBitBucketTrigger.withSlug(null)    | "slug"
    enabledBitBucketTrigger.withProject(null) | "project"
    enabledBitBucketTrigger.withSource(null)  | "source"

    event = createGitEvent("bitbucket")
    goodPipeline = createPipelineWith(enabledBitBucketTrigger)
    badPipeline = createPipelineWith(trigger)
  }

  @Unroll
  def "triggers events on branch when #description"() {
    given:
    def gitEvent = createGitEvent("stash")
    gitEvent.content.branch = eventBranch
    def trigger = enabledStashTrigger.atBranch(triggerBranch)
    def pipeline = createPipelineWith(trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    eventBranch         | triggerBranch       | description
    'whatever'          | null                | 'no branch set in trigger'
    'whatever'          | ""                  | 'empty string in trigger'
    'master'            | 'master'            | 'branches are identical'
    'ref/origin/master' | 'ref/origin/master' | 'branches have slashes'
    'regex12345'        | 'regex.*'           | 'branches match pattern'
  }

  @Unroll
  def "does not triggers events on branch on mistmatch branch"() {
    given:
    def gitEvent = createGitEvent("stash")
    gitEvent.content.branch = eventBranch
    def trigger = enabledStashTrigger.atBranch(triggerBranch)
    def pipeline = createPipelineWith(trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    eventBranch  | triggerBranch
    'master'     | 'featureBranch'
    'regex12345' | 'not regex.*'
  }

  @Unroll
  def "computes and compares GitHub signature, if available"() {
    def gitEvent = createGitEvent("github")
    gitEvent.rawContent = "toBeHashed"
    gitEvent.details.source = "github"
    if (signature) {
      gitEvent.details.requestHeaders.add("X-Hub-Signature", "sha1=" + signature)
    }

    def trigger = enabledGithubTrigger.atSecret(secret).atBranch("master")

    def pipeline = createPipelineWith(trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines)

    then:
    matchingPipelines.size() == callCount

    where:
    secret | signature                                  | callCount
    null   | null                                       | 1
    "foo"  | null                                       | 0
    null   | "foo"                                      | 0 // No secret defined in trigger
    "foo"  | "foo"                                      | 0 // Signatures don't match
    "foo"  | "67af18bbedab68252b01902ac0a8d7095ca93692" | 1 // Signatures match! Generated by http://www.freeformatter.com/hmac-generator.html
  }
}
