/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications.scheduling

import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import groovy.util.logging.Slf4j
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Slf4j
class SuspendedPipelinesNotificationHandler extends AbstractNotificationHandler {

  String handlerType = SuspendedPipelinesPollingNotificationAgent.NOTIFICATION_TYPE

  SuspendedPipelinesNotificationHandler(Map input) {
    super(input)
  }

  @Override
  void handle(Map pipeline) {
    try {
      executionRunner.restart(executionRepository.retrieve(PIPELINE, pipeline.id as String))
    } catch (e) {
      log.error("Unable to resume pipeline", e)
      throw e
    }
  }
}
