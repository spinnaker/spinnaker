package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.echo.EchoService
import spock.lang.Specification
import spock.lang.Subject

class NotifyEchoSpec extends Specification {


  @Subject
  NotifyEchoTask task = new NotifyEchoTask()

  void "should send notification"() {
    setup:
    task.echo = Mock(EchoService)

    SimpleTaskContext context = new SimpleTaskContext()
    context.application = "myapp"
    context."notification.type" = "testtype"
    context."randomAttr" = 'random'

    when:
    task.execute(context)

    then:
    1 * task.echo.recordEvent(
      {
        it.details.type == 'testtype' &&
        it.details.application == 'myapp' &&
        it.details.source == 'kato' &&
        it.content.randomAttr == 'random'
      }
    )
  }

  void 'does not send an event if echo is not configured'(){
    setup:
    task.echo = null

    SimpleTaskContext context = new SimpleTaskContext()

    when:
    task.execute(context)

    then:
    0 * task.echo._

  }

}
