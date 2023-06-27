package com.netflix.spinnaker.echo.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem
import com.netflix.spinnaker.echo.model.trigger.*
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.PubsubEventHandler
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import retrofit.RetrofitError
import retrofit.client.Response

import java.util.concurrent.atomic.AtomicInteger

import static com.netflix.spinnaker.echo.model.trigger.BuildEvent.Result.BUILDING
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE
import static retrofit.RetrofitError.httpError

trait RetrofitStubs {

  final String url = "http://echo"
  final Trigger enabledJenkinsTrigger = Trigger.builder().enabled(true).type('jenkins').master('master').job('job').build()
  final Trigger disabledJenkinsTrigger = Trigger.builder().enabled(false).type('jenkins').master('master').job('job').build()
  final Trigger enabledJenkinsTriggerWithRunAsUser = enabledJenkinsTrigger.withRunAsUser("user@managed-service-account")
  final Trigger enabledTravisTrigger = Trigger.builder().enabled(true).type('travis').master('master').job('job').build()
  final Trigger disabledTravisTrigger = Trigger.builder().enabled(false).type('travis').master('master').job('job').build()
  final Trigger enabledWerckerTrigger = Trigger.builder().enabled(true).type('wercker').master('master').job('job').build()
  final Trigger disabledWerckerTrigger = Trigger.builder().enabled(false).type('wercker').master('master').job('job').build()
  final Trigger enabledConcourseTrigger = Trigger.builder().enabled(true).type('concourse').master('master').job('job').build()
  final Trigger disabledConcourseTrigger = Trigger.builder().enabled(false).type('concourse').master('master').job('job').build()
  final Trigger nonJenkinsTrigger = Trigger.builder().enabled(true).type('not jenkins').master('master').job('job').build()
  final Trigger enabledStashTrigger = Trigger.builder().enabled(true).type('git').source('stash').project('project').slug('slug').build()
  final Trigger disabledStashTrigger = Trigger.builder().enabled(false).type('git').source('stash').project('project').slug('slug').build()
  final Trigger enabledBitBucketTrigger = Trigger.builder().enabled(true).type('git').source('bitbucket').project('project').slug('slug').build()
  final Trigger disabledBitBucketTrigger = Trigger.builder().enabled(false).type('git').source('bitbucket').project('project').slug('slug').build()

  final Trigger enabledGithubTrigger = Trigger.builder().enabled(true).type('git').source('github').project('project').slug('slug').build()

