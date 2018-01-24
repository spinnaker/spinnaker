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
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.pipelinetriggers.monitor.GitEventMonitor
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.functions.Action1
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GitEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def pipelineCache = Mock(PipelineCache)
  def subscriber = Mock(Action1)
  def registry = Stub(Registry) {
    createId(*_) >> Stub(Id)
    counter(*_) >> Stub(Counter)
    gauge(*_) >> Integer.valueOf(1)
  }

  @Subject
  def monitor = new GitEventMonitor(pipelineCache, subscriber, registry)

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event                       | trigger
    createGitEvent("stash")     | enabledStashTrigger
    createGitEvent("bitbucket") | enabledBitBucketTrigger
  }

  def "attaches stash trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledStashTrigger.type
      it.trigger.project == enabledStashTrigger.project
      it.trigger.slug == enabledStashTrigger.slug
      it.trigger.hash == event.content.hash
    })

    where:
    event = createGitEvent("stash")
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledStashTrigger, disabledStashTrigger)
  }

  def "attaches bitbucket trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledBitBucketTrigger.type
      it.trigger.project == enabledBitBucketTrigger.project
      it.trigger.slug == enabledBitBucketTrigger.slug
      it.trigger.hash == event.content.hash
    })

    where:
    event = createGitEvent("bitbucket")
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledBitBucketTrigger, disabledBitBucketTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    given:
    pipelineCache.getPipelines() >> pipelines

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    pipelines.size() * subscriber.call(_ as Pipeline)

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

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
    pipelineCache.getPipelines() >> [badPipeline, goodPipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

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
    pipelineCache.getPipelines() >> [badPipeline, goodPipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(gitEvent, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(gitEvent, Event))

    then:
    0 * subscriber._

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
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(gitEvent, Event))

    then:
    callCount * subscriber._

    where:
    secret | signature                                  | callCount
    null   | null                                       | 1
    "foo"  | null                                       | 1
    null   | "foo"                                      | 0 // No secret defined in trigger
    "foo"  | "foo"                                      | 0 // Signatures don't match
    "foo"  | "67af18bbedab68252b01902ac0a8d7095ca93692" | 1 // Signatures match! Generated by http://www.freeformatter.com/hmac-generator.html
  }
}
