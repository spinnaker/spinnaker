/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.echo.pagerduty

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.controller.EchoResponse
import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.services.Front50Service
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import retrofit.RetrofitError
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedInput

import static net.logstash.logback.argument.StructuredArguments.kv

@Slf4j
@Component
@ConditionalOnProperty('pager-duty.enabled')
class PagerDutyNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.PAGER_DUTY

  @Autowired
  ObjectMapper mapper

  @Autowired
  PagerDutyService pagerDuty

  @Value('${pager-duty.token:}')
  String token

  @Autowired
  Front50Service front50Service

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  EchoResponse.Void handle(Notification notification) {
    def errors = [:]
    def pdErrors = [:]

    notification.to.each { serviceKey ->
      try {
        Map response = pagerDuty.createEvent(
          "Token token=${token}",
          new PagerDutyService.PagerDutyCreateEvent(
            service_key: serviceKey,
            client: "Spinnaker (${notification.source.user})",
            description: notification.additionalContext.message,
            details: notification.additionalContext.details as Map
          )
        )

        if ("success".equals(response.status)) {
          // Page successful
          log.info('Sent page {} {}',
            kv('serviceKey', serviceKey), kv('message', notification.additionalContext.message))
        } else {
          pdErrors.put(serviceKey, response.message)
        }
      } catch (RetrofitError error) {
        String errorMessage = error.response.reason
        TypedInput responseBody = error.response.getBody()
        if (responseBody != null) {
          PagerDutyErrorResponseBody errorResponse = mapper.readValue(
            new String(((TypedByteArray)responseBody).getBytes()),
            PagerDutyErrorResponseBody
          )
          if (errorResponse.errors && errorResponse.errors.size() > 0) {
            errorMessage = errorResponse.errors.join(", ")
          }
        }
        log.error('Failed to send page {} {} {}',
          kv('serviceKey', serviceKey), kv('message',
          notification.additionalContext.message),
          kv('error', errorMessage)
        )
        errors.put(serviceKey, errorMessage)
      }
    }

    // If some errors occurred
    if (errors || pdErrors) {
      String message = ""
      String pagerDutyError = pdErrors.collect { "${it.key}: ${it.value}" }.join(", ")
      if (pagerDutyError) {
        message += " ${pagerDutyError}."
      }
      String requestError = errors.collect { "${it.key}: ${it.value}" }.join(", ")
      if (requestError) {
        message += " ${requestError}."
      }
      // If some successes occurred
      if (errors.size() + pdErrors.size() < notification.to.size()) {
        throw new PagerDutyException("Some notifications succeeded, but the following failed: ${message}")
      } else {
        if (pdErrors.size() > 0) {
          throw new PagerDutyException("There were problems with PagerDuty: ${message}")
        }
        throw new PagerDutyException("There were issues with the request sent to PagerDuty: ${message}")
      }
    }

    new EchoResponse.Void()
  }
}

class PagerDutyErrorResponseBody {
  String status
  String message
  List<String> errors
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
@InheritConstructors
class PagerDutyException extends RuntimeException {}
