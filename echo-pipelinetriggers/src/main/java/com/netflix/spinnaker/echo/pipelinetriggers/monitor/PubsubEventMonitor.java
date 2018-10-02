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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.anyArtifactsMatchExpected;
import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import rx.functions.Action1;

/**
 * Triggers pipelines in _Orca_ when a trigger-enabled pubsub message arrives.
 */
@Slf4j
public class PubsubEventMonitor extends TriggerMonitor {

  public static final String PUBSUB_TRIGGER_TYPE = "pubsub";

  public PubsubEventMonitor(@NonNull PipelineCache pipelineCache,
                            @NonNull Action1<Pipeline> subscriber,
                            @NonNull Registry registry) {
    super(pipelineCache, subscriber, registry);
  }

  @Override
  protected boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(PubsubEvent.TYPE);
  }


  @Override
  protected PubsubEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, PubsubEvent.class);
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    return pubsubEvent != null;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    Map payload = pubsubEvent.getPayload();
    Map parameters = payload.containsKey("parameters") ? (Map) payload.get("parameters") : new HashMap();
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();
    return trigger -> pipeline
        .withReceivedArtifacts(description.getArtifacts())
        .withTrigger(trigger
          .atMessageDescription(description.getSubscriptionName(), description.getPubsubSystem().toString())
          .atParameters(parameters)
          .atPayload(payload)
          .atEventId(event.getEventId()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled()
        && isPubsubTrigger(trigger);
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();

    return trigger -> trigger.getType().equalsIgnoreCase(PUBSUB_TRIGGER_TYPE)
        && trigger.getPubsubSystem().equalsIgnoreCase(description.getPubsubSystem().toString())
        && trigger.getSubscriptionName().equalsIgnoreCase(description.getSubscriptionName())
        && (trigger.getPayloadConstraints() == null || isConstraintInPayload(trigger.getPayloadConstraints(), event.getPayload()))
        && (trigger.getAttributeConstraints() == null || isConstraintInPayload(trigger.getAttributeConstraints(), description.getMessageAttributes()))
        && anyArtifactsMatchExpected(description.getArtifacts(), trigger, pipeline);
  }

  @Override
  protected Map<String, String> getAdditionalTags(Pipeline pipeline) {
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
}
