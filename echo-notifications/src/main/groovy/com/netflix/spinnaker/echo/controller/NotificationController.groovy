package com.netflix.spinnaker.echo.controller

import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.api.Notification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/notifications")
@RestController
class NotificationController {
  @Autowired
  Collection<NotificationService> notificationServices

  @RequestMapping(method = RequestMethod.POST)
  void create(@RequestBody Notification notification) {
    notificationServices.find { it.supportsType(notification.notificationType) }?.handle(notification)
  }
}
