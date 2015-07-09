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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import org.apache.velocity.app.VelocityEngine
import spock.lang.Specification

import static NotificationTemplateEngine.*

class NotificationTemplateEngineSpec extends Specification {
  void "should favor type-specific template when available"() {
    given:
    def engine = Mock(VelocityEngine) {
      1 * resourceExists(_) >> { specificTemplateExists }
    }

    when:
    def determinedTemplate = determinateTemplate(engine, templateGroup, type, notificationType)

    then:
    determinedTemplate == expectedTemplate

    where:
    templateGroup | type      | notificationType        | specificTemplateExists || expectedTemplate
    "group"       | Type.BODY | Notification.Type.EMAIL | true                   || "group/body-email.vm"
    "group"       | Type.BODY | Notification.Type.EMAIL | false                  || "group/body.vm"
  }
}
