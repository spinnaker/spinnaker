/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Process
import com.netflix.spinnaker.clouddriver.model.ProcessProvider
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/processes")
class ProcessController {

  @Autowired(required = false)
  List<ProcessProvider> processProviders

  @Autowired
  MessageSource messageSource

  @ApiOperation(value = "Get a Process", notes = "Usually associated with a `Job` object")
  @RequestMapping(value = "/{account}/{location}/{id:.+}", method = RequestMethod.GET)
  Process getProcess(@ApiParam(value = "Account process was created by", required = true) @PathVariable String account,
                     @ApiParam(value = "Namespace, region, or zone process is running in", required = true) @PathVariable String location,
                     @ApiParam(value = "Unique identifier of process being looked up", required = true) @PathVariable String id) {
    Collection<Process> processMatches = processProviders.findResults {
      it.getProcess(account, location, id)
    }
    if (!processMatches) {
      throw new ProcessNotFoundException(name: id)
    }
    processMatches.first()
  }

  static class ProcessNotFoundException extends RuntimeException {
    String name
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map processNotFoundException(ProcessNotFoundException ex) {
    def message = messageSource.getMessage("process.not.found", [ex.name] as String[], "Process not found", LocaleContextHolder.locale)
    [error: "process.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map badRequestException(IllegalArgumentException ex) {
    [error: 'invalid.request', message: ex.message, status: HttpStatus.BAD_REQUEST]
  }
}
