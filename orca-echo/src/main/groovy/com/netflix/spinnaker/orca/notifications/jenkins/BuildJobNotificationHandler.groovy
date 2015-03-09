package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

@Slf4j
class BuildJobNotificationHandler extends AbstractNotificationHandler {

  public static final String TRIGGER_TYPE = "jenkins"

  final String handlerType = BuildJobPollingNotificationAgent.NOTIFICATION_TYPE

  @Autowired @Qualifier("buildJobPipelineIndexer")
  PipelineIndexer pipelineIndexer

  BuildJobNotificationHandler(Map input) {
    super(input)
  }

  @Override
  void handle(Map input) {
    try {
      def pipelines = pipelineIndexer.pipelines
      def key = new Trigger(input.master as String, input.name as String)
      if (pipelines.containsKey(key)) {
        if (input.lastBuild?.result != "SUCCESS" || input.lastBuild?.building) return
        def pipelineConfigs = pipelines[key]
        for (Map pipelineConfig in pipelineConfigs) {
          Map trigger = pipelineConfig.triggers.find {
            it.type == TRIGGER_TYPE && it.job == input.name && it.master == input.master
          } as Map
          def pipelineConfigClone = new HashMap(pipelineConfig)
          pipelineConfigClone.trigger = new HashMap(trigger)
          pipelineConfigClone.trigger.buildInfo = input
          def json = objectMapper.writeValueAsString(pipelineConfigClone)
          log.info "Starting pipeline '$pipelineConfig.name' for application '$pipelineConfig.application' due to Jenkins job '$key.job'"
          pipelineStarter.start(json)
        }
      }
    } catch (e) {
      e.printStackTrace()
      throw e
    }
  }
}
