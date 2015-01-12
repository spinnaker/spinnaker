package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.InheritConstructors
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component
@Scope(SCOPE_PROTOTYPE)
@InheritConstructors
class BuildJobNotificationHandler extends AbstractNotificationHandler {

  public static final String TRIGGER_TYPE = "jenkins"

  final String handlerType = BuildJobPollingNotificationAgent.NOTIFICATION_TYPE

  @Autowired PipelineIndexer pipelineIndexer

  @Override
  void handle(Map input) {
    try {
      def pipelines = pipelineIndexer.pipelines
      def key = new Trigger(input.master as String, input.name as String)
      if (pipelines.containsKey(key)) {
        if (input.lastBuildStatus != "Success") return
        def pipelineConfigs = pipelines[key]
        for (Map pipelineConfig in pipelineConfigs) {
          Map trigger = pipelineConfig.triggers.find {
            it.type == TRIGGER_TYPE && it.job == input.name && it.master == input.master
          } as Map
          def pipelineConfigClone = new HashMap(pipelineConfig)
          pipelineConfigClone.trigger = new HashMap(trigger)
          pipelineConfigClone.trigger.buildInfo = input
          def json = objectMapper.writeValueAsString(pipelineConfigClone)
          pipelineStarter.start(json)
        }
      }
    } catch (e) {
      e.printStackTrace()
      throw e
    }
  }
}
