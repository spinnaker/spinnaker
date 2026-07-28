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

import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class MicrosoftTeamsServiceTest {

  private MicrosoftTeamsService service;
  private OkHttp3ClientConfiguration mockConfig;
  private ListAppender<ILoggingEvent> logWatcher;

  @BeforeEach
  void setUp() {
    mockConfig = mock(OkHttp3ClientConfiguration.class);
    service = new MicrosoftTeamsService(mockConfig);

    // Set up log appender to capture warnings
    Logger logger = (Logger) LoggerFactory.getLogger(MicrosoftTeamsService.class);
    logWatcher = new ListAppender<>();
    logWatcher.start();
    logger.addAppender(logWatcher);
  }

  @AfterEach
  void tearDown() {
    Logger logger = (Logger) LoggerFactory.getLogger(MicrosoftTeamsService.class);
    logger.detachAppender(logWatcher);
  }

  @Test
  void testLegacyWebhookUrlWarning_OutlookOffice() {
    String legacyUrl = "https://outlook.office.com/webhook/abc123/IncomingWebhook/def456";

    try {
      // This will fail due to mock, but we only care about the warning
      service.sendMessage(legacyUrl, "{}");
    } catch (Exception e) {
      // Expected - we're just testing the warning
    }

    // Verify warning was logged
    boolean foundWarning =
        logWatcher.list.stream()
            .anyMatch(
                event ->
                    event.getLevel().equals(Level.WARN)
                        && event.getMessage().contains("DEPRECATED")
                        && event.getMessage().contains("Office 365 Connectors"));

    assert foundWarning : "Expected deprecation warning for legacy webhook URL";
  }

  @Test
  void testLegacyWebhookUrlWarning_Office365() {
    String legacyUrl = "https://something.office365.com/webhooks/abc123";

    try {
      service.sendMessage(legacyUrl, "{}");
    } catch (Exception e) {
      // Expected - we're just testing the warning
    }

    // Verify warning was logged
    boolean foundWarning =
        logWatcher.list.stream()
            .anyMatch(
                event ->
                    event.getLevel().equals(Level.WARN)
                        && event.getMessage().contains("DEPRECATED")
                        && event.getMessage().contains("Office 365 Connectors"));

    assert foundWarning : "Expected deprecation warning for legacy webhook URL";
  }

  @Test
  void testModernWebhookUrlNoWarning() {
    String modernUrl =
        "https://prod-01.eastus.logic.azure.com:443/workflows/abc123/triggers/manual/paths/invoke";

    try {
      service.sendMessage(modernUrl, "{}");
    } catch (Exception e) {
      // Expected - we're just testing the warning
    }

    // Verify NO warning was logged
    boolean foundWarning =
        logWatcher.list.stream()
            .anyMatch(
                event ->
                    event.getLevel().equals(Level.WARN)
                        && event.getMessage().contains("DEPRECATED"));

    assert !foundWarning : "Should not warn for modern Power Automate webhook URL";
  }
}
