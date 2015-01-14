package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Indexes pipelines known by mayo for consumption by a
 * {@link BuildJobNotificationHandler}.
 */
@Component
@CompileStatic
class BuildJobPipelineIndexer implements PipelineIndexer, Runnable {

  private static final String TRIGGER_KEY = "job"
  private static final String TRIGGER_MASTER = "master"

  final long pollingInterval = 60
  private final PipelineConfigurationService pipelineConfigurationService
  private Map<Trigger, Collection<Map>> interestingPipelines = [:]

  @Autowired
  BuildJobPipelineIndexer(PipelineConfigurationService pipelineConfigurationService) {
    this.pipelineConfigurationService = pipelineConfigurationService
  }

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor().schedule(this, pollingInterval, TimeUnit.SECONDS)
  }

  @Override
  ImmutableMap<Serializable, Collection<Map>> getPipelines() {
    ImmutableMap.copyOf(interestingPipelines)
  }

  @Override
  void run() {
    try {
      Map<Trigger, Collection<Map>> _interestingPipelines = [:]
      for (Map pipeline in pipelineConfigurationService.pipelines) {
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
      this.interestingPipelines = _interestingPipelines
    } catch (e) {
      e.printStackTrace()
    }
  }
}
