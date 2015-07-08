package com.netflix.spinnaker.echo.hipchat

import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HipchatNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.HIPCHAT

  @Autowired
  HipchatService hipchat

  @Autowired
  NotificationTemplateEngine notificationTemplateEngine

  @Value('${hipchat.token}')
  String token

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  void handle(Notification notification) {
    def body = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY)
    notification.to.each {
      hipchat.sendMessage(token, it, new HipchatMessage(
          message: body,
          notify: notification.severity == Notification.Severity.HIGH
      ))
    }
  }
}
