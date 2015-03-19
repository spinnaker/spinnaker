package com.netflix.spinnaker.orca.notifications.jenkins

import com.netflix.spinnaker.orca.igor.IgorService
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

  @Autowired(required = false)
  IgorService igorService

  BuildJobNotificationHandler(Map input) {
    super(input)
  }

  @Override
  void handle(Map input) {
    if (input.lastBuild?.result == "SUCCESS") {
      log.debug "Detected build $input.master $input.name ${input.lastBuild?.number}"
    }
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
          if(igorService && input.lastBuild?.number){
            pipelineConfigClone.trigger.buildInfo = igorService.getBuild(input.master, input.name, input.lastBuild?.number)
            if( pipelineConfigClone.trigger.propertyFile ) {
              pipelineConfigClone.trigger.properties = igorService.getPropertyFile(input.master, input.name, input.lastBuild?.number, pipelineConfigClone.trigger.propertyFile)
            }
          }
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
