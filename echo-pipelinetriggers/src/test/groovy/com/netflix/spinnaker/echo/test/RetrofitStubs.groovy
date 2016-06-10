package com.netflix.spinnaker.echo.test

import com.netflix.spinnaker.echo.model.Metadata
import com.netflix.spinnaker.echo.model.trigger.BuildEvent
import com.netflix.spinnaker.echo.model.trigger.DockerEvent
import com.netflix.spinnaker.echo.model.trigger.GitEvent
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent
import com.netflix.spinnaker.echo.model.Trigger

import java.util.concurrent.atomic.AtomicInteger

import com.netflix.spinnaker.echo.model.Pipeline
import retrofit.RetrofitError
import retrofit.client.Response

import static com.netflix.spinnaker.echo.model.trigger.BuildEvent.Result.BUILDING
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE
import static retrofit.RetrofitError.httpError

trait RetrofitStubs {

  final String url = "http://echo"
  final Trigger enabledJenkinsTrigger = new Trigger(true, null, 'jenkins', 'master', 'job', null, null, null, null, null, null, null, null, null, null, null, null)
  final Trigger disabledJenkinsTrigger = new Trigger(false, null, 'jenkins', 'master', 'job', null, null, null, null, null, null, null, null, null, null, null, null)
  final Trigger nonJenkinsTrigger = new Trigger(true, null, 'not jenkins', 'master', 'job', null, null, null, null, null, null, null, null, null, null, null, null)
  final Trigger enabledStashTrigger = new Trigger(true, null, 'git', null, null, null, null, null, 'stash', 'project', 'slug', null, null, null, null, null, null)
  final Trigger disabledStashTrigger = new Trigger(false, null, 'git', 'master', 'job', null, null, null, 'stash', 'project', 'slug', null, null, null, null, null, null)
  final Trigger enabledDockerTrigger = new Trigger(true, null, 'docker', null, null, null, null, null, null, null, null, null, 'registry', 'repository', 'tag', null, null)
  final Trigger disabledDockerTrigger = new Trigger(false, null, 'git', null, null, null, null, null, null, null, null, null, 'registry', 'repository', 'tag', null, null)
  final Trigger enabledWebhookTrigger = new Trigger(true, null, 'webhook', null, null, null, null, null, null, null, null, null, null, null, null, null, null)
  final Trigger disabledWebhookTrigger = new Trigger(false, null, 'webhook', null, null, null, null, null, null, null, null, null, null, null, null, null, null)
  final Trigger nonWebhookTrigger = Trigger.builder().enabled(true).type('not webhook').build()
  final Trigger webhookTriggerWithConstraints = Trigger.builder().enabled(true).type('webhook').constraints([ "application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger webhookTriggerWithoutConstraints = Trigger.builder().enabled(true).type('webhook').constraints().build()
  final Trigger teamcityTriggerWithConstraints = Trigger.builder().enabled(true).type('teamcity').constraints([ "application": "myApplicationName", "pipeline": "myPipeLineName" ]).build()
  final Trigger teamcityTriggerWithoutConstraints = Trigger.builder().enabled(true).type('teamcity').constraints().build()

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

  GitEvent createGitEvent() {
    def res = new GitEvent()
    res.content = new GitEvent.Content("project", "slug", "hash", "master")
    res.details = new Metadata([type: GitEvent.TYPE, source: "stash"])
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
    res.payload = [ application : "myApplicationName" ];
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
