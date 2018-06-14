package com.netflix.spinnaker.orca.webhook.exception

class PreconfiguredWebhookUnauthorizedException extends RuntimeException {
  PreconfiguredWebhookUnauthorizedException(String user, String webhookKey) {
    super("User '$user' is not allowed to execute stage named '$webhookKey'")
  }
}
