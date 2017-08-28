package com.netflix.spinnaker.echo.test

import com.netflix.spinnaker.echo.model.Metadata
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import com.netflix.spinnaker.echo.model.pubsub.PubsubType
import com.netflix.spinnaker.echo.model.trigger.*
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
  final Trigger enabledTravisTrigger = Trigger.builder().enabled(true).type('travis').master('master').job('job').build()
  final Trigger disabledTravisTrigger = Trigger.builder().enabled(false).type('travis').master('master').job('job').build()
  final Trigger nonJenkinsTrigger = Trigger.builder().enabled(true).type('not jenkins').master('master').job('job').build()
  final Trigger enabledStashTrigger = Trigger.builder().enabled(true).type('git').source('stash').project('project').slug('slug').build()
  final Trigger disabledStashTrigger = Trigger.builder().enabled(false).type('git').source('stash').project('project').slug('slug').build()
  final Trigger enabledBitBucketTrigger = Trigger.builder().enabled(true).type('git').source('bitbucket').project('project').slug('slug').build()
  final Trigger disabledBitBucketTrigger = Trigger.builder().enabled(false).type('git').source('bitbucket').project('project').slug('slug').build()

  final Trigger enabledGithubTrigger = Trigger.builder().enabled(true).type('git').source('github').project('project').slug('slug').build()

  final Trigger enabledDockerTrigger = Trigger.builder().enabled(true).type('docker').account('registry').repository('repository').tag('tag').build()
  final Trigger disabledDockerTrigger = Trigger.builder().enabled(false).type('docker').account('registry').repository('repository').tag('tag').build()
  final Trigger enabledWebhookTrigger = Trigger.builder().enabled(true).type('webhook').build()
  final Trigger disabledWebhookTrigger = Trigger.builder().enabled(true).type('webhook').build()
  final Trigger nonWebhookTrigger = Trigger.builder().enabled(true).type('not webhook').build()
  final Trigger webhookTriggerWithConstraints = Trigger.builder().enabled(true).type('webhook').constraints([ "application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger webhookTriggerWithoutConstraints = Trigger.builder().enabled(true).type('webhook').constraints().build()
  final Trigger teamcityTriggerWithConstraints = Trigger.builder().enabled(true).type('teamcity').constraints([ "application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger teamcityTriggerWithoutConstraints = Trigger.builder().enabled(true).type('teamcity').constraints().build()

  final Trigger enabledGooglePubsubTrigger = Trigger.builder()
      .enabled(true).type('pubsub').pubsubType('google').subscriptionName('projects/project/subscriptions/subscription').build()
  final Trigger disabledGooglePubsubTrigger = Trigger.builder()
      .enabled(false).type('pubsub').pubsubType('google').subscriptionName('projects/project/subscriptions/subscription').build()

  private nextId = new AtomicInteger(1)

  RetrofitError unavailable() {
    httpError(url, new Response(url, HTTP_UNAVAILABLE, "Unavailable", [], null), null, null)
  }

  BuildEvent createBuildEventWith(BuildEvent.Result result) {
    def build = result ? new BuildEvent.Build(result == BUILDING, 1, result) : null
    def res = new BuildEvent()
    res.content = new BuildEvent.Content(new BuildEvent.Project("job", build), "master")
    res.details = new Metadata([type: BuildEvent.TYPE])
    return res
  }

  GitEvent createGitEvent(String eventSource) {
    def res = new GitEvent()
    res.content = new GitEvent.Content("project", "slug", "hash", "master")
    res.details = new Metadata([type: GitEvent.TYPE, source: eventSource])
    return res
  }

  DockerEvent createDockerEvent(String inTag) {
    def tag = "tag"
    if (inTag) {
      tag = inTag
    }
    def res = new DockerEvent()
    res.content = new DockerEvent.Content("registry", "repository", tag, "sha")
    res.details = new Metadata([type: DockerEvent.TYPE, source: "spock"])
    return res
  }

  WebhookEvent createWebhookEvent(String type) {
    def res = new WebhookEvent()
    res.details = new Metadata([type: type, source: "myCIServer"])
    res.payload = [ application : "myApplicationName" ]
    return res
  }

  PubsubEvent createPubsubEvent(PubsubType pubsubType, String subscriptionName) {
    def res = new PubsubEvent()

    def description = MessageDescription.builder()
        .pubsubType(pubsubType)
        .ackDeadlineMillis(10000)
        .subscriptionName(subscriptionName)
        .build()

    def content = new PubsubEvent.Content()
    content.setMessageDescription(description)

    res.details = new Metadata([type: "pubsub"])
    res.content = content
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
}
