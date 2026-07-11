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

package com.netflix.spinnaker.echo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Microsoft Teams notifications.
 *
 * <p><strong>Important:</strong> Microsoft is retiring Office 365 Connectors (deadline: March 31,
 * 2026). If you're using legacy Incoming Webhooks, you must migrate to Power Automate Workflows
 * webhooks. Update your webhook URLs in Spinnaker configuration - no code changes needed.
 *
 * <p>New webhook URL format (Power Automate): {@code
 * https://prod-XX.eastus.logic.azure.com:443/workflows/.../triggers/manual/paths/invoke?...}
 *
 * <p>Example configuration in echo.yml or application.yml:
 *
 * <pre>
 * microsoftteams:
 *   enabled: true
 *   template-path: /path/to/custom/templates  # Optional
 * </pre>
 *
 * <p>Custom templates should be Jinja2 templates with .jinja extension. Available templates: -
 * event-notification.jinja - Used for event-based notifications - pipeline-notification.jinja -
 * Used for pipeline and stage notifications
 *
 * <p>Default templates use Adaptive Card format (recommended for Power Automate Workflows). If
 * template-path is not specified or templates are not found, the default bundled templates will be
 * used.
 *
 * @see <a
 *     href="https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/">Office
 *     365 Connectors Retirement</a>
 * @see <a
 *     href="https://support.microsoft.com/en-us/office/send-messages-in-teams-using-incoming-webhooks-323660ec-12ca-40b1-a1d3-a3df47e808c4">Microsoft
 *     Support: Incoming Webhooks</a>
 */
@Data
@Component
@ConfigurationProperties(prefix = "microsoftteams")
public class MicrosoftTeamsProperties {

  /** Whether Microsoft Teams notifications are enabled. */
  private boolean enabled = false;

  /**
   * Optional path to directory containing custom Jinja templates.
   *
   * <p>Templates should be named: - event-notification.jinja - pipeline-notification.jinja
   *
   * <p>If not specified or templates are not found, default templates will be used.
   */
  private String templatePath;
}
