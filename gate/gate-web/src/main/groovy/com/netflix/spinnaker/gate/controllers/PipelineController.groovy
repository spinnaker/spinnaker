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
import com.netflix.spinnaker.gate.config.controllers.PipelineControllerConfigProperties
import com.netflix.spinnaker.gate.services.PipelineService
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

import java.nio.charset.StandardCharsets

import static net.logstash.logback.argument.StructuredArguments.value

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelines")
class PipelineController {
  final PipelineService pipelineService
  final TaskService taskService
  final Front50Service front50Service
  final ObjectMapper objectMapper
  final PipelineControllerConfigProperties pipelineControllerConfigProperties

  @Autowired
  PipelineController(PipelineService pipelineService,
                     TaskService taskService,
                     Front50Service front50Service,
                     ObjectMapper objectMapper,
                     PipelineControllerConfigProperties pipelineControllerConfigProperties) {
    this.pipelineService = pipelineService
    this.taskService = taskService
    this.front50Service = front50Service
    this.objectMapper = objectMapper
    this.pipelineControllerConfigProperties = pipelineControllerConfigProperties
  }

  @CompileDynamic
  @Operation(summary = "Delete a pipeline definition")
  @DeleteMapping("/{application}/{pipelineName:.+}")
  void deletePipeline(@PathVariable String application, @PathVariable String pipelineName) {
    List<Map> pipelineConfigs = Retrofit2SyncCall.execute(front50Service.getPipelineConfigsForApplication(application, null, true))
    if (pipelineConfigs!=null && !pipelineConfigs.isEmpty()){
      Optional<Map> filterResult = pipelineConfigs.stream().filter({ pipeline -> ((String) pipeline.get("name")) != null && ((String) pipeline.get("name")).trim().equalsIgnoreCase(pipelineName) }).findFirst()
      if (filterResult.isPresent()){
        Map pipeline = filterResult.get()

        def operation = [
          description: (String) "Delete pipeline '${pipeline.get("name") ?: 'Unknown'}'",
          application: (String) pipeline.get('application'),
          job        : [
            [
              type    : 'deletePipeline',
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
            exception ?: "Pipeline delete operation did not succeed: ${result.get("id", "unknown task id")} (status: ${resultStatus})"
          )
        }
      }
    }
  }

  @CompileDynamic
  @Operation(summary = "Save a pipeline definition")
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

  @CompileDynamic
  @Operation(summary = "Save a list of pipelines")
  @PostMapping('/bulksave')
  Map bulksavePipeline(
    @RequestParam(defaultValue = "bulk_save_placeholder_app")
    @Parameter(description = "Application in which to run the bulk save task",
      example = "bulk_save_placeholder_app",
      required = false) String application,
    @RequestBody List<Map> pipelines) {
    def operation = [
      description: "Bulk save pipelines",
      application: application,
      job        : [
        [
          type                      : "savePipeline",
          pipelines                 : Base64.encoder
            .encodeToString(objectMapper.writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8)),
          user                      : AuthenticatedRequest.spinnakerUser.orElse("anonymous"),
          isBulkSavingPipelines : true
        ]
      ]
    ]

    def result = taskService.createAndWaitForCompletion(operation,
      pipelineControllerConfigProperties.getBulksave().getMaxPollsForTaskCompletion(),
      pipelineControllerConfigProperties.getBulksave().getTaskCompletionCheckIntervalMs())
    String resultStatus = result.get("status")

    if (!"SUCCEEDED".equalsIgnoreCase(resultStatus)) {
      String exception = result.variables.find { it.key == "exception" }?.value?.details?.errors?.getAt(0)
      throw new PipelineException(
        exception ?: "Pipeline bulk save operation did not succeed: ${result.get("id", "unknown task id")} " +
          "(status: ${resultStatus})"
      )
    } else {
      def retVal = result.variables.find { it.key == "bulksave"}?.value
      return retVal
    }
  }

  @Operation(summary = "Rename a pipeline definition")
  @PostMapping('move')
  void renamePipeline(@RequestBody Map renameCommand) {
    pipelineService.move(renameCommand)
  }

  @Operation(summary = "Retrieve a pipeline execution")
  @GetMapping("{id}")
  Map getPipeline(@PathVariable("id") String id) {
    try {
      pipelineService.getPipeline(id)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @CompileDynamic
  @Operation(summary = "Update a pipeline definition")
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

    return Retrofit2SyncCall.execute(front50Service.getPipelineConfigsForApplication((String) pipeline.get("application"), null, true))?.find {
      id == (String) it.get("id")
    }
  }

  @Operation(summary = "Cancel a pipeline execution")
  @PutMapping("{id}/cancel")
  void cancelPipeline(@PathVariable("id") String id,
                      @RequestParam(required = false) String reason,
                      @RequestParam(defaultValue = "false") boolean force) {
    pipelineService.cancelPipeline(id, reason, force)
  }

  @Operation(summary = "Pause a pipeline execution")
  @PutMapping("{id}/pause")
  void pausePipeline(@PathVariable("id") String id) {
    pipelineService.pausePipeline(id)
  }

  @Operation(summary = "Resume a pipeline execution")
  @PutMapping("{id}/resume")
  void resumePipeline(@PathVariable("id") String id) {
    pipelineService.resumePipeline(id)
  }

  @Operation(summary = "Update a stage execution")
  @PatchMapping("/{id}/stages/{stageId}")
  Map updateStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.updatePipelineStage(id, stageId, context)
  }

