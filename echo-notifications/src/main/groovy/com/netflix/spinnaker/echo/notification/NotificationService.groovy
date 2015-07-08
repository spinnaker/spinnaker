package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification

interface NotificationService {
  boolean supportsType(Notification.Type type)
  void handle(Notification notification)
}
