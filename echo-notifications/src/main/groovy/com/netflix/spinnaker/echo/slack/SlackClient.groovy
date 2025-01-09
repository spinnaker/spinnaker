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

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SlackClient {

  /**
   * Post a message via Slack API
   *
   * see https://api.slack.com/methods/chat.postMessage
   * One note: linkUserNames according to slack API is a boolean. But guess what? It doesn't work, it must be an int 0 or 1
   */
  @FormUrlEncoded
  @POST('/api/chat.postMessage')
  Call<ResponseBody> sendMessage(
    @Field('token') String token,
    @Field('attachments') String attachments,
    @Field('channel') String channel,
    @Field('as_user') boolean asUser,
    @Field('link_names') Integer linkUserNames)

  /**
   * Documentation: https://api.slack.com/incoming-webhooks
   */
  @POST('/{token}')
  Call<ResponseBody> sendUsingIncomingWebHook(
    @Path(value = "token", encoded = true) String token,
    @Body SlackRequest slackRequest)

  /**
   * Documentation: https://api.slack.com/methods/users.info
   */
  @GET('/api/users.info')
  Call<SlackService.SlackUserInfo> getUserInfo(
    @Query('token') String token,
    @Query('user') String userId
  )
}
