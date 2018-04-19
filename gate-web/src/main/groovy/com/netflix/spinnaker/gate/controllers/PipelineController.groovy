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


package com.netflix.spinnaker.gate.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.services.PipelineService

import com.netflix.spinnaker.kork.web.exceptions.HasAdditionalAttributes
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

import static net.logstash.logback.argument.StructuredArguments.value

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelines")
class PipelineController {
  @Autowired
  PipelineService pipelineService

  @Autowired
  TaskService taskService

  @Autowired
  Front50Service front50Service

  @Autowired
  ObjectMapper objectMapper

  @ApiOperation(value = "Delete a pipeline definition")
  @RequestMapping(value = "/{application}/{pipelineName:.+}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String application, @PathVariable String pipelineName) {
    pipelineService.deleteForApplication(application, pipelineName)
  }

  @ApiOperation(value = "Save a pipeline definition")
  @RequestMapping(value = '', method = RequestMethod.POST)
  void savePipeline(@RequestBody Map pipeline) {
    def operation = [
      description: (String) "Save pipeline '${pipeline.get("name") ?: "Unknown"}'",
      application: pipeline.get('application'),
      job: [
        [
          type: "savePipeline",
          pipeline: (String) Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).getBytes("UTF-8")),
          user: AuthenticatedRequest.spinnakerUser.orElse("anonymous")
        ]
      ]
    ]
    def result = taskService.createAndWaitForCompletion(operation)

    if ("TERMINAL".equalsIgnoreCase((String) result.get("status"))) {
      throw new PipelineException("Pipeline save operation failed with terminal status: ${result.get("id", "unknown task id")}")
    }
  }

  @ApiOperation(value = "Rename a pipeline definition")
  @RequestMapping(value = 'move', method = RequestMethod.POST)
  void renamePipeline(@RequestBody Map renameCommand) {
    pipelineService.move(renameCommand)
  }

  @ApiOperation(value = "Retrieve a pipeline execution")
  @RequestMapping(value = "{id}", method = RequestMethod.GET)
  Map getPipeline(@PathVariable("id") String id) {
    try {
      pipelineService.getPipeline(id)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @ApiOperation(value = "Update a pipeline definition", response = HashMap.class)
  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  Map updatePipeline(@PathVariable("id") String id, @RequestBody Map pipeline) {
    def operation = [
      description: (String) "Update pipeline '${pipeline.get("name") ?: 'Unknown'}'",
      application: (String) pipeline.get('application'),
      job: [
        [
          type: 'updatePipeline',
          pipeline: (String) Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes),
          user: AuthenticatedRequest.spinnakerUser.orElse("anonymous")
        ]
      ]
    ]

    def result = taskService.createAndWaitForCompletion(operation)
    String resultStatus = result.get("status")

    if ("TERMINAL".equalsIgnoreCase(resultStatus)) {
      throw new PipelineException("Pipeline save operation failed with terminal status: ${result.get("id", "unknown task id")}")
    }
    if (!"SUCCEEDED".equalsIgnoreCase(resultStatus)) {
      throw new PipelineException("Pipeline save operation did not succeed: ${result.get("id", "unknown task id")} (status: ${resultStatus})")
    }

    return front50Service.getPipelineConfigsForApplication((String) pipeline.get("application"), true)?.find { id == (String) it.get("id") }
  }

  @ApiOperation(value = "Retrieve pipeline execution logs", response = HashMap.class, responseContainer = "List")
  @RequestMapping(value = "{id}/logs", method = RequestMethod.GET)
  List<Map> getPipelineLogs(@PathVariable("id") String id) {
    try {
      pipelineService.getPipelineLogs(id)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @ApiOperation(value = "Cancel a pipeline execution", response = HashMap.class)
  @RequestMapping(value = "{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id,
                     @RequestParam(required = false) String reason,
                     @RequestParam(defaultValue = "false") boolean force) {
    pipelineService.cancelPipeline(id, reason, force)
  }

  @ApiOperation(value = "Pause a pipeline execution", response = HashMap.class)
  @RequestMapping(value = "{id}/pause", method = RequestMethod.PUT)
  Map pausePipeline(@PathVariable("id") String id) {
    pipelineService.pausePipeline(id)
  }

  @ApiOperation(value = "Resume a pipeline execution", response = HashMap.class)
  @RequestMapping(value = "{id}/resume", method = RequestMethod.PUT)
  Map resumePipeline(@PathVariable("id") String id) {
    pipelineService.resumePipeline(id)
  }

  @ApiOperation(value = "Update a stage execution", response = HashMap.class)
  @RequestMapping(value = "/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Map updateStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.updatePipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Restart a stage execution", response = HashMap.class)
  @RequestMapping(value = "/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  Map restartStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.restartPipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Delete a pipeline execution", response = HashMap.class)
  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  Map deletePipeline(@PathVariable("id") String id) {
    pipelineService.deletePipeline(id);
  }

  @ApiOperation(value = "Initiate a pipeline execution")
  @RequestMapping(value = '/start', method = RequestMethod.POST)
  ResponseEntity start(@RequestBody Map map) {
    String authenticatedUser = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
    maybePropagateTemplatedPipelineErrors(map, {
      pipelineService.startPipeline(map, authenticatedUser)
    })
  }

  @ApiOperation(value = "Trigger a pipeline execution")
  @RequestMapping(value = "/{application}/{pipelineNameOrId:.+}", method = RequestMethod.POST)
  HttpEntity invokePipelineConfig(@PathVariable("application") String application,
                                  @PathVariable("pipelineNameOrId") String pipelineNameOrId,
                                  @RequestBody(required = false) Map trigger) {
    trigger = trigger ?: [:]
    trigger.user = trigger.user ?: AuthenticatedRequest.getSpinnakerUser().orElse('anonymous')
    trigger.notifications = trigger.notifications ?: [];

    try {
      def body = pipelineService.trigger(application, pipelineNameOrId, trigger)
      new ResponseEntity(body, HttpStatus.ACCEPTED)
    } catch (NotFoundException e) {
      throw e
    } catch (e) {
      log.error("Unable to trigger pipeline (application: {}, pipelineId: {})",
        value("application", application), value("pipelineId", pipelineNameOrId), e)
      new ResponseEntity([message: e.message], new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }

  @ApiOperation(value = "Evaluate a pipeline expression using the provided execution as context", response = HashMap.class)
  @RequestMapping(value = "{id}/evaluateExpression")
  Map evaluateExpressionForExecution(@PathVariable("id") String id,
                                     @RequestParam("expression") String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, pipelineExpression)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  private ResponseEntity maybePropagateTemplatedPipelineErrors(Map requestBody, Closure<Map> call) {
    try {
      def body = call()
      new ResponseEntity(body, HttpStatus.OK)
    } catch (RetrofitError re) {
      if (re.response?.status == HttpStatus.BAD_REQUEST.value() && requestBody.type == "templatedPipeline") {
        throw new PipelineException((HashMap<String, Object>) re.getBodyAs(HashMap.class))
      } else {
        throw re
      }
    }
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @InheritConstructors
  class PipelineException extends RuntimeException implements HasAdditionalAttributes {
    Map<String, Object> additionalAttributes = [:]

    PipelineException(String message) {
      super(message)
    }

    PipelineException(Map<String, Object> additionalAttributes) {
      this.additionalAttributes = additionalAttributes
    }
  }
}
