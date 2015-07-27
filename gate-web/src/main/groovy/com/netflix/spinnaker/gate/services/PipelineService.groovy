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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaService
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@CompileStatic
@Component
@Slf4j
class PipelineService {
  @Autowired(required = false)
  Front50Service front50Service

  @Autowired(required = false)
  EchoService echoService

  @Autowired
  OrcaService orcaService

  @Autowired
  ApplicationService applicationService

  void deleteForApplication(String applicationName, String pipelineName) {
    front50Service.deletePipelineConfig(applicationName, pipelineName)
  }

  void save(Map pipeline) {
    front50Service.savePipelineConfig(pipeline)
  }

  void move(Map moveCommand) {
    front50Service.movePipelineConfig(moveCommand)
  }

  Map trigger(String application, String pipelineName, Map trigger) {
    def pipelineConfig = applicationService.getPipelineConfig(application, pipelineName)
    if (!pipelineConfig) {
      throw new PipelineConfigNotFoundException()
    }
    pipelineConfig.trigger = trigger
    orcaService.startPipeline(pipelineConfig, trigger.user?.toString())
  }

  Map startPipeline(Map pipelineConfig, String user) {
    orcaService.startPipeline(pipelineConfig, user)
  }

  Map getPipeline(String id) {
    orcaService.getPipeline(id)
  }

  Map cancelPipeline(String id) {
    orcaService.cancelPipeline(id)
  }

  Map deletePipeline(String id) {
    orcaService.deletePipeline(id)
  }

  Map updatePipelineStage(String executionId, String stageId, Map context) {
    orcaService.updatePipelineStage(executionId, stageId, context)
  }

  @InheritConstructors
  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "pipeline config not found!")
  static class PipelineConfigNotFoundException extends RuntimeException {}
}
