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

import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.TemplateSource
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@ConditionalOnProperty("pipelineTemplate.enabled")
@RestController
@Slf4j
class PipelineTemplateController {

  @Autowired
  PipelineTemplateService pipelineTemplateService

  @RequestMapping(value = "/pipelineTemplate", method = RequestMethod.GET)
  PipelineTemplate getPipelineTemplate(@RequestParam("source") String source) {
    if (source == null || source?.empty) {
      throw new InvalidTemplateSourceException("template source must not be empty")
    }

    pipelineTemplateService.resolveTemplate(new TemplateSource(source: source))
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(InvalidTemplateSourceException)
  Map invalidTemplateSourceHandler(InvalidTemplateSourceException e) {
    return [message: e.message, status: HttpStatus.BAD_REQUEST]
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(TemplateLoaderException)
  Map templateLoadingErrorHandler(TemplateLoaderException e) {
    return [message: e.message, status: HttpStatus.BAD_REQUEST]
  }

  @InheritConstructors
  static class InvalidTemplateSourceException extends RuntimeException {}
}
