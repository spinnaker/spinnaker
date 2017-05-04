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

import com.netflix.spinnaker.gate.services.PipelineService
import com.netflix.spinnaker.gate.services.PipelineService.PipelineConfigNotFoundException
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
import org.springframework.web.bind.annotation.*
import retrofit.RetrofitError

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelines")
class PipelineController {
  @Autowired
  PipelineService pipelineService

  @ApiOperation(value = "Delete a pipeline definition")
  @RequestMapping(value = "/{application}/{pipelineName:.+}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String application, @PathVariable String pipelineName) {
    pipelineService.deleteForApplication(application, pipelineName)
  }

  @ApiOperation(value = "Save a pipeline definition")
  @RequestMapping(value = '', method = RequestMethod.POST)
  void savePipeline(@RequestBody Map pipeline) {
    pipelineService.save(pipeline)
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
        throw new PipelineNotFoundException()
      }
    }
  }

  @ApiOperation(value = "Update a pipeline definition")
  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  Map updatePipeline(@PathVariable("id") String id, @RequestBody Map pipeline) {
    pipelineService.update(id, pipeline)
  }

  @ApiOperation(value = "Retrieve pipeline execution logs")
  @RequestMapping(value = "{id}/logs", method = RequestMethod.GET)
  List<Map> getPipelineLogs(@PathVariable("id") String id) {
    try {
      pipelineService.getPipelineLogs(id)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new PipelineNotFoundException()
      }
    }
  }

  @ApiOperation(value = "Cancel a pipeline execution")
  @RequestMapping(value = "{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id,
                     @RequestParam(required = false) String reason,
                     @RequestParam(defaultValue = "false") boolean force) {
    pipelineService.cancelPipeline(id, reason, force)
  }

  @ApiOperation(value = "Pause a pipeline execution")
  @RequestMapping(value = "{id}/pause", method = RequestMethod.PUT)
  Map pausePipeline(@PathVariable("id") String id) {
    pipelineService.pausePipeline(id)
  }

  @ApiOperation(value = "Resume a pipeline execution")
  @RequestMapping(value = "{id}/resume", method = RequestMethod.PUT)
  Map resumePipeline(@PathVariable("id") String id) {
    pipelineService.resumePipeline(id)
  }

  @ApiOperation(value = "Update a stage execution")
  @RequestMapping(value = "/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Map updateStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.updatePipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Restart a stage execution")
  @RequestMapping(value = "/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  Map restartStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.restartPipelineStage(id, stageId, context)
  }

  @ApiOperation(value = "Delete a pipeline execution")
  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  Map deletePipeline(@PathVariable("id") String id) {
    pipelineService.deletePipeline(id);
  }

  @ApiOperation(value = "Initiate a pipeline execution")
  @RequestMapping(value = '/start', method = RequestMethod.POST)
  Map start(@RequestBody Map map) {
    String authenticatedUser = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
    pipelineService.startPipeline(map, authenticatedUser)
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
    } catch (PipelineConfigNotFoundException e) {
      throw e
    } catch (e) {
      log.error("Unable to trigger pipeline (application: ${application}, pipelineName: ${pipelineNameOrId})", e)
      new ResponseEntity([message: e.message], new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class PipelineNotFoundException extends RuntimeException {}
}