  final Trigger enabledDockerTrigger = Trigger.builder().enabled(true).type('docker').account('account').repository('repository').tag('tag').build()
  final Trigger disabledDockerTrigger = Trigger.builder().enabled(false).type('docker').account('account').repository('repository').tag('tag').build()
  final Trigger enabledWebhookTrigger = Trigger.builder().enabled(true).type('webhook').build()
  final Trigger disabledWebhookTrigger = Trigger.builder().enabled(false).type('webhook').build()
  final Trigger nonWebhookTrigger = Trigger.builder().enabled(true).type('not webhook').build()
  final Trigger webhookTriggerWithConstraints = Trigger.builder().enabled(true).type('webhook').payloadConstraints(["application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger webhookTriggerWithoutConstraints = Trigger.builder().enabled(true).type('webhook').payloadConstraints().build()
  final Trigger teamcityTriggerWithConstraints = Trigger.builder().enabled(true).type('teamcity').payloadConstraints(["application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger teamcityTriggerWithoutConstraints = Trigger.builder().enabled(true).type('teamcity').payloadConstraints().build()

  final Trigger enabledGooglePubsubTrigger = Trigger.builder()
      .enabled(true).type('pubsub').pubsubSystem('google').subscriptionName('projects/project/subscriptions/subscription').expectedArtifactIds([]).build()
  final Trigger disabledGooglePubsubTrigger = Trigger.builder()
      .enabled(false).type('pubsub').pubsubSystem('google').subscriptionName('projects/project/subscriptions/subscription').expectedArtifactIds([]).build()
  final Trigger enabledHelmTrigger = Trigger.builder().enabled(true).type('helm').account('account').version('1.0.0').digest('digest').build()
  final Trigger disabledHelmTrigger = Trigger.builder().enabled(false).type('helm').account('account').version('1.0.0').digest('digest').build()
  final Trigger enabledCDEventsTrigger = Trigger.builder().enabled(true).type('cdevents').build()
  final Trigger disabledCDEventsTrigger = Trigger.builder().enabled(false).type('cdevents').build()
  private nextId = new AtomicInteger(1)

  RetrofitError unavailable() {
    httpError(url, new Response(url, HTTP_UNAVAILABLE, "Unavailable", [], null), null, null)
  }

  BuildEvent createBuildEventWith(BuildEvent.Result result) {
    def build = result ? new BuildEvent.Build(result == BUILDING, 1, result, null, []) : null
    def res = new BuildEvent()
    res.content = new BuildEvent.Content(new BuildEvent.Project("job", build), "master")
    res.details = new Metadata([type: BuildEvent.TYPE])
    return res
  }

  GitEvent createGitEvent(String eventSource) {
    def res = new GitEvent()
    res.content = new GitEvent.Content("project", "slug", "hash", "master", "action", [])
    res.details = new Metadata([type: GitEvent.TYPE, source: eventSource])
    return res
  }

  DockerEvent createDockerEvent(String inTag) {
    return createDockerEvent(inTag, null)
  }

  DockerEvent createDockerEvent(String inTag, String inDigest) {
    def tag = "tag"
    if (inTag) {
      tag = inTag
    }
    def digest = "sha"
    if (inDigest) {
      digest = inDigest
    }
    def res = new DockerEvent()
    res.content = new DockerEvent.Content("account", "registry", "repository", tag, digest)
    res.details = new Metadata([type: DockerEvent.TYPE, source: "spock"])
    return res
  }

  WebhookEvent createWebhookEvent(final String source) {
    return createWebhookEvent(source, [:])
  }

  WebhookEvent createWebhookEvent(final String source, final Map payload) {
    def res = new WebhookEvent()
    res.details = new Metadata([type: WebhookEvent.TYPE, source: source])
    res.payload = payload
    res.content = EchoObjectMapper.getInstance().convertValue(payload, WebhookEvent.Content)
    return res
  }

  CDEvent createCDEvent(final String source) {
    return createCDEvent(source, [:])
  }

  CDEvent createCDEvent(final String source, final Map payload) {
    def res = new CDEvent()
    res.details = new Metadata([type: CDEvent.TYPE, source: source])
    res.payload = payload
    res.content = EchoObjectMapper.getInstance().convertValue(payload, CDEvent.Content)
    return res
  }

  CDEvent createCDEventRequestHeaders(final String source, final Map payload, final TreeMap requestHeaders) {
    def res = new CDEvent()
    res.details = new Metadata([type: CDEvent.TYPE, source: source])
    res.payload = payload
    res.content = EchoObjectMapper.getInstance().convertValue(payload, CDEvent.Content)
    res.details.requestHeaders = requestHeaders
    return res
  }

  PubsubEvent createPubsubEvent(PubsubSystem pubsubSystem, String subscriptionName, List<Artifact> artifacts, Map payload) {
    def res = new PubsubEvent()

    def description = MessageDescription.builder()
        .pubsubSystem(pubsubSystem)
        .ackDeadlineSeconds(1)
        .subscriptionName(subscriptionName)
        .artifacts(artifacts)
        .build()

    def content = new PubsubEvent.Content()
    content.setMessageDescription(description)

    res.details = new Metadata([type: PubsubEventHandler.PUBSUB_TRIGGER_TYPE])
    res.content = content
    res.payload = payload
    return res
  }

  Pipeline createPipelineWith(Trigger... triggers) {
    Pipeline.builder()
      .application("application")
      .name("name")
      .id("${nextId.getAndIncrement()}")
      .triggers(triggers.toList())
      .build()
  }

  Pipeline createPipelineWith(List<ExpectedArtifact> expectedArtifacts, Trigger... triggers) {
    Pipeline.builder()
      .application("application")
      .name("name")
      .id("${nextId.getAndIncrement()}")
      .triggers(triggers.toList())
      .expectedArtifacts(expectedArtifacts)
      .build()
  }

  HelmEvent createHelmEvent(String version = "1.0.0") {
    def res = new HelmEvent()
    res.content = new HelmEvent.Content("account", "chart", version, "digest")
    res.details = new Metadata([type: HelmEvent.TYPE, source: "spock"])
    return res
  }
}
