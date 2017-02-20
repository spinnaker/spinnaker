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

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelines")
class PipelineController {
  @Autowired
  PipelineService pipelineService

  @RequestMapping(value = "/{application}/{pipelineName:.+}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String application, @PathVariable String pipelineName) {
    pipelineService.deleteForApplication(application, pipelineName)
  }

  @RequestMapping(value = '', method = RequestMethod.POST)
  void savePipeline(@RequestBody Map pipeline) {
    pipelineService.save(pipeline)
  }

  @RequestMapping(value = 'move', method = RequestMethod.POST)
  void renamePipeline(@RequestBody Map renameCommand) {
    pipelineService.move(renameCommand)
  }

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

  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  Map updatePipeline(@PathVariable("id") String id, @RequestBody Map pipeline) {
    pipelineService.update(id, pipeline)
  }

  @RequestMapping(value = "{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id,
                     @RequestParam(required = false) String reason,
                     @RequestParam(defaultValue = "false") boolean force) {
    pipelineService.cancelPipeline(id, reason, force)
  }

  @RequestMapping(value = "{id}/pause", method = RequestMethod.PUT)
  Map pausePipeline(@PathVariable("id") String id) {
    pipelineService.pausePipeline(id)
  }

  @RequestMapping(value = "{id}/resume", method = RequestMethod.PUT)
  Map resumePipeline(@PathVariable("id") String id) {
    pipelineService.resumePipeline(id)
  }

  @RequestMapping(value = "/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Map updateStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.updatePipelineStage(id, stageId, context)
  }

  @RequestMapping(value = "/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  Map restartStage(@PathVariable("id") String id, @PathVariable("stageId") String stageId, @RequestBody Map context) {
    pipelineService.restartPipelineStage(id, stageId, context)
  }

  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  Map deletePipeline(@PathVariable("id") String id) {
    pipelineService.deletePipeline(id);
  }

  @RequestMapping(value = '/start', method = RequestMethod.POST)
  Map start(@RequestBody Map map) {
    String authenticatedUser = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
    pipelineService.startPipeline(map, authenticatedUser)
  }

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
