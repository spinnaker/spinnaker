package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import groovy.util.logging.Slf4j

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Scheduler
import rx.Subscription
import rx.schedulers.Schedulers
import static com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler.TRIGGER_TYPE
import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Indexes pipelines known by mayo for consumption by a
 * {@link BuildJobNotificationHandler}.
 */
@Component
@CompileStatic
@Slf4j
class BuildJobPipelineIndexer implements PipelineIndexer {

  private static final String TRIGGER_KEY = "job"
  private static final String TRIGGER_MASTER = "master"

  final long pollingInterval = 60

  private final MayoService mayoService
  private final ObjectMapper objectMapper

  private Map<Trigger, Collection<Map>> pipelinesByTrigger = [:]
  private Scheduler scheduler = Schedulers.io()
  private Subscription subscription

  @Autowired
  BuildJobPipelineIndexer(MayoService mayoService, ObjectMapper objectMapper) {
    this.mayoService = mayoService
    this.objectMapper = objectMapper
  }

  @PostConstruct
  void init() {
    subscription = rx.Observable.interval(pollingInterval, SECONDS, scheduler).map {
      poll()
    } doOnError { Throwable err ->
      log.error "Error when polling for pipelines", err
    } retry() distinctUntilChanged() subscribe { Map<Trigger, Collection<Map>> pipelines ->
      pipelinesByTrigger = pipelines
    }
  }

  @PreDestroy
  void shutdown() {
    subscription?.unsubscribe()
  }

  @Override
  ImmutableMap<Serializable, Collection<Map>> getPipelines() {
    ImmutableMap.copyOf(pipelinesByTrigger)
  }

  private Map<Trigger, Collection<Map>> poll() {
    filterPipelinesByTrigger(readMayoPipelines())
  }

  private Map<Trigger, Collection<Map>> filterPipelinesByTrigger(List<Map> pipelines) {
    Map<Trigger, Collection<Map>> _interestingPipelines = [:]
    for (pipeline in pipelines) {
      def triggers = pipeline.triggers as List<Map>
      if (!triggers) continue
      for (trigger in triggers) {
        if (trigger.type == TRIGGER_TYPE && trigger.enabled) {
          def key = new Trigger(trigger[TRIGGER_MASTER] as String, trigger[TRIGGER_KEY] as String)
          if (!_interestingPipelines.containsKey(key)) {
            _interestingPipelines[key] = []
          }
          _interestingPipelines[key] << pipeline
        }
      }
    }
    _interestingPipelines
  }

  private List<Map> readMayoPipelines() {
    objectMapper.readValue(
      mayoService.pipelines.body.in().text,
      new TypeReference<List<Map>>() {}
    ) as List<Map>
  }

  @VisibleForTesting
  void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler
  }
}
