package com.netflix.spinnaker.orca.notifications.manual

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.notifications.NoopNotificationHandler
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.Client
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.notifications.manual.ManualTriggerPollingNotificationAgent.NOTIFICATION_TYPE

class ManualTriggerPollingNotificationAgentSpec extends Specification {

  def mapper = new ObjectMapper()
  def echoService = Stub(EchoService)
  def jesqueClient = Mock(Client)
  @Subject agent = new ManualTriggerPollingNotificationAgent(
      mapper, echoService, jesqueClient, [new ManualTriggerNotificationHandler()]
  )

  def "incoming notifications are placed on a task queue"() {
    when:
    agent.handleNotification(event)

    then:
    1 * jesqueClient.enqueue(NOTIFICATION_TYPE, { Job job ->
      job.vars == event[0].content
    })

    where:
    event = [[content: [pipeline: "foo"]]]
  }

  def "the queued task type is based on the handler"() {
    when:
    agent.handleNotification(event)

    then:
    1 * jesqueClient.enqueue(NOTIFICATION_TYPE, { Job job ->
      job.className == ManualTriggerNotificationHandler.name
    })

    where:
    event = [[content: [pipeline: "foo"]]]
  }

  def "if there are multiple cooperating handlers the task is queued for each"() {
    given:
    @Subject agent = new ManualTriggerPollingNotificationAgent(
        mapper,
        echoService,
        jesqueClient,
        [new ManualTriggerNotificationHandler(), new BuildJobNotificationHandler(), new TestNotificationHandler()]
    )

    def jobNames = []
    jesqueClient.enqueue(*_) >> { String queue, Job job -> jobNames << job.className; return }

    when:
    agent.handleNotification(event)

    then:
    jobNames == [ManualTriggerNotificationHandler.name, TestNotificationHandler.name]

    where:
    event = [[content: [pipeline: "foo"]]]
  }

  def "a notification for multiple triggers is enqueued multiple times"() {
    when:
    agent.handleNotification(event)

    then:
    1 * jesqueClient.enqueue(NOTIFICATION_TYPE, { Job job ->
      job.vars == event[0].content
    })
    1 * jesqueClient.enqueue(NOTIFICATION_TYPE, { Job job ->
      job.vars == event[1].content
    })

    where:
    event = [
        [content: [pipeline: "foo"]],
        [content: [pipeline: "bar"]]
    ]
  }

  private static
  final Header APPLICATION_JSON = new Header("Content-Type", "application/json")

  private Response success(content) {
    def body = mapper.writeValueAsString(content)
    new Response("http://echo", 200, "OK",
        [APPLICATION_JSON], new TypedString(body))
  }

}

/**
 * A duplicate handler for the notification type.
 */
class TestNotificationHandler extends NoopNotificationHandler {
  @Override
  boolean handles(String type) {
    return NOTIFICATION_TYPE
  }
}