package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.notifications.PipelineIndexer

/**
 * Indexes pipelines known by mayo for consumption by a
 * {@link BuildJobNotificationHandler}.
 */
@CompileStatic
class BuildJobPipelineIndexer implements PipelineIndexer, Runnable {

  static final String TRIGGER_TYPE = "jenkins"
  static final String TRIGGER_KEY = "job"
  static final String TRIGGER_MASTER = "master"

  final long pollingInterval = 60
  private final PipelineConfigurationService pipelineConfigurationService
  private Map<String, Collection<Map>> interestingPipelines = [:]

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
      Map<String, Collection<Map>> _interestingPipelines = [:]
      for (Map pipeline in pipelineConfigurationService.pipelines) {
        def triggers = pipeline.triggers as List<Map>
        for (trigger in triggers) {
          if (trigger.type == TRIGGER_TYPE) {
            String key = generateKey(trigger[TRIGGER_MASTER] as String, trigger[TRIGGER_KEY] as String)
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

  private static String generateKey(String master, String job) {
    "$master:$job"
  }
}
