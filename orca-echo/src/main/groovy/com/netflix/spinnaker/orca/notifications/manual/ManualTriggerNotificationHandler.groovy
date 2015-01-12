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

package com.netflix.spinnaker.orca.notifications.manual

import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler

class ManualTriggerNotificationHandler extends AbstractNotificationHandler implements Runnable {
  String handlerType = ManualTriggerPollingNotificationAgent.NOTIFICATION_TYPE
  long pollingInterval = 15

  private Map<PipelineId, Map> indexedPipelines = [:]

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this, 0, pollingInterval, TimeUnit.SECONDS)
  }

  void run() {
    try {
      def _indexedPipelines = [:]
      for (pipeline in pipelineConfigurationService.pipelines) {
        def id = new PipelineId(pipeline.application as String, pipeline.name as String)
        _indexedPipelines[id] = pipeline
      }
      indexedPipelines = _indexedPipelines
    } catch (e) {
      e.printStackTrace()
    }
  }

  @Override
  void handleInternal(Map input) {
    def id = new PipelineId(input.application as String, input.name as String)
    if (indexedPipelines.containsKey(id)) {
      def config = new HashMap(indexedPipelines[id])
      config.trigger = [type: "manual", user: input.user]
      def json = objectMapper.writeValueAsString(config)
      pipelineStarter.start(json)
    }
  }

  @Immutable
  @EqualsAndHashCode
  static class PipelineId {
    String application
    String name
  }
}
