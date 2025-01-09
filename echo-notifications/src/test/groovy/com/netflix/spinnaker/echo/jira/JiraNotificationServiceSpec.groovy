package com.netflix.spinnaker.echo.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Unroll
import retrofit2.mock.Calls;


class JiraNotificationServiceSpec extends Specification {

  def jiraService = Mock(JiraService)
  def retrySupport = new RetrySupport()
  def objectMapper = EchoObjectMapper.getInstance()
  JiraNotificationService service = new JiraNotificationService(jiraService, retrySupport, objectMapper)

  @Unroll
  void 'Handles Jira transition, calls comment if comment is set'() {
    given:
    def notification = new Notification(
      notificationType: "JIRA",
      source: new Notification.Source(user: "foo@example.com"),
      additionalContext: [
        jiraIssue: "EXMPL-0000",
        comment: comment,
        transitionContext: [
          transition: [
            name: "Done"
          ]
        ]
      ]
    )

    when:
    service.handle(notification)

    then:
    1 * jiraService.getIssueTransitions(_) >> Calls.response(new JiraService.IssueTransitions(transitions: [new JiraService.IssueTransitions.Transition(name: "Done", id: "4")]))
    1 * jiraService.transitionIssue(_, _) >> Calls.response(null)
    addCommentCall * jiraService.addComment(_, _) >> Calls.response(null)

    where:
    comment         || addCommentCall
    null            || 0
    "testing 1234"  || 1
  }
}
