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
import com.google.common.annotations.VisibleForTesting
import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BuildJobNotificationHandler implements NotificationHandler, Runnable {

  static final String TRIGGER_TYPE = "jenkins"
  static final String TRIGGER_KEY = "job"
  static final String TRIGGER_MASTER = "master"

  @Autowired
  MayoService mayoService

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  @Qualifier("appInfoManager")
  ApplicationInfoManager appInfoManager

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
        List<Map> triggers = pipeline.triggers
        for (Map trigger in triggers) {
          if (trigger.type == TRIGGER_TYPE) {
            String key = generateKey(trigger[TRIGGER_MASTER], trigger[TRIGGER_KEY])
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

  @Override
  boolean handles(String type) {
    type == BuildJobPollingNotificationAgent.NOTIFICATION_TYPE
  }

  @Override
  void handle(Map input) {
    if (appInfoManager?.info?.status == InstanceInfo.InstanceStatus.OUT_OF_SERVICE) {
      return
    }
    try {
      String key = generateKey(input.master as String, input.name as String)
      if (interestingPipelines.containsKey(key)) {
        if (input.lastBuildStatus != "Success") return
        def pipelineConfigs = interestingPipelines[key]
        for (Map pipelineConfig in pipelineConfigs) {
          Map trigger = pipelineConfig.triggers.find {
            it.type == "jenkins" && it.job == input.name && it.master == input.master
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

  @VisibleForTesting
  static private String generateKey(String master, String job) {
    "$master:$job"
  }
}
