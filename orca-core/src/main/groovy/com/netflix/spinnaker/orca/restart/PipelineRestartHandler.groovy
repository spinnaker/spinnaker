/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.restart

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import static java.util.Collections.emptyMap
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component
@Scope(SCOPE_PROTOTYPE)
@Slf4j
@CompileStatic
class PipelineRestartHandler extends AbstractNotificationHandler {

  @Autowired PipelineJobBuilder pipelineJobBuilder
  @Autowired ExecutionRepository executionRepository
  @Autowired Registry registry

  PipelineRestartHandler() {
    super(emptyMap())
  }

  PipelineRestartHandler(Map input) {
    super(input)
  }

  @Override
  String getHandlerType() {
    PipelineRestartAgent.NOTIFICATION_TYPE
  }

  @Override
  void handle(Map input) {
    try {
      def pipeline = executionRepository.retrievePipeline(input.id as String)
      log.warn "Restarting pipeline $pipeline.application $pipeline.name $pipeline.id with status $pipeline.status"
      pipelineStarter.resume(pipeline)
      registry.counter("pipeline.restarts").increment()
    } catch (IllegalStateException e) {
      log.error("Unable to resume pipeline: $e.message")
      registry.counter("pipeline.failed.restarts").increment()
    } catch (e) {
      log.error("Unable to resume pipeline", e)
      registry.counter("pipeline.failed.restarts").increment()
      throw e
    }
  }
}
