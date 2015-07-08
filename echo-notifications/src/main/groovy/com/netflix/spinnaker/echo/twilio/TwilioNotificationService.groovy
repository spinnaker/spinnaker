package com.netflix.spinnaker.echo.twilio

import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TwilioNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.SMS

  @Autowired
  TwilioService twilioService

  @Autowired
  NotificationTemplateEngine notificationTemplateEngine

  @Value('${twilio.account}')
  String account

  @Value('${twilio.from}')
  String from

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  void handle(Notification notification) {
    def body = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY)

    notification.to.each {
      twilioService.sendMessage(
          account,
          from,
          it,
          body
      )
    }
  }
}
