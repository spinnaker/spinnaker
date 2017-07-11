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

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.services.Front50Service
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import static net.logstash.logback.argument.StructuredArguments.kv

@Slf4j
@Component
@ConditionalOnProperty('pagerDuty.enabled')
class PagerDutyNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.PAGER_DUTY

  @Autowired
  PagerDutyService pagerDuty

  @Value('${pagerDuty.token}')
  String token

  @Autowired
  Front50Service front50Service

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  void handle(Notification notification) {
    notification.to.each {
      pagerDuty.createEvent(
        "Token token=${token}",
        new PagerDutyService.PagerDutyCreateEvent(
          service_key: it,
          client: "Spinnaker (${notification.source.user})",
          description: notification.additionalContext.message
        )
      )

      log.info('Sent page {} {}', kv('serviceKey', it), kv('message', notification.additionalContext.message))
    }
  }
}
