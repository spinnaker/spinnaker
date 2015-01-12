package com.netflix.spinnaker.orca.notifications.manual

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.notifications.PipelineIndexer

@CompileStatic
class ManualTriggerPipelineIndexer implements PipelineIndexer, Runnable {

  long pollingInterval = 15

  private final PipelineConfigurationService pipelineConfigurationService
  private Map<ManualTriggerNotificationHandler.PipelineId, Collection<Map>> indexedPipelines = [:]

  ManualTriggerPipelineIndexer(PipelineConfigurationService pipelineConfigurationService) {
    this.pipelineConfigurationService = pipelineConfigurationService
  }

  @Override
  ImmutableMap<Serializable, Map> getPipelines() {
    ImmutableMap.copyOf(indexedPipelines)
  }

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this, 0, pollingInterval, TimeUnit.SECONDS)
  }

  @Override
  void run() {
    try {
      def _indexedPipelines = [:]
      for (pipeline in pipelineConfigurationService.pipelines) {
        def id = new ManualTriggerNotificationHandler.PipelineId(pipeline.application as String, pipeline.name as String)
        _indexedPipelines[id] = [pipeline]
      }
      indexedPipelines = _indexedPipelines
    } catch (e) {
      e.printStackTrace()
    }
  }

}
