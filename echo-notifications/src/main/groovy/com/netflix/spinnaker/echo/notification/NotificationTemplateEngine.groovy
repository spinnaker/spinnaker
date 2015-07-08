package com.netflix.spinnaker.echo.notification

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.echo.api.Notification
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.apache.velocity.app.VelocityEngine
import org.jsoup.Jsoup
import org.jsoup.examples.HtmlToPlainText
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.ui.velocity.VelocityEngineUtils

@Slf4j
@Component
class NotificationTemplateEngine {
  @Autowired
  VelocityEngine engine

  @Value('${spinnaker.baseUrl}')
  String spinnakerUrl

  String build(Notification notification, Type type) {
    VelocityEngineUtils.mergeTemplateIntoString(
        engine,
        determinateTemplate(engine, notification.templateGroup, type, notification.notificationType),
        "UTF-8",
        [
            baseUrl     : spinnakerUrl,
            notification: notification,
            htmlToText  : new HtmlToPlainTextFormatter()
        ]
    )
  }

  @PackageScope
  @VisibleForTesting
  static String determinateTemplate(VelocityEngine engine, String templateGroup, Type type, Notification.Type notificationType) {
    def specificTemplate = "${templateGroup}/${type.toString().toLowerCase()}-${notificationType.toString().toLowerCase()}.vm"
    def genericTemplate = "${templateGroup}/${type.toString().toLowerCase()}.vm"

    if (engine.resourceExists(specificTemplate)) {
      return specificTemplate
    }
    return genericTemplate
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
}
