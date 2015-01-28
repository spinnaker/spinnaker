package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Scheduler
import rx.Subscription
import rx.schedulers.Schedulers
import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Indexes pipelines known by mayo for consumption by a
 * {@link BuildJobNotificationHandler}.
 */
@Component
@CompileStatic
@Log4j
class BuildJobPipelineIndexer implements PipelineIndexer {

  private static final String TRIGGER_KEY = "job"
  private static final String TRIGGER_MASTER = "master"

  final long pollingInterval = 60
  private final PipelineConfigurationService pipelineConfigurationService
  private Map<Trigger, Collection<Map>> interestingPipelines = [:]
  private Scheduler scheduler = Schedulers.io()
  private Subscription subscription

  @Autowired
  BuildJobPipelineIndexer(PipelineConfigurationService pipelineConfigurationService) {
    this.pipelineConfigurationService = pipelineConfigurationService
  }

  @PostConstruct
  @CompileDynamic
  void init() {
    subscription = rx.Observable.interval(pollingInterval, SECONDS, scheduler).map {
      try {
        poll()
      } catch (e) {
        log.error "Caught exception polling for pipelines", e
      }
    } distinctUntilChanged()
    .subscribe { Map<Trigger, Collection<Map>> pipelines ->
      interestingPipelines = pipelines
    }
  }

  @PreDestroy
  void shutdown() {
    subscription.unsubscribe()
  }

  @Override
  ImmutableMap<Serializable, Collection<Map>> getPipelines() {
    ImmutableMap.copyOf(interestingPipelines)
  }

  private Map<Trigger, Collection<Map>> poll() {
    Map<Trigger, Collection<Map>> _interestingPipelines = [:]
    for (pipeline in pipelineConfigurationService.pipelines) {
      def triggers = pipeline.triggers as List<Map>
      for (trigger in triggers) {
        if (trigger.type == BuildJobNotificationHandler.TRIGGER_TYPE) {
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

  @VisibleForTesting
  void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler
  }
}
