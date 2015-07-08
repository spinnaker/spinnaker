package com.netflix.spinnaker.echo.api

class Notification {
  Type notificationType
  Collection<String> to
  String templateGroup
  Severity severity

  Source source
  Map<String, Object> additionalContext = [:]

  static class Source {
    String executionType
    String executionId
    String application
  }

  static enum Type {
    HIPCHAT,
    EMAIL,
    SMS
  }

  static enum Severity {
    NORMAL,
    HIGH
  }
}
