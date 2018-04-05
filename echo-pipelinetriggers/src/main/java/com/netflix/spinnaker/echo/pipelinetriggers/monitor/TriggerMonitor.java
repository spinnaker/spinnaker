/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
@Slf4j
public abstract class TriggerMonitor implements EchoEventListener {

  protected final Action1<Pipeline> subscriber;
  protected final Registry registry;

  protected void validateEvent(Event event) {
    if (event.getDetails() == null) {
      throw new IllegalArgumentException("Event details required by the event monitor.");
    } else if (event.getDetails().getType() == null) {
      throw new IllegalArgumentException("Event details type required by the event monitor.");
    }
  }

  public TriggerMonitor(@NonNull Action1<Pipeline> subscriber,
                        @NonNull Registry registry) {
    this.subscriber = subscriber;
    this.registry = registry;
  }

  protected boolean matchesPattern(String s, String pattern) {
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(s);
    return m.matches();
  }

  protected void onEchoResponse(final TriggerEvent event) {
    registry.gauge("echo.events.per.poll", 1);
  }

  protected Action1<TriggerEvent> triggerEachMatchFrom(final List<Pipeline> pipelines) {
    return event -> {
      if (isSuccessfulTriggerEvent(event)) {
        Observable.from(pipelines)
          .doOnCompleted(() -> onEventProcessed(event))
          .map(withMatchingTrigger(event))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .doOnNext(this::onMatchingPipeline)
          .subscribe(subscriber, this::onSubscriberError);
      } else {
        onEventProcessed(event);
      }
    };
  }

  protected Func1<Pipeline, Optional<Pipeline>> withMatchingTrigger(final TriggerEvent event) {
    return pipeline -> {
      if (pipeline.getTriggers() == null || pipeline.isDisabled()) {
        return Optional.empty();
      } else {
        return pipeline.getTriggers()
          .stream()
          .filter(this::isValidTrigger)
          .filter(matchTriggerFor(event, pipeline))
          .findFirst()
          .map(buildTrigger(pipeline, event));
      }
    };
  }

  protected abstract boolean isSuccessfulTriggerEvent(TriggerEvent event);

  protected abstract Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline);

  protected abstract Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event);

  protected abstract boolean isValidTrigger(final Trigger trigger);

  protected abstract void emitMetricsOnMatchingPipeline(Pipeline pipeline);

  protected void onMatchingPipeline(Pipeline pipeline) {
    log.info("Found matching pipeline {}:{}", pipeline.getApplication(), pipeline.getName());
    emitMetricsOnMatchingPipeline(pipeline);
  }

  protected void onEventProcessed(final TriggerEvent event) {
    registry.counter("echo.events.processed").increment();
  }

  private void onSubscriberError(Throwable error) {
    log.error("Subscriber raised an error processing pipeline", error);
    registry.counter("trigger.errors").increment();
  }
}

