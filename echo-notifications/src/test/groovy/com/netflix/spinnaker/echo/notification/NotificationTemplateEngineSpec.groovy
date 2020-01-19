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
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateNotFoundException
import spock.lang.Specification
import spock.lang.Unroll

import static NotificationTemplateEngine.*

class NotificationTemplateEngineSpec extends Specification {
  void "should favor type-specific template when available"() {
    given:
    def engine = Mock(Configuration) {
      1 * getTemplate(specificTemplate(templateGroup, type, notificationType), "UTF-8") >> { name, enc ->
        if (specificTemplateExists) {
          return specificTemplate
        }
        throw new TemplateNotFoundException(name, "bacon", "nay")
      }

      (specificTemplateExists ? 0 : 1) * getTemplate(genericTemplate(templateGroup, type), "UTF-8") >> genericTemplate
    }

    when:
    def determinedTemplate = determineTemplate(engine, templateGroup, type, notificationType)

    then:
    specificTemplateExists ? determinedTemplate == specificTemplate : determinedTemplate == genericTemplate

    where:
    specificTemplate = Stub(Template)
    genericTemplate = Stub(Template)
    templateGroup | type      | notificationType        | specificTemplateExists
    "group"       | Type.BODY | Notification.Type.EMAIL | true
    "group"       | Type.BODY | Notification.Type.EMAIL | false
  }

  @Unroll
  void "Not defining a template should work"() {
    given:
    def notification = new Notification(
      notificationType: notificationType,
      to: ["test@netflix.com"],
      severity: Notification.Severity.NORMAL,
      additionalContext: [
        subject: content,
        body: content
      ]
    )

    when:
    def output = new NotificationTemplateEngine().build(notification, Type.BODY)

    then:
    output == "<p>test</p>\n"

    where:
    templateGroup | type          | notificationType        | content
    null          | Type.BODY     | Notification.Type.EMAIL | "test"
    null          | Type.SUBJECT  | Notification.Type.EMAIL | "test"
  }

  void "Using a MARKDOWN formatter should leave the original markdown untouched"() {
    given:
    def notification = new Notification(
      notificationType: Notification.Type.EMAIL,
      to: ["test@netflix.com"],
      severity: Notification.Severity.NORMAL,
      additionalContext: [
        subject: "test",
        body: "This is *markdown*! :yay:",
        formatter: "MARKDOWN"
      ]
    )

    when:
    def output = new NotificationTemplateEngine().build(notification, Type.BODY)

    then:
    output == "This is *markdown*! :yay:"
  }

}
