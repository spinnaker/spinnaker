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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.controller.EchoResponse;
import com.netflix.spinnaker.echo.jira.JiraService.CommentIssueRequest;
import com.netflix.spinnaker.echo.jira.JiraService.CreateIssueRequest;
import com.netflix.spinnaker.echo.jira.JiraService.CreateIssueResponse;
import com.netflix.spinnaker.echo.jira.JiraService.IssueTransitions;
import com.netflix.spinnaker.echo.jira.JiraService.TransitionIssueRequest;
import com.netflix.spinnaker.echo.notification.NotificationService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
@ConditionalOnProperty("jira.enabled")
public class JiraNotificationService implements NotificationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(JiraNotificationService.class);
  private static final int MAX_RETRY = 3;
  private static final long RETRY_BACKOFF = 100;

  private final JiraService jiraService;
  private final RetrySupport retrySupport;
  private final ObjectMapper mapper;

  @Autowired
  public JiraNotificationService(
      JiraService jiraService, RetrySupport retrySupport, ObjectMapper objectMapper) {
    this.jiraService = jiraService;
    this.retrySupport = retrySupport;
    this.mapper = objectMapper;
  }

  @Override
  public boolean supportsType(String type) {
    return "JIRA".equalsIgnoreCase(type);
  }

  @Override
  public EchoResponse handle(Notification notification) {
    return isTransition(notification) ? transitionIssue(notification) : create(notification);
  }

  private boolean isTransition(Notification notification) {
    return notification.getAdditionalContext().get("transitionContext") != null;
  }

  private EchoResponse.Void transitionIssue(Notification notification) {
    TransitionJiraNotification transitionNotification =
        mapper.convertValue(notification.getAdditionalContext(), TransitionJiraNotification.class);
    String jiraIssue = transitionNotification.getJiraIssue();

    try {
      // transitionContext is the full Jira transition API payload (which is stored in
      // transitionDetails) - except the transition ID is probably unknown.  So, we get the
      // transition ID from the transition name.
      Map<String, String> transition =
          transitionNotification.getTransitionContext().getTransition();
      Map<String, Object> transitionDetails =
          transitionNotification.getTransitionContext().getTransitionDetails();
      String transitionName = transition.get("name");

      IssueTransitions issueTransitions =
          retrySupport.retry(getIssueTransitions(jiraIssue), MAX_RETRY, RETRY_BACKOFF, false);

      issueTransitions.getTransitions().stream()
          .filter(it -> it.getName().equals(transitionName))
          .findFirst()
          .ifPresentOrElse(
              t -> {
                transition.put("id", t.getId());
                transitionDetails.put("transition", transition);
              },
              () -> {
                throw new IllegalArgumentException(
                    ImmutableMap.of(
                            "issue", jiraIssue,
                            "transitionName", transitionName,
                            "validTransitionNames",
                                issueTransitions.getTransitions().stream()
                                    .map(IssueTransitions.Transition::getName)
                                    .collect(Collectors.toList()))
                        .toString());
              });

      retrySupport.retry(
          transitionIssue(jiraIssue, transitionDetails), MAX_RETRY, RETRY_BACKOFF, false);

      if (transitionNotification.getComment() != null) {
        retrySupport.retry(
            addComment(jiraIssue, transitionNotification.getComment()),
            MAX_RETRY,
            RETRY_BACKOFF,
            false);
      }

      return new EchoResponse.Void();
    } catch (Exception e) {
      throw new TransitionJiraIssueException(
          String.format("Failed to transition Jira issue %s: %s", jiraIssue, errors(e)), e);
    }
  }

  private EchoResponse<CreateIssueResponse> create(Notification notification) {
    Map<String, Object> issueRequestBody = issueRequestBody(notification);
    try {
      CreateIssueResponse response =
          retrySupport.retry(createIssue(issueRequestBody), MAX_RETRY, RETRY_BACKOFF, false);

      return new EchoResponse<>(response);
    } catch (Exception e) {
      throw new CreateJiraIssueException(
          String.format(
              "Failed to create Jira Issue %s: %s",
              kv("issueRequestBody", issueRequestBody), errors(e)),
          e);
    }
  }

  private Supplier<IssueTransitions> getIssueTransitions(String issueIdOrKey) {
    return () -> Retrofit2SyncCall.execute(jiraService.getIssueTransitions(issueIdOrKey));
  }

  private Supplier<ResponseBody> transitionIssue(
      String issueIdOrKey, Map<String, Object> transitionDetails) {
    return () ->
        Retrofit2SyncCall.execute(
            jiraService.transitionIssue(
                issueIdOrKey, new TransitionIssueRequest(transitionDetails)));
  }

  private Supplier<ResponseBody> addComment(String issueIdOrKey, String comment) {
    return () ->
        Retrofit2SyncCall.execute(
            jiraService.addComment(issueIdOrKey, new CommentIssueRequest(comment)));
  }

  private Supplier<CreateIssueResponse> createIssue(Map<String, Object> issueRequestBody) {
    return () ->
        Retrofit2SyncCall.execute(
            jiraService.createIssue(new CreateIssueRequest(issueRequestBody)));
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
    if (exception instanceof SpinnakerHttpException) {
      return ((SpinnakerHttpException) exception).getResponseBody();
    }

    return ImmutableMap.of("errors", exception.getMessage());
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  static class CreateJiraIssueException extends RuntimeException {
    public CreateJiraIssueException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  static class TransitionJiraIssueException extends RuntimeException {
    public TransitionJiraIssueException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  static class TransitionJiraNotification {
    private String jiraIssue;
    private String comment;
    private TransitionContext transitionContext;

    public String getJiraIssue() {
      return jiraIssue;
    }

    public void setJiraIssue(String jiraIssue) {
      this.jiraIssue = jiraIssue;
    }

    public String getComment() {
      return comment;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }

    public TransitionContext getTransitionContext() {
      return transitionContext;
    }

    public void setTransitionContext(TransitionContext transitionContext) {
      this.transitionContext = transitionContext;
    }

    static class TransitionContext {
      private Map<String, String> transition;

      // placeholder for all the other remaining transition context payload
      private Map<String, Object> transitionDetails = new HashMap<>();

      public Map<String, String> getTransition() {
        return transition;
      }

      public void setTransition(Map<String, String> transition) {
        this.transition = transition;
      }

      public Map<String, Object> getTransitionDetails() {
        return transitionDetails;
      }

      @JsonAnySetter
      public void setTransitionDetails(String name, Object value) {
        this.transitionDetails.put(name, value);
      }
    }
  }
}