  @Operation(summary = "Restart a stage execution")
  @PutMapping("/{id}/stages/{stageId}/restart")
  Map restartStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    Map pipelineMap = getPipeline(id)

    String pipelineName = pipelineMap.get("name");
    String application = pipelineMap.get("application");

    List<Map> pipelineConfigs = Retrofit2SyncCall.execute(front50Service.getPipelineConfigsForApplication(application, null, true))

    if (pipelineConfigs!=null && !pipelineConfigs.isEmpty()){
      Optional<Map> filterResult = pipelineConfigs.stream()
        .filter({pipeline -> ((String) pipeline.get("name")) != null && ((String) pipeline.get("name")).trim().equalsIgnoreCase(pipelineName)})
        .findFirst()
      if (filterResult.isPresent()){
        context = filterResult.get()
      }
	}

    pipelineService.restartPipelineStage(id, stageId, context)
  }

  @Operation(summary = "Delete a pipeline execution")
  @DeleteMapping("{id}")
  void deletePipeline(@PathVariable("id") String id) {
    pipelineService.deletePipeline(id);
  }

  @Operation(summary = "Initiate a pipeline execution")
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

  @Operation(summary = "Trigger a pipeline execution")
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
    } catch (SpinnakerException e) {
      throw e.newInstance(triggerFailureMessage(application, pipelineNameOrId, e));
    }
  }

  private String triggerFailureMessage(String application, String pipelineNameOrId, Throwable e) {
    String.format("Unable to trigger pipeline (application: %s, pipelineNameOrId: %s). Error: %s",
        value("application", application), value("pipelineId", pipelineNameOrId), e.getMessage())
  }

  @Operation(summary = "Trigger a pipeline execution")
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

  @Operation(summary = "Evaluate a pipeline expression using the provided execution as context")
  @GetMapping("{id}/evaluateExpression")
  Map evaluateExpressionForExecution(@PathVariable("id") String id,
                                     @RequestParam("expression") String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, pipelineExpression)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @Operation(summary = "Evaluate a pipeline expression using the provided execution as context")
  @PostMapping(value = "{id}/evaluateExpression", consumes = "text/plain")
  Map evaluateExpressionForExecutionViaPOST(@PathVariable("id") String id,
                                            @RequestBody String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, pipelineExpression)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @Operation(summary = "Evaluate a pipeline expression at a specific stage using the provided execution as context")
  @GetMapping("{id}/{stageId}/evaluateExpression")
  Map evaluateExpressionForExecutionAtStage(@PathVariable("id") String id,
                                            @PathVariable("stageId") String stageId,
                                            @RequestParam("expression") String pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecutionAtStage(id, stageId, pipelineExpression)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})", e)
      }
    }
  }

  @Operation(summary = "Evaluate a pipeline expression using the provided execution as context")
  @PostMapping(value = "{id}/evaluateExpression", consumes = "application/json")
  Map evaluateExpressionForExecutionViaPOST(@PathVariable("id") String id,
                                            @RequestBody Map pipelineExpression) {
    try {
      pipelineService.evaluateExpressionForExecution(id, (String) pipelineExpression.expression)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${id})")
      }
    }
  }

  @Operation(summary = "Evaluate variables same as Evaluate Variables stage using the provided execution as context")
  @PostMapping(value = "{id}/evaluateVariables", consumes = "application/json")
  Map evaluateVariables(@Parameter(description = "Execution id to run against", required = true)
                        @RequestParam("executionId") String executionId,
                        @Parameter(description = "Comma separated list of requisite stage IDs for the evaluation stage", required = false)
                        @RequestParam(value = "requisiteStageRefIds", defaultValue = "") String requisiteStageRefIds,
                        @Parameter(description = "Version of SpEL evaluation logic to use (v3 or v4)", required = false)
                        @RequestParam(value = "spelVersion", defaultValue = "") String spelVersionOverride,
                        @Parameter(description = "List of variables/expressions to evaluate",
                          required = true,
                          example = '[{"key":"a","value":"1"},{"key":"b","value":"2"},{"key":"sum","value":"${a+b}"}]'
                        )
                        @RequestBody List<Map<String, String>> expressions) {
    try {
      return pipelineService.evaluateVariables(executionId, requisiteStageRefIds, spelVersionOverride, expressions)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new NotFoundException("Pipeline not found (id: ${executionId})")
      }
    }
  }

  private ResponseEntity maybePropagateTemplatedPipelineErrors(Map requestBody, Closure<Map> call) {
    try {
      def body = call()
      new ResponseEntity(body, HttpStatus.OK)
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == HttpStatus.BAD_REQUEST.value() && requestBody.type == "templatedPipeline") {
        throw new PipelineException(e.responseBody)
      }
      throw e
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
