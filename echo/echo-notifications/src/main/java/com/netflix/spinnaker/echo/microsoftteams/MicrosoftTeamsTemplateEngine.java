/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.echo.microsoftteams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Template engine for rendering Microsoft Teams messages using Jinja templates.
 *
 * <p>Supports both default templates (bundled with Echo) and custom user-provided templates.
 *
 * <p><strong>Important:</strong> Microsoft is retiring Office 365 Connectors (deadline: March 31,
 * 2026). Users must migrate to Power Automate Workflows webhooks. The default templates use
 * Adaptive Cards which work with both legacy connectors and the new Workflows webhooks.
 *
 * <p>Default templates use Adaptive Card format (recommended). Custom templates can be provided for
 * legacy MessageCard format if needed, but note that MessageCard has limitations in Power Automate
 * Workflows (no interactive elements, no custom bot identity).
 *
 * <p>Template variables available: - notification: The notification object - spinnakerUrl: Base URL
 * of Spinnaker - application: Application name - executionId: Execution ID - executionName:
 * Execution name - executionUrl: Direct link to execution - status: Execution status - message:
 * Custom message (if provided) - Any additional context from notification.additionalContext
 *
 * @see <a
 *     href="https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/">Office
 *     365 Connectors Retirement Announcement</a>
 */
@Slf4j
@Component
public class MicrosoftTeamsTemplateEngine {

  private final Jinjava jinjava;
  private final ObjectMapper objectMapper;
  private final String customTemplatePath;

  public MicrosoftTeamsTemplateEngine(
      ObjectMapper objectMapper,
      @Value("${microsoftteams.template-path:}") String customTemplatePath) {
    this.objectMapper = objectMapper;
    this.customTemplatePath = customTemplatePath;
    this.jinjava = new Jinjava(JinjavaConfig.newBuilder().build());
  }

  /**
   * Renders a Microsoft Teams notification using a Jinja template.
   *
   * @param templateName Name of the template (e.g., "event-notification", "pipeline-notification")
   * @param context Template variables
   * @return Rendered JSON string for the Teams message
   * @throws TemplateRenderException if template rendering fails
   */
  public String render(String templateName, Map<String, Object> context)
      throws TemplateRenderException {
    String template = loadTemplate(templateName);

    try {
      String rendered = jinjava.render(template, context);

      // Validate that the rendered output is valid JSON
      objectMapper.readTree(rendered);

      return rendered;
    } catch (FatalTemplateErrorsException e) {
      log.error("Failed to render MS Teams template '{}': {}", templateName, e.getMessage(), e);
      throw new TemplateRenderException("Template rendering failed: " + e.getMessage(), e);
    } catch (IOException e) {
      log.error(
          "Rendered MS Teams template '{}' produced invalid JSON: {}",
          templateName,
          e.getMessage());
      throw new TemplateRenderException("Template produced invalid JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Loads a template from custom path or default classpath location.
   *
   * @param templateName Name of the template
   * @return Template content
   * @throws TemplateRenderException if template cannot be loaded
   */
  private String loadTemplate(String templateName) throws TemplateRenderException {
    String templateFileName = templateName + ".jinja";

    // Try custom template path first
    if (customTemplatePath != null && !customTemplatePath.isEmpty()) {
      Path customPath = Paths.get(customTemplatePath, templateFileName);
      if (Files.exists(customPath)) {
        try {
          log.debug("Loading custom MS Teams template from: {}", customPath);
          return Files.readString(customPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
          log.warn("Failed to read custom template at {}, falling back to default", customPath, e);
        }
      }
    }

    // Fall back to default classpath template
    String classpathLocation = "templates/microsoftteams/" + templateFileName;
    try {
      ClassPathResource resource = new ClassPathResource(classpathLocation);
      try (InputStream is = resource.getInputStream()) {
        log.debug("Loading default MS Teams template from classpath: {}", classpathLocation);
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      log.error("Failed to load MS Teams template '{}' from classpath", templateName, e);
      throw new TemplateRenderException(
          "Template not found: " + templateName + " (looked in custom path and classpath)", e);
    }
  }

  /** Creates a base context map with common variables. */
  public Map<String, Object> createBaseContext() {
    return new HashMap<>();
  }

  /** Exception thrown when template rendering fails. */
  public static class TemplateRenderException extends Exception {
    public TemplateRenderException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
