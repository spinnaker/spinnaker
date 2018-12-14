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

import retrofit.client.Response
import retrofit.http.*

interface SlackClient {

  @FormUrlEncoded
  @POST('/api/chat.postMessage')
  Response sendMessage(
    @Field('token') String token,
    @Field('attachments') String attachments,
    @Field('channel') String channel,
    @Field('as_user') boolean asUser)

  /**
   * Documentation: https://api.slack.com/incoming-webhooks
   */
  @POST('/services/{token}')
  Response sendUsingIncomingWebHook(
    @Path(value = "token", encode = false) String token,
    @Body SlackRequest slackRequest)

}
