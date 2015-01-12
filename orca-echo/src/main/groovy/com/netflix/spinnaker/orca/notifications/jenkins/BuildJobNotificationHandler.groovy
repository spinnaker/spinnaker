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

package com.netflix.spinnaker.orca.notifications.jenkins

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler

class BuildJobNotificationHandler extends AbstractNotificationHandler implements Runnable {
  static final String TRIGGER_TYPE = "jenkins"
  static final String TRIGGER_KEY = "job"
  static final String TRIGGER_MASTER = "master"

  final String handlerType = BuildJobPollingNotificationAgent.NOTIFICATION_TYPE
  final long pollingInterval = 60
  private Map<String, Map> interestingPipelines = [:]

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor().schedule(this, pollingInterval, TimeUnit.SECONDS)
  }

  @Override
  void run() {
    try {
      def _interestingPipelines = [:]
      for (Map pipeline in pipelineConfigurationService.pipelines) {
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
  void handleInternal(Map input) {
    try {
      String key = generateKey(input.master as String, input.name as String)
      if (interestingPipelines.containsKey(key)) {
        if (input.lastBuildStatus != "Success") return
        def pipelineConfigs = interestingPipelines[key]
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

  @VisibleForTesting
  static private String generateKey(String master, String job) {
    "$master:$job"
  }
}
