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
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.Example
import io.swagger.annotations.ExampleProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
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
  @DeleteMapping("/{application}/{pipelineName:.+}")
  void deletePipeline(@PathVariable String application, @PathVariable String pipelineName) {
    pipelineService.deleteForApplication(application, pipelineName)
  }

  @CompileDynamic
  @ApiOperation(value = "Save a pipeline definition")
  @PostMapping('')
  void savePipeline(
    @RequestBody Map pipeline,
    @RequestParam(value = "staleCheck", required = false, defaultValue = "false")
      Boolean staleCheck) {
    def operation = [
      description: (String) "Save pipeline '${pipeline.get("name") ?: "Unknown"}'",
      application: pipeline.get('application'),
      job        : [
        [
          type      : "savePipeline",
          pipeline  : (String) Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).getBytes("UTF-8")),
          user      : AuthenticatedRequest.spinnakerUser.orElse("anonymous"),
          staleCheck: staleCheck
        ]
      ]
    ]
    def result = taskService.createAndWaitForCompletion(operation)
    String resultStatus = result.get("status")

    if (!"SUCCEEDED".equalsIgnoreCase(resultStatus)) {
      String exception = result.variables.find { it.key == "exception" }?.value?.details?.errors?.getAt(0)
      throw new PipelineException(
        exception ?: "Pipeline save operation did not succeed: ${result.get("id", "unknown task id")} (status: ${resultStatus})"
      )
    }
  }

  @ApiOperation(value = "Rename a pipeline definition")
  @PostMapping('move')
  void renamePipeline(@RequestBody Map renameCommand) {
    pipelineService.move(renameCommand)
  }

  @ApiOperation(value = "Retrieve a pipeline execution")
  @GetMapping("{id}")
  Map getPipeline(@PathVariable("id") String id) {
    try {
      pipelineService.getPipeline(id)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @CompileDynamic
  @ApiOperation(value = "Update a pipeline definition", response = HashMap.class)
  @PutMapping("{id}")
  Map updatePipeline(@PathVariable("id") String id, @RequestBody Map pipeline) {
    def operation = [
      description: (String) "Update pipeline '${pipeline.get("name") ?: 'Unknown'}'",
      application: (String) pipeline.get('application'),
      job        : [
        [
          type    : 'updatePipeline',
          pipeline: (String) Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes),
          user    : AuthenticatedRequest.spinnakerUser.orElse("anonymous")
        ]
      ]
    ]

    def result = taskService.createAndWaitForCompletion(operation)
    String resultStatus = result.get("status")

    if (!"SUCCEEDED".equalsIgnoreCase(resultStatus)) {
      String exception = result.variables.find { it.key == "exception" }?.value?.details?.errors?.getAt(0)
      throw new PipelineException(
        exception ?: "Pipeline save operation did not succeed: ${result.get("id", "unknown task id")} (status: ${resultStatus})"
      )
    }

    return front50Service.getPipelineConfigsForApplication((String) pipeline.get("application"), true)?.find {
      id == (String) it.get("id")
    }
  }

  @ApiOperation(value = "Cancel a pipeline execution")
  @PutMapping("{id}/cancel")
  void cancelPipeline(@PathVariable("id") String id,
                      @RequestParam(required = false) String reason,
                      @RequestParam(defaultValue = "false") boolean force) {
    pipelineService.cancelPipeline(id, reason, force)
  }

  @ApiOperation(value = "Pause a pipeline execution")
  @PutMapping("{id}/pause")
  void pausePipeline(@PathVariable("id") String id) {
    pipelineService.pausePipeline(id)
  }

  @ApiOperation(value = "Resume a pipeline execution", response = HashMap.class)
  @PutMapping("{id}/resume")
  void resumePipeline(@PathVariable("id") String id) {
    pipelineService.resumePipeline(id)
  }

  @ApiOperation(value = "Update a stage execution", response = HashMap.class)
  @PatchMapping("/{id}/stages/{stageId}")
  Map updateStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.updatePipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Restart a stage execution", response = HashMap.class)
  @PutMapping("/{id}/stages/{stageId}/restart")
  Map restartStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.restartPipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Delete a pipeline execution", response = HashMap.class)
  @DeleteMapping("{id}")
  Map deletePipeline(@PathVariable("id") String id) {
    pipelineService.deletePipeline(id);
  }

  @ApiOperation(value = "Initiate a pipeline execution")
  @PostMapping('/start')
  ResponseEntity start(@RequestBody Map map) {
    if (map.containsKey("application")) {
      AuthenticatedRequest.setApplication(map.get("application").toString())
    }
    String authenticatedUser = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
    maybePropagateTemplatedPipelineErrors(map, {
      pipelineService.startPipeline(map, authenticatedUser)
    })
  }

  @ApiOperation(value = "Trigger a pipeline execution")
  @PostMapping("/{application}/{pipelineNameOrId:.+}")
  @ResponseBody
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map invokePipelineConfig(@PathVariable("application") String application,
                           @PathVariable("pipelineNameOrId") String pipelineNameOrId,
                           @RequestBody(required = false) Map trigger) {
    trigger = trigger ?: [:]
    trigger.user = trigger.user ?: AuthenticatedRequest.getSpinnakerUser().orElse('anonymous')
    trigger.notifications = trigger.notifications ?: [];

    AuthenticatedRequest.setApplication(application)
    try {
      pipelineService.trigger(application, pipelineNameOrId, trigger)
    } catch (NotFoundException e) {
      throw e
    } catch (e) {
      log.error("Unable to trigger pipeline (application: {}, pipelineId: {})",
        value("application", application), value("pipelineId", pipelineNameOrId), e)
      throw new PipelineExecutionException(e.message)
    }
  }

  @ApiOperation(value = "Trigger a pipeline execution", response = Map.class)
  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'EXECUTE')")
  @PostMapping("/v2/{application}/{pipelineNameOrId:.+}")
  HttpEntity invokePipelineConfigViaEcho(@PathVariable("application") String application,
                                         @PathVariable("pipelineNameOrId") String pipelineNameOrId,
                                         @RequestBody(required = false) Map trigger) {
    trigger = trigger ?: [:]
    AuthenticatedRequest.setApplication(application)
    try {
      def body = pipelineService.triggerViaEcho(application, pipelineNameOrId, trigger)
      return new ResponseEntity(body, HttpStatus.ACCEPTED)
    } catch (e) {
      log.error("Unable to trigger pipeline (application: {}, pipelineId: {})",
        value("application", application), value("pipelineId", pipelineNameOrId), e)
      throw e
    }
  }

  @ApiOperation(value = "Evaluate a pipeline expression using the provided execution as context", response = HashMap.class)
  @GetMapping("{id}/evaluateExpression")
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

  @ApiOperation(value = "Evaluate a pipeline expression using the provided execution as context", response = HashMap.class)
  @PostMapping(value = "{id}/evaluateExpression", consumes = "text/plain")
  Map evaluateExpressionForExecutionViaPOST(@PathVariable("id") String id,
                                            @RequestBody String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, pipelineExpression)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @ApiOperation(value = "Evaluate a pipeline expression at a specific stage using the provided execution as context", response = HashMap.class)
  @GetMapping("{id}/{stageId}/evaluateExpression")
  Map evaluateExpressionForExecutionAtStage(@PathVariable("id") String id,
                                            @PathVariable("stageId") String stageId,
                                            @RequestParam("expression") String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecutionAtStage(id, stageId, pipelineExpression)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})", e)
      }
    }
  }

  @ApiOperation(value = "Evaluate a pipeline expression using the provided execution as context", response = HashMap.class)
  @PostMapping(value = "{id}/evaluateExpression", consumes = "application/json")
  Map evaluateExpressionForExecutionViaPOST(@PathVariable("id") String id,
                                            @RequestBody Map pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, (String) pipelineExpression.expression)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @ApiOperation(value = "Evaluate variables same as Evaluate Variables stage using the provided execution as context", response = HashMap.class)
  @PostMapping(value = "{id}/evaluateVariables", consumes = "application/json")
  Map evaluateVariables(@ApiParam(value = "Execution id to run against", required = true)
                        @RequestParam("executionId") String executionId,
                        @ApiParam(value = "Comma separated list of requisite stage IDs for the evaluation stage", required = false)
                        @RequestParam(value = "requisiteStageRefIds", defaultValue = "") String requisiteStageRefIds,
                        @ApiParam(value = "Version of SpEL evaluation logic to use (v3 or v4)", required = false)
                        @RequestParam(value = "spelVersion", defaultValue = "") String spelVersionOverride,
                        @ApiParam(value = "List of variables/expressions to evaluate",
                          required = true,
                          examples = @Example(value =
                            @ExampleProperty(mediaType = "application/json", value = '[{"key":"a","value":"1"},{"key":"b","value":"2"},{"key":"sum","value":"${a+b}"}]')
                          ))
                        @RequestBody List<Map<String, String>> expressions) {
    try {
      return pipelineService.evaluateVariables(executionId, requisiteStageRefIds, spelVersionOverride, expressions)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new NotFoundException("Pipeline not found (id: ${executionId})")
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

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @InheritConstructors
  class PipelineExecutionException extends InvalidRequestException {
    PipelineExecutionException(String message) {
      super(message)
    }
  }
}
