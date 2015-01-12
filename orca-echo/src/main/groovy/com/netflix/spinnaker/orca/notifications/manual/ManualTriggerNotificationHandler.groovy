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
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.notifications.PipelineIndexer

class ManualTriggerNotificationHandler extends AbstractNotificationHandler implements Runnable {

  String handlerType = ManualTriggerPollingNotificationAgent.NOTIFICATION_TYPE

  private final PipelineIndexer pipelineIndexer

  ManualTriggerNotificationHandler(PipelineIndexer pipelineIndexer) {
    this.pipelineIndexer = pipelineIndexer
  }

  @Override
  void run() {

  }

  @Override
  void handleInternal(Map input) {
    def id = new PipelineId(input.application as String, input.name as String)
    def pipelines = pipelineIndexer.pipelines
    if (pipelines.containsKey(id)) {
      def config = new HashMap(pipelines[id][0])
      config.trigger = [type: "manual", user: input.user]
      def json = objectMapper.writeValueAsString(config)
      pipelineStarter.start(json)
    }
  }

  @Immutable
  @EqualsAndHashCode
  static class PipelineId implements Serializable {
    String application
    String name
  }
}
