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
import org.jsoup.examples.HtmlToPlainText
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils

@Slf4j
@Component
class NotificationTemplateEngine {
    @Autowired
    Configuration configuration

    @Value('${spinnaker.baseUrl}')
    String spinnakerUrl

    String build(Notification notification, Type type) {
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
    static Template determineTemplate(Configuration configuration, String templateGroup, Type type, Notification.Type notificationType) {
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
    static String specificTemplate(String templateGroup, Type type, Notification.Type notificationType) {
        return "${templateGroup}/${type.toString().toLowerCase()}-${notificationType.toString().toLowerCase()}.ftl"
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

    static class HtmlToPlainTextFormatter {
        private final HtmlToPlainText htmlToPlainText = new HtmlToPlainText()

        String convert(String content) {
            return htmlToPlainText.getPlainText(Jsoup.parse(content))
        }
    }

    static class MarkdownToHtmlFormatter {
      private final Parser parser = Parser.builder().build()
      private final HtmlRenderer renderer = HtmlRenderer.builder().build()

      String convert(String content) {
        Node document = parser.parse(content)
        return renderer.render(document)
      }
    }
}
