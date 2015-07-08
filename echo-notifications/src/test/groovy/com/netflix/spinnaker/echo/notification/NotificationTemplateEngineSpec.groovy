package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import org.apache.velocity.app.VelocityEngine
import spock.lang.Specification

import static NotificationTemplateEngine.*

class NotificationTemplateEngineSpec extends Specification {
  void "should favor type-specific template when available"() {
    given:
    def engine = Mock(VelocityEngine) {
      1 * resourceExists(_) >> { specificTemplateExists }
    }

    when:
    def determinedTemplate = determinateTemplate(engine, templateGroup, type, notificationType)

    then:
    determinedTemplate == expectedTemplate

    where:
    templateGroup | type      | notificationType        | specificTemplateExists || expectedTemplate
    "group"       | Type.BODY | Notification.Type.EMAIL | true                   || "group/body-email.vm"
    "group"       | Type.BODY | Notification.Type.EMAIL | false                  || "group/body.vm"
  }
}
