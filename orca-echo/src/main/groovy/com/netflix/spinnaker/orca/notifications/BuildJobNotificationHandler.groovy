/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired

class BuildJobNotificationHandler implements NotificationHandler, Runnable {

  static final String INTERESTING_STEP = "jenkins"
  static final String TRIGGER_KEY = "job"

  @Autowired
  MayoService mayoService

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ApplicationInfoManager applicationInfoManager

  private Map<String, Map> interestingPipelines = [:]

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this, 0, 120, TimeUnit.SECONDS)
  }

  @Override
  void run() {
    try {
      def _interestingPipelines = [:]
      List<Map> pipelines = objectMapper.readValue(mayoService.pipelines.body.in().text, new TypeReference<List<Map>>() {
      })
      for (Map pipeline in pipelines) {
        List<Map> stages = pipeline.stages
        for (Map stage in stages) {
          if (stage.type == INTERESTING_STEP) {
            if (!_interestingPipelines.containsKey(stage[TRIGGER_KEY] as String)) {
              _interestingPipelines[stage[TRIGGER_KEY] as String] = []
            }
            _interestingPipelines[stage[TRIGGER_KEY] as String] << pipeline
          }
        }
      }
      this.interestingPipelines = _interestingPipelines
    } catch (e) {
      e.printStackTrace()
    }
  }

  @Override
  boolean handles(String type) {
    type == BuildJobPollingNotificationAgent.NOTIFICATION_TYPE
  }

  @Override
  void handle(Map input) {
    if (applicationInfoManager?.info?.status == InstanceInfo.InstanceStatus.OUT_OF_SERVICE) {
      return
    }
    try {
      if (interestingPipelines.containsKey(input.name)) {
        if (input.lastBuildStatus != "Success") return
        def pipelineConfigs = interestingPipelines[input.name as String]
        for (Map pipelineConfig in pipelineConfigs) {
          def jenkinsStageIdx = pipelineConfig.stages.findIndexOf {
            it.type == "jenkins"
          }
          def stages = (jenkinsStageIdx + 1..pipelineConfig.stages.size() - 1).collect { int n ->
            pipelineConfig.stages[n]
          }
          def config = [application: pipelineConfig.application, name: pipelineConfig.name, stages: stages]
          def json = objectMapper.writeValueAsString(config)
          pipelineStarter.start(json).subscribe()
        }
      }
    } catch (e) {
      e.printStackTrace()
      throw e
    }
  }
}
