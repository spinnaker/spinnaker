package com.netflix.spinnaker.echo.pipelinetriggers;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.BuildEvent;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.services.Front50Service;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
@Slf4j
public class BuildEventMonitor implements MonitoredPoller, EchoEventListener {

  public static final String ECHO_EVENT_TYPE = "build";
  public static final String TRIGGER_TYPE = "jenkins";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final Scheduler scheduler;
  private final int pollingIntervalSeconds;
  private final Front50Service front50;
  private final Action1<Pipeline> subscriber;
  private final ExtendedRegistry registry;

  private transient Instant lastPollTimestamp;
  private transient Subscription subscription;

  private transient AtomicReference<List<Pipeline>> pipelines = new AtomicReference<>(Collections.emptyList());

  @Autowired
  public BuildEventMonitor(@NonNull Scheduler scheduler,
                           int pollingIntervalSeconds,
                           @NonNull Front50Service front50,
                           @NonNull Action1<Pipeline> subscriber,
                           @NonNull @Qualifier("extendedRegistry") ExtendedRegistry registry) {
    this.scheduler = scheduler;
    this.pollingIntervalSeconds = pollingIntervalSeconds;
    this.front50 = front50;
    this.subscriber = subscriber;
    this.registry = registry;
  }

  @PostConstruct
  public void start() {
    if (subscription == null || subscription.isUnsubscribed()) {
      subscription = Observable.interval(pollingIntervalSeconds, SECONDS, scheduler)
        .doOnNext(this::onFront50Request)
        .flatMap(tick -> front50.getPipelines())
        .doOnError(this::onFront50Error)
        .retry()
        .subscribe(this::cachePipelines);
    }
  }

  @PreDestroy
  public void stop() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }

  @Override
  public void processEvent(Event event) {
    if (!event.getDetails().getType().equalsIgnoreCase(ECHO_EVENT_TYPE)) {
      return;
    }

    val buildEvent = objectMapper.convertValue(event, BuildEvent.class);
    Observable.from(Collections.singletonList(buildEvent))
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelines.get()));
  }

  @Override
  public boolean isRunning() {
    return subscription != null && !subscription.isUnsubscribed();
  }

  @Override
  public Instant getLastPollTimestamp() {
    return lastPollTimestamp;
  }

  @Override
  public int getPollingIntervalSeconds() {
    return pollingIntervalSeconds;
  }

  private void cachePipelines(final List<Pipeline> pipelines) {
    log.info("Refreshing pipelines");
    this.pipelines.set(pipelines);
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

  private boolean isEnabledJenkinsTrigger(final Pipeline.Trigger trigger) {
    return trigger.isEnabled() &&
      TRIGGER_TYPE.equals(trigger.getType()) &&
      trigger.getJob() != null &&
      trigger.getMaster() != null;
  }

  private Predicate<Pipeline.Trigger> matchTriggerFor(final BuildEvent event) {
    String jobName = event.getContent().getProject().getName();
    String master = event.getContent().getMaster();
    return trigger -> trigger.getJob().equals(jobName) && trigger.getMaster().equals(master);
  }

  private void onEventProcessed(final BuildEvent event) {
    registry.counter("echo.events.processed").increment();
  }

  private static Instant laterOf(final Instant a, final Instant b) {
    return a.isAfter(b) ? a : b;
  }

  private void onMatchingPipeline(Pipeline pipeline) {
    log.info("Found matching pipeline {}:{}", pipeline.getApplication(), pipeline.getName());
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName())
      .withTag("job", pipeline.getTrigger().getJob());
    registry.counter(id).increment();
  }

  private void onFront50Request(final long tick) {
    log.debug("Getting pipelines from Front50...");
    lastPollTimestamp = now();
    registry.counter("front50.requests").increment();
  }

  private void onFront50Error(Throwable e) {
    log.error("Error fetching pipelines from Front50: {}", e.getMessage());
    registry.counter("front50.errors").increment();
  }

  private void onSubscriberError(Throwable error) {
    log.error("Subscriber raised an error processing pipeline", error);
    registry.counter("trigger.errors").increment();
  }
}
