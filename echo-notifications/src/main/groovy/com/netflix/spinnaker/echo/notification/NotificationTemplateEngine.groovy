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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.echo.api.Notification
import freemarker.template.Configuration
import freemarker.template.Template
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils

@Slf4j
@Component
class NotificationTemplateEngine {
    private static final List<Formatter> FORMATTERS = [
      new MarkdownToHtmlFormatter(),
      new HtmlToPlainTextFormatter(),
      new MarkdownPassThruFormatter()
    ]

    @Autowired
    Configuration configuration

    @Value('${spinnaker.base-url}')
    String spinnakerUrl

    String build(Notification notification, Type type) {
        if (!notification.templateGroup) {
          Formatter formatter = FORMATTERS.find { it.type == notification.additionalContext.formatter } ?:
            new MarkdownToHtmlFormatter()

          if (type == Type.SUBJECT) {
            return (formatter
              .convert(notification.additionalContext.customSubject?: notification.additionalContext.subject) as String)
          }

          if (type == Type.BODY) {
            return (formatter
              .convert(notification.additionalContext.customBody?: notification.additionalContext.body) as String)
          }
        }

        Template template = determineTemplate(configuration, notification.templateGroup, type, notification.notificationType)
        FreeMarkerTemplateUtils.processTemplateIntoString(
          template,
          [
            baseUrl         : spinnakerUrl,
            notification    : notification,
            htmlToText      : new HtmlToPlainTextFormatter(),
            markdownToHtml  : new MarkdownToHtmlFormatter()
          ]
        )
    }

    @PackageScope
    @VisibleForTesting
    static Template determineTemplate(Configuration configuration, String templateGroup, Type type, String notificationType) {
        def specificTemplate = specificTemplate(templateGroup, type, notificationType)
        def genericTemplate = genericTemplate(templateGroup, type)

        try {
            return configuration.getTemplate(specificTemplate, "UTF-8")
        } catch (TemplateNotFoundException) {
            return configuration.getTemplate(genericTemplate, "UTF-8")
        }
    }

    @PackageScope
    @VisibleForTesting
    static String specificTemplate(String templateGroup, Type type, String notificationType) {
        return "${templateGroup}/${type.toString().toLowerCase()}-${notificationType.toLowerCase()}.ftl"
    }

    @PackageScope
    @VisibleForTesting
    static String genericTemplate(String templateGroup, Type type) {
        return "${templateGroup}/${type.toString().toLowerCase()}.ftl"
    }

    static enum Type {
        BODY,
        SUBJECT
    }

    interface Formatter {
      String getType()
      String convert(String text)
    }

    static class HtmlToPlainTextFormatter implements Formatter {
        private final HtmlToPlainText htmlToPlainText = new HtmlToPlainText()

      @Override
      String getType() {
        return "TEXT"
      }

      String convert(String content) {
            return htmlToPlainText.getPlainText(Jsoup.parse(content))
        }
    }

    static class MarkdownToHtmlFormatter implements Formatter {
      private final Parser parser = Parser.builder().build()
      private final HtmlRenderer renderer = HtmlRenderer.builder().build()

      @Override
      String getType() {
        return "HTML"
      }

      String convert(String content) {
        Node document = parser.parse(content)
        return renderer.render(document)
      }
    }

  static class MarkdownPassThruFormatter implements Formatter {
    private final Parser parser = Parser.builder().build()

    @Override
    String getType() {
      return "MARKDOWN"
    }

    String convert(String content) {
      // parse just to make sure the syntax is OK
      parser.parse(content)
      return content
    }
  }

}
