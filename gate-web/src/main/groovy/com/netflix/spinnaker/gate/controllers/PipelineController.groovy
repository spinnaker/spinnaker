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
import groovy.transform.CompileStatic
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
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/pipelines")
@RestController
class PipelineController {
  @Autowired
  PipelineService pipelineService

  @RequestMapping(value = "/{applicationName}/{pipelineName:.+}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String applicationName, @PathVariable String pipelineName) {
    pipelineService.deleteForApplication(applicationName, pipelineName)
  }

  @RequestMapping(method = RequestMethod.POST)
  void savePipeline(@RequestBody Map pipeline) {
    pipelineService.save(pipeline)
  }

  @RequestMapping(value = 'move', method = RequestMethod.POST)
  void renamePipeline(@RequestBody Map renameCommand) {
    pipelineService.move(renameCommand)
  }

  @RequestMapping(value = "{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id) {
    pipelineService.cancelPipeline(id)
  }

  @RequestMapping(value = "/{applicationName}/{pipelineName:.+}", method = RequestMethod.POST, params = ['user'])
  HttpEntity invokePipelineConfig(@PathVariable("applicationName") String application,
                                  @PathVariable("pipelineName") String pipelineName,
                                  @RequestParam("user") String user) {
    try {
      def body = pipelineService.trigger(application, pipelineName, user)
      new ResponseEntity(body, HttpStatus.ACCEPTED)
    } catch (e) {
      new ResponseEntity([message: e.message], new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }
}
