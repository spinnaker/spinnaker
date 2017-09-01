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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import lombok.NonNull;
import lombok.val;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Triggers pipelines in _Orca_ when a trigger-enabled pubsub message arrives.
 */
@Component
public class PubsubEventMonitor extends TriggerMonitor {

  public static final String PUBSUB_TRIGGER_TYPE = "pubsub";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public PubsubEventMonitor(@NonNull PipelineCache pipelineCache,
                            @NonNull Action1<Pipeline> subscriber,
                            @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (!event.getDetails().getType().equalsIgnoreCase(PubsubEvent.TYPE)) {
      return;
    }

    PubsubEvent pubsubEvent = objectMapper.convertValue(event, PubsubEvent.class);

    Observable.just(pubsubEvent)
        .doOnNext(this::onEchoResponse)
        .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    return pubsubEvent != null;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();
    return trigger -> pipeline
        .withArtifacts(description.getArtifacts())
        .withTrigger(trigger.atMessageDescription(description.getSubscriptionName(), description.getPubsubType().toString()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled()
        && isPubsubTrigger(trigger);
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();

    // TODO(jacobkiefer): Need to apply filters specified in triggers here.
    return trigger -> trigger.getType().equalsIgnoreCase(PUBSUB_TRIGGER_TYPE)
        && trigger.getPubsubType().equalsIgnoreCase(description.getPubsubType().toString())
        && trigger.getSubscriptionName().equalsIgnoreCase(description.getSubscriptionName());
  }

  @Override
  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
        .withTag("application", pipeline.getApplication())
        .withTag("name", pipeline.getName());

    if (isPubsubTrigger(pipeline.getTrigger())) {
      id.withTag("pubsubType", pipeline.getTrigger().getPubsubType());
      id.withTag("subscriptionName", pipeline.getTrigger().getSubscriptionName());
    }

    registry.counter(id).increment();
  }

  private boolean isPubsubTrigger(Trigger trigger) {
    return PUBSUB_TRIGGER_TYPE.equals(trigger.getType())
        && !StringUtils.isEmpty(trigger.getSubscriptionName())
        && !StringUtils.isEmpty(trigger.getPubsubType());
  }
}
