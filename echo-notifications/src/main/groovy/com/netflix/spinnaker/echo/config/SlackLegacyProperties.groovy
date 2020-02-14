/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config

import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "slack")
@Qualifier("slackLegacyConfig")
class SlackLegacyProperties {
  final static String SLACK_INCOMING_WEBHOOK = 'https://hooks.slack.com/services'
  final static String SLACK_CHAT_API = 'https://slack.com'

  private String _baseUrl
  String token
  boolean forceUseIncomingWebhook = false
  boolean sendCompactMessages = false
  boolean expandUserNames = false

  boolean getUseIncomingWebhook() {
    return forceUseIncomingWebhook || isIncomingWebhookToken(token)
  }

  void setUseIncomingWebHook() { }

  boolean isIncomingWebhookToken(String token) {
    return (StringUtils.isNotBlank(token) && token.count("/") >= 2)
  }

  void setBaseUrl(String baseUrl) {
    this._baseUrl = baseUrl
  }

  String getBaseUrl() {
    if (StringUtils.isNotBlank(_baseUrl)) {
      return _baseUrl
    } else {
      return useIncomingWebhook ? SLACK_INCOMING_WEBHOOK : SLACK_CHAT_API;
    }
  }
}
