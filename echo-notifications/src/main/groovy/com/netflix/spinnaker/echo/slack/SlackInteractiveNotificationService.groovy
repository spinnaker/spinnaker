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

package com.netflix.spinnaker.echo.slack

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.InteractiveNotificationService
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import groovy.util.logging.Slf4j
import okhttp3.ResponseBody
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@Component
@ConditionalOnProperty('slack.enabled')
class SlackInteractiveNotificationService extends SlackNotificationService implements InteractiveNotificationService {
  private final static String SLACK_WEBHOOK_BASE_URL = "https://hooks.slack.com"

  private SlackAppService slackAppService
  private SlackHookService slackHookService
  private ObjectMapper objectMapper

  @Autowired
  SlackInteractiveNotificationService(
    @Qualifier("slackAppService") SlackAppService slackAppService,
    NotificationTemplateEngine notificationTemplateEngine,
    OkHttp3ClientConfiguration okHttp3ClientConfiguration,
    ObjectMapper objectMapper
  ) {
    super(slackAppService, notificationTemplateEngine)
    this.slackAppService = slackAppService as SlackAppService
    this.objectMapper = objectMapper
    this.slackHookService = getSlackHookService(okHttp3ClientConfiguration)
  }

  // For access from tests only
  SlackInteractiveNotificationService(
    @Qualifier("slackAppService") SlackAppService slackAppService,
    SlackHookService slackHookService,
    NotificationTemplateEngine notificationTemplateEngine,
    ObjectMapper objectMapper
  ) {
    super(slackAppService, notificationTemplateEngine)
    this.slackAppService = slackAppService as SlackAppService
    this.objectMapper = objectMapper
    this.slackHookService = slackHookService
  }

  private Map parseSlackPayload(String body) {
    if (!body.startsWith("payload=")) {
      throw new InvalidRequestException("Missing payload field in Slack callback request.")
    }

    Map payload = objectMapper.readValue(
      // Slack requests use application/x-www-form-urlencoded
      URLDecoder.decode(body.split("payload=")[1], "UTF-8"),
      Map)

    // currently supporting only interactive actions
    if (payload.type != "interactive_message") {
      throw new InvalidRequestException("Unsupported Slack callback type: ${payload.type}")
    }

    if (!payload.callback_id || !payload.user?.name) {
      throw new InvalidRequestException("Slack callback_id and user not present. Cannot route the request to originating Spinnaker service.")
    }

    payload
  }

  @Override
  Notification.InteractiveActionCallback parseInteractionCallback(RequestEntity<String> request) {
    // Before anything else, verify the signature on the request
    slackAppService.verifySignature(request)

    Map payload = parseSlackPayload(request.getBody())
    log.debug("Received callback event from Slack of type ${payload.type}")

    if (payload.actions.size() > 1) {
      log.warn("Expected a single selected action from Slack, but received ${payload.actions.size}")
    }

    if (payload.actions[0].type != "button") {
      throw new InvalidRequestException("Spinnaker currently only supports Slack button actions.")
    }

    def (serviceId, callbackId) = payload.callback_id.split(":")

    String user = payload.user.name
    try {
      SlackService.SlackUserInfo userInfo = slackAppService.getUserInfo(payload.user.id)
      user = userInfo.email
    } catch (Exception e) {
      log.error("Error retrieving info for Slack user ${payload.user.name} (${payload.user.id}). Falling back to username.")
    }

    new Notification.InteractiveActionCallback(
      serviceId: serviceId,
      messageId: callbackId,
      user: user,
      actionPerformed: new Notification.ButtonAction(
        name: payload.actions[0].name,
        label: payload.actions[0].text,
        value: payload.actions[0].value
      )
    )
  }

  @Override
  Optional<ResponseEntity<String>> respondToCallback(RequestEntity<String> request) {
    String body = request.getBody()
    Map payload = parseSlackPayload(body)
    log.info("Responding to Slack callback via ${payload.response_url}")

    def selectedAction = payload.actions[0]
    def attachment = payload.original_message.attachments[0] // we support a single attachment as per Echo notifications
    def selectedActionText = attachment.actions.stream().find {
      it.type == selectedAction.type && it.value == selectedAction.value
    }.text

    Map message = [:]
    message.putAll(payload.original_message)
    message.attachments[0].remove("actions")
    message.attachments[0].text += "\n\nUser <@${payload.user.id}> clicked the *${selectedActionText}* action."

    // Example: https://hooks.slack.com/actions/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
    URI responseUrl = new URI(payload.response_url)
    log.info("POST ${SLACK_WEBHOOK_BASE_URL}${responseUrl.path}: ${message}")
    ResponseBody response = Retrofit2SyncCall.execute(slackHookService.respondToMessage(responseUrl.path, message))
    log.info("Response from Slack: ${response.string()}")

    return Optional.empty()
  }

  private SlackHookService getSlackHookService(OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    log.info("Slack hook service loaded")
    new Retrofit.Builder()
      .baseUrl(SLACK_WEBHOOK_BASE_URL)
      .client(okHttp3ClientConfiguration.createForRetrofit2().build())
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
      .create(SlackHookService.class);
  }

  interface SlackHookService {
    @POST('/{path}')
    Call<ResponseBody> respondToMessage(@Path(value = "path", encoded = true) path, @Body Map content)
  }
}
