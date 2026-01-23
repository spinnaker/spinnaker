/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.converter.PipelineTemplateConverter
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.TemplateSource
import groovy.util.logging.Slf4j
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ConditionalOnExpression("\${pipeline-templates.enabled:true}")
@RestController
@Slf4j
class PipelineTemplateController {

  @Autowired
  PipelineTemplateService pipelineTemplateService

  @RequestMapping(value = "/pipelineTemplate", method = RequestMethod.GET)
  PipelineTemplate getPipelineTemplate(@RequestParam String source,
                                       @RequestParam(required = false) String executionId,
                                       @RequestParam(required = false) String pipelineConfigId) {
    if (!source) {
      throw new InvalidRequestException("template source must not be empty")
    }
    pipelineTemplateService.resolveTemplate(new TemplateSource(source: source), executionId, pipelineConfigId)
  }

  @RequestMapping(value = "/convertPipelineToTemplate", method = RequestMethod.POST, produces = 'text/x-yaml')
  String convertPipelineToPipelineTemplate(@RequestBody Map<String, Object> pipeline) {
    new PipelineTemplateConverter().convertToPipelineTemplate(pipeline)
  }

  @ExceptionHandler(TemplateLoaderException)
  static void handleTemplateLoaderException(TemplateLoaderException tle, HttpServletResponse response) {
    log.error("Could not load pipeline template from source: {}", tle.message)
    response.sendError(HttpStatus.BAD_REQUEST.value(), "Could not load pipeline template from source")
  }
}
