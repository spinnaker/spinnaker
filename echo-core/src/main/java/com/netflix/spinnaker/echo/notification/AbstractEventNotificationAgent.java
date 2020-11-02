/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.echo.notification;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class AbstractEventNotificationAgent implements EventListener {

  private static final String ORCHESTRATION = "orchestration";
  private static final String PIPELINE = "pipeline";
  private static final String TASK = "task";
  private static final String STAGE = "stage";

  private static final Map<String, Map<String, String>> CONFIG =
      ImmutableMap.<String, Map<String, String>>builder()
          .put(ORCHESTRATION, ImmutableMap.of("type", ORCHESTRATION, "link", "tasks"))
          .put(PIPELINE, ImmutableMap.of("type", PIPELINE, "link", "executions/details"))
          .put(TASK, ImmutableMap.of("type", TASK, "link", "tasks"))
          .put(STAGE, ImmutableMap.of("type", STAGE, "link", "stage"))
          .build();

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected ObjectMapper mapper = EchoObjectMapper.getInstance();

  @Value("${spinnaker.base-url}")
  protected String spinnakerUrl;

  public abstract String getNotificationType();

  public abstract void sendNotifications(
      Map<String, Object> notification,
      String application,
      Event event,
      Map<String, String> config,
      String status);

  protected String getSpinnakerUrl() {
    return spinnakerUrl;
  }

  @SneakyThrows
  @Override
  public void processEvent(Event event) {
    if (log.isDebugEnabled() && mapper != null && !event.getDetails().getType().equals("pubsub")) {
      log.debug(
          "Event received: {}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(event));
    }

    if (!event.getDetails().getType().startsWith("orca:")) {
      return;
    }

    List<String> eventDetails = Arrays.asList(event.getDetails().getType().split(":"));

    Map<String, String> config = CONFIG.get(eventDetails.get(1));
    String status = eventDetails.get(2);

    if (config == null || Strings.isNullOrEmpty(config.get("type"))) {
      return;
    }

    String configType = config.get("type");

    if (TASK.equals(configType) && !contentKeyAsBoolean(event, "standalone")) {
      return;
    }

    if (TASK.equals(configType) && contentKeyAsBoolean(event, "canceled")) {
      return;
    }

    if (STAGE.equals(configType) && contentKeyAsBoolean(event, "canceled")) {
      return;
    }

    // TODO(lpollo): why do we have a 'CANCELED' status and a canceled property, which are prime for
    // inconsistency?
    if (isExecution(configType) && isExecutionCanceled(event)) {
      return;
    }

    // Send application level notification
    String application = event.getDetails().getApplication();

    List<Map<String, Object>> notificationsToSend = new ArrayList<>();

    // Pipeline level
    if (isExecution(configType)) {
      notificationsToSend.addAll(buildPipelineNotifications(event, configType, status));
    }

    if (STAGE.equals(configType)) {
      notificationsToSend.addAll(buildStageNotifications(event, configType, status));
    }

    notificationsToSend.forEach(
        notification -> {
          try {
            sendNotifications(notification, application, event, config, status);
          } catch (Exception e) {
            log.error("failed to send {} message", getNotificationType());
          }
        });
  }

  private List<Map<String, Object>> buildPipelineNotifications(
      Event event, String configType, String status) {
    Execution execution = convertExecution(event);
    if (execution == null) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> notifications = execution.notifications;
    if (notifications == null) {
      return Collections.emptyList();
    }

    return execution.notifications.stream()
        .filter(it -> shouldSendRequestForNotification(it, configType, status))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buildStageNotifications(
      Event event, String configType, String status) {
    Map<String, Object> context = (Map<String, Object>) event.getContent().get("context");
    if (context == null) {
      return Collections.emptyList();
    }

    boolean sendNotifications =
        Boolean.parseBoolean(
            Optional.ofNullable(context.get("sendNotifications")).orElse("false").toString());
    if (!sendNotifications || isStageSynthetic(event)) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> notifications =
        (List<Map<String, Object>>) context.getOrDefault("notifications", Collections.emptyList());

    return notifications.stream()
        .filter(it -> shouldSendRequestForNotification(it, configType, status))
        .collect(Collectors.toList());
  }

  private boolean shouldSendRequestForNotification(
      Map<String, Object> notification, String configType, String status) {
    String key = getNotificationType();
    if (key.equals(notification.get("type"))) {
      // TODO(rz): I think this is always a list, but the original Groovy code was testing this
      //  assuming it is a string... but I've never seen it that way. But, since we don't have
      //  a well-typed model, I'm just going to assume that we "support" both unknowingly.
      Object when = notification.get("when");
      if (when != null) {
        String requiredWhen = format("%s.%s", configType, status);
        if (when instanceof String) {
          return ((String) when).contains(requiredWhen);
        } else if (when instanceof Collection) {
          return ((Collection<String>) when).contains(requiredWhen);
        }
      }
    }
    return false;
  }

  private boolean contentKeyAsBoolean(Event event, String key) {
    Object value = event.getContent().get(key);
    if (value == null) {
      return false;
    }
    return Boolean.parseBoolean(value.toString());
  }

  private static boolean isExecution(String type) {
    return "pipeline".equals(type) || "orchestration".equals(type);
  }

  private boolean isExecutionCanceled(Event event) {
    Execution execution = convertExecution(event);
    if (execution == null) {
      return false;
    }

    return "CANCELED".equals(execution.status) || execution.canceled;
  }

  private Execution convertExecution(Event event) {
    Object rawExecution = event.getContent().get("execution");
    if (rawExecution == null) {
      return null;
    }
    return mapper.convertValue(rawExecution, Execution.class);
  }

  @SuppressWarnings("unchecked")
  private boolean isStageSynthetic(Event event) {
    return Boolean.parseBoolean(
        Optional.ofNullable(event.getContent().get("isSynthetic"))
            .orElseGet(
                () -> {
                  Map<String, Object> context =
                      (Map<String, Object>) event.getContent().get("context");
                  if (context == null) {
                    return false;
                  }
                  Map<String, Object> details = (Map<String, Object>) context.get("stageDetails");
                  if (details != null) {
                    return details.get("isSynthetic").toString();
                  }
                  return false;
                })
            .toString());
  }

  private static class Execution {
    public String status;
    public boolean canceled;
    public List<Map<String, Object>> notifications;
  }
}
