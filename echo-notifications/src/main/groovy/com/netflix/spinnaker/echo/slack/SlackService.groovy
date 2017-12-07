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

import groovy.json.JsonBuilder
import groovy.transform.Canonical
import retrofit.client.Response

@Canonical
class SlackService {

  SlackClient slackClient
  boolean useIncomingWebHook

  Response sendCompactMessage(String token, CompactSlackMessage message, String channel, boolean asUser) {
    slackClient.sendMessage(token, message.buildMessage(), channel, asUser)
  }

  Response sendMessage(String token, SlackAttachment message, String channel, boolean asUser) {
    useIncomingWebHook ?
      slackClient.sendUsingIncomingWebHook(token, new SlackRequest([message], channel)) :
      slackClient.sendMessage(token, toJson(message), channel, asUser)
  }

  def static toJson(message) {
    "[" + new JsonBuilder(message).toPrettyString() + "]"
  }
}
