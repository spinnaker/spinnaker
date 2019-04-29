/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.jira;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.controller.EchoResponse;
import com.netflix.spinnaker.echo.jira.JiraService.CreateJiraIssueRequest;
import com.netflix.spinnaker.echo.jira.JiraService.CreateJiraIssueResponse;
import com.netflix.spinnaker.echo.notification.NotificationService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import retrofit.RetrofitError;

@Component
@ConditionalOnProperty("jira.enabled")
public class JiraNotificationService implements NotificationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(JiraNotificationService.class);
  private static final int MAX_RETRY = 3;
  private static final long RETRY_BACKOFF = 3;

  private final JiraService jiraService;
  private final RetrySupport retrySupport;
  private final ObjectMapper mapper;

  @Autowired
  public JiraNotificationService(JiraService jiraService, ObjectMapper objectMapper) {
    this.jiraService = jiraService;
    this.retrySupport = new RetrySupport();
    this.mapper = objectMapper;
  }

  @Override
  public boolean supportsType(Notification.Type type) {
    return type == Notification.Type.JIRA;
  }

  @Override
  public EchoResponse<CreateJiraIssueResponse> handle(Notification notification) {
    Map<String, Object> issueRequestBody = issueRequestBody(notification);
    try {
      CreateJiraIssueResponse response =
          retrySupport.retry(createJiraIssue(issueRequestBody), MAX_RETRY, RETRY_BACKOFF, false);

      return new EchoResponse<>(response);
    } catch (Exception e) {
      throw new CreateJiraIssueException(
          String.format(
              "Failed to create Jira Issue %s: %s",
              kv("issueRequestBody", issueRequestBody), errors(e)),
          e);
    }
  }

  private Supplier<CreateJiraIssueResponse> createJiraIssue(Map<String, Object> issueRequestBody) {
    return () -> jiraService.createJiraIssue(new CreateJiraIssueRequest(issueRequestBody));
  }

  private Map<String, Object> issueRequestBody(Notification notification) {
    Map<String, Object> issue =
        (Map<String, Object>) notification.getAdditionalContext().get("issueContext");
    // Move up additional details to main level
    // details contains undefined fields in orca strongly typed request
    // it allows the flexibility of arbitrary fields in the request
    Optional.ofNullable((Map<String, Object>) issue.get("details"))
        .ifPresent(i -> i.forEach(issue::put));
    issue.remove("details");
    return issue;
  }

  private Map errors(Exception exception) {
    if (exception instanceof RetrofitError) {
      try {
        return mapper.readValue(
            ((RetrofitError) exception).getResponse().getBody().in(), Map.class);
      } catch (Exception e) {
        LOGGER.warn("failed retrieving error messages {}", e.getMessage());
      }
    }

    return ImmutableMap.of("errors", exception.getMessage());
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  static class CreateJiraIssueException extends RuntimeException {
    public CreateJiraIssueException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
