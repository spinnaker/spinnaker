package com.netflix.spinnaker.echo.pipelinetriggers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.BuildEvent;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
@Slf4j
public class BuildEventMonitor implements EchoEventListener {

  public static final String ECHO_EVENT_TYPE = "build";
  public static final String TRIGGER_TYPE = "jenkins";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;
  private final Action1<Pipeline> subscriber;
  private final Registry registry;

  @Autowired
  public BuildEventMonitor(@NonNull PipelineCache pipelineCache,
                           @NonNull Action1<Pipeline> subscriber,
                           @NonNull Registry registry) {
    this.pipelineCache = pipelineCache;
    this.subscriber = subscriber;
    this.registry = registry;
  }

  @Override
  public void processEvent(Event event) {
    if (!event.getDetails().getType().equalsIgnoreCase(ECHO_EVENT_TYPE)) {
      return;
    }

    BuildEvent buildEvent = objectMapper.convertValue(event, BuildEvent.class);
    Observable.just(buildEvent)
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
  }

  private void onEchoResponse(final BuildEvent event) {
    registry.gauge("echo.events.per.poll", 1);
  }

  private Action1<BuildEvent> triggerEachMatchFrom(final List<Pipeline> pipelines) {
    return event -> {
      if (isSuccessfulBuild(event)) {
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

  private boolean isSuccessfulBuild(final BuildEvent event) {
    BuildEvent.Build lastBuild = event.getContent().getProject().getLastBuild();
    return lastBuild != null && !lastBuild.isBuilding() && lastBuild.getResult() == BuildEvent.Result.SUCCESS;
  }

  private Func1<Pipeline, Optional<Pipeline>> withMatchingTrigger(final BuildEvent event) {
    val triggerPredicate = matchTriggerFor(event);
    int buildNumber = event.getContent().getProject().getLastBuild().getNumber();
    return pipeline -> {
      if (pipeline.getTriggers() == null) {
        return Optional.empty();
      } else {
        return pipeline.getTriggers()
          .stream()
          .filter(this::isEnabledJenkinsTrigger)
          .filter(triggerPredicate)
          .findFirst()
          .map(trigger -> pipeline.withTrigger(trigger.atBuildNumber(buildNumber)));
      }
    };
  }

  private boolean isEnabledJenkinsTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      TRIGGER_TYPE.equals(trigger.getType()) &&
      trigger.getJob() != null &&
      trigger.getMaster() != null;
  }

  private Predicate<Trigger> matchTriggerFor(final BuildEvent event) {
    String jobName = event.getContent().getProject().getName();
    String master = event.getContent().getMaster();
    return trigger -> trigger.getJob().equals(jobName) && trigger.getMaster().equals(master);
  }

  private void onEventProcessed(final BuildEvent event) {
    registry.counter("echo.events.processed").increment();
  }

  private void onMatchingPipeline(Pipeline pipeline) {
    log.info("Found matching pipeline {}:{}", pipeline.getApplication(), pipeline.getName());
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName())
      .withTag("job", pipeline.getTrigger().getJob());
    registry.counter(id).increment();
  }

  private void onSubscriberError(Throwable error) {
    log.error("Subscriber raised an error processing pipeline", error);
    registry.counter("trigger.errors").increment();
  }
}
