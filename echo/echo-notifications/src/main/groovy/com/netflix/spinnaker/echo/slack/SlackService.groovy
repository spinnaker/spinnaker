/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.echo.slack

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.echo.config.SlackLegacyProperties
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import groovy.json.JsonBuilder
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import okhttp3.ResponseBody
import retrofit2.Response

@Canonical
@Slf4j
class SlackService {
  SlackClient slackClient
  SlackLegacyProperties config

  ResponseBody sendCompactMessage(CompactSlackMessage message, String channel, boolean asUser) {
    Retrofit2SyncCall.execute(slackClient.sendMessage(config.token, message.buildMessage(), channel, asUser, config.expandUserNames ? 1 : 0))
  }

  ResponseBody sendMessage(SlackAttachment message, String channel, boolean asUser) {
    config.useIncomingWebhook ?
      Retrofit2SyncCall.execute(slackClient.sendUsingIncomingWebHook(config.token, new SlackRequest([message], channel))) :
      Retrofit2SyncCall.execute(slackClient.sendMessage(config.token, toJson(message), channel, asUser, config.expandUserNames ? 1 : 0))
  }


  SlackUserInfo getUserInfo(String userId) {
    Retrofit2SyncCall.execute(slackClient.getUserInfo(config.token, userId))
  }


  def static toJson(message) {
    "[" + new JsonBuilder(message).toPrettyString() + "]"
  }

  // Partial view into the response from Slack, but enough for our needs
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class SlackUserInfo {
    String id
    String name
    String realName
    String email
    boolean deleted
    boolean has2fa

    @JsonProperty('user')
    private void unpack(Map user) {
      this.id = user.id
      this.name = user.name
      this.realName = user.real_name
      this.deleted = user.deleted
      this.has2fa = user.has_2fa
      this.email = user.profile.email
    }
  }
}
