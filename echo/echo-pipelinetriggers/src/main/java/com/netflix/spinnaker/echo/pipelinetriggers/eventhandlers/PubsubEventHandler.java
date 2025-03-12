/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of TriggerEventHandler for events of type {@link PubsubEvent}, which occur when a
 * pubsub message is received.
 */
public class PubsubEventHandler extends BaseTriggerEventHandler<PubsubEvent> {
  public static final String PUBSUB_TRIGGER_TYPE = "pubsub";
  private static final List<String> supportedTriggerTypes =
      Collections.singletonList(PUBSUB_TRIGGER_TYPE);

  @Autowired
  public PubsubEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(PubsubEvent.TYPE);
  }

  @Override
  public Class<PubsubEvent> getEventType() {
    return PubsubEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(PubsubEvent pubsubEvent) {
    return pubsubEvent != null;
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(PubsubEvent pubsubEvent) {
    Map payload = pubsubEvent.getPayload();
    Map parameters =
        payload.containsKey("parameters") ? (Map) payload.get("parameters") : new HashMap();
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();
    return inputTrigger -> {
      Trigger trigger =
          inputTrigger
              .atMessageDescription(
                  description.getSubscriptionName(), description.getPubsubSystem().toString())
              .atParameters(parameters)
              .atPayload(payload)
              .atEventId(pubsubEvent.getEventId());

      Object linkTextObject = payload.get("linkText");
      Object linkObject = payload.get("link");
      if (linkTextObject instanceof String && linkObject instanceof String) {
        String linkText = (String) linkTextObject;
        String link = (String) linkObject;
        if (StringUtils.isNotBlank(linkText) && StringUtils.isNotBlank(link)) {
          trigger = trigger.withLink(link).withLinkText(linkText);
        }
      }
      return trigger;
    };
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() && isPubsubTrigger(trigger);
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(PubsubEvent pubsubEvent) {
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();

    return trigger ->
        supportedTriggerTypes().contains(trigger.getType())
            && trigger.getPubsubSystem().equalsIgnoreCase(description.getPubsubSystem().toString())
            && trigger.getSubscriptionName().equalsIgnoreCase(description.getSubscriptionName())
            && (trigger.getPayloadConstraints() == null
                || isConstraintInPayload(trigger.getPayloadConstraints(), pubsubEvent.getPayload()))
            && (trigger.getAttributeConstraints() == null
                || isConstraintInPayload(
                    trigger.getAttributeConstraints(), description.getMessageAttributes()));
  }

  @Override
  public Map<String, String> getAdditionalTags(Pipeline pipeline) {
    Map<String, String> tags = new HashMap<>();
    tags.put("pubsubSystem", pipeline.getTrigger().getPubsubSystem());
    tags.put("subscriptionName", pipeline.getTrigger().getSubscriptionName());
    return tags;
  }

  private boolean isPubsubTrigger(Trigger trigger) {
    return PUBSUB_TRIGGER_TYPE.equals(trigger.getType())
        && !StringUtils.isEmpty(trigger.getSubscriptionName())
        && !StringUtils.isEmpty(trigger.getPubsubSystem());
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(PubsubEvent pubsubEvent, Trigger trigger) {
    return pubsubEvent.getContent().getMessageDescription().getArtifacts();
  }
}
