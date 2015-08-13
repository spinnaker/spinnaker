package com.netflix.spinnaker.echo.test

import com.netflix.spinnaker.echo.model.BuildEvent
import com.netflix.spinnaker.echo.pipelinetriggers.BuildEventMonitor

import java.util.concurrent.atomic.AtomicInteger

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Pipeline.Trigger
import retrofit.RetrofitError
import retrofit.client.Response
import rx.Observable
import static com.netflix.spinnaker.echo.model.BuildEvent.Result.BUILDING
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE
import static retrofit.RetrofitError.httpError
import static rx.Observable.just

trait RetrofitStubs {

  final String url = "http://echo"
  final Trigger enabledJenkinsTrigger = new Trigger(true, "jenkins", "master", "job", null)
  final Trigger disabledJenkinsTrigger = new Trigger(false, "jenkins", "master", "job", null)
  final Trigger nonJenkinsTrigger = new Trigger(true, "not jenkins", "master", "job", null)

  private nextId = new AtomicInteger(1)

  Observable<List<BuildEvent>> echoResponse(BuildEvent... events) {
    just events.toList()
  }

  RetrofitError unavailable() {
    httpError(url, new Response(url, HTTP_UNAVAILABLE, "Unavailable", [], null), null, null)
  }

  BuildEvent createBuildEventWith(BuildEvent.Result result) {
    def build = result ? new BuildEvent.Build(result == BUILDING, 1, result) : null
    new BuildEvent(new BuildEvent.Content(
      new BuildEvent.Project("job", build), "master"),
      new BuildEvent.Details(BuildEventMonitor.ECHO_EVENT_TYPE)
    )
  }

  Pipeline createPipelineWith(Trigger... triggers) {
    Pipeline.builder().
      application("application").
      name("name").
      id("${nextId.getAndIncrement()}").
      triggers(triggers.toList()).
      build()
  }
}
