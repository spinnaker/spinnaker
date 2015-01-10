package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoService
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.Client
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.notifications.ManualTriggerPollingNotificationAgent.NOTIFICATION_TYPE

class ManualTriggerPollingNotificationAgentSpec extends Specification {

  def mapper = new ObjectMapper()
  def echoService = Stub(EchoService)
  def jesqueClient = Mock(Client)
  @Subject
      agent = new ManualTriggerPollingNotificationAgent(mapper, echoService, jesqueClient)

  def "incoming notifications are placed on a task queue"() {
    when:
    agent.handleNotification([[content: "foo"]])

    then:
    1 * jesqueClient.enqueue(NOTIFICATION_TYPE, { Job job ->
      job.args == event.content
    })

    where:
    event = [[content: "foo"]]
  }

  private static
  final Header APPLICATION_JSON = new Header("Content-Type", "application/json")

  private Response success(content) {
    def body = mapper.writeValueAsString(content)
    new Response("http://echo", 200, "OK", [APPLICATION_JSON], new TypedString(body))
  }

}
