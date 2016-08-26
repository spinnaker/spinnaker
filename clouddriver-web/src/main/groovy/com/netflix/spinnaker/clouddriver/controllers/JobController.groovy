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

import com.netflix.spinnaker.clouddriver.model.JobStatus
import com.netflix.spinnaker.clouddriver.model.JobProvider
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications/{application}/jobs")
class JobController {

  @Autowired(required = false)
  List<JobProvider> jobProviders

  @Autowired
  MessageSource messageSource

  @ApiOperation(value = "Collect a JobStatus", notes = "Collects the output of the job, may modify the job.")
  @RequestMapping(value = "/{account}/{location}/{id:.+}", method = RequestMethod.POST)
  JobStatus collectJob(@ApiParam(value = "Application name", required = true) @PathVariable String application,
                       @ApiParam(value = "Account job was created by", required = true) @PathVariable String account,
                       @ApiParam(value = "Namespace, region, or zone job is running in", required = true) @PathVariable String location,
                       @ApiParam(value = "Unique identifier of job being looked up", required = true) @PathVariable String id) {
    Collection<JobStatus> jobMatches = jobProviders.findResults {
      it.collectJob(account, location, id)
    }
    if (!jobMatches) {
      throw new JobNotFoundException(name: id)
    }
    jobMatches.first()
  }

  static class JobNotFoundException extends RuntimeException {
    String name
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map jobNotFoundException(JobNotFoundException ex) {
    def message = messageSource.getMessage("job.not.found", [ex.name] as String[], "JobStatus not found", LocaleContextHolder.locale)
    [error: "job.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map badRequestException(IllegalArgumentException ex) {
    [error: 'invalid.request', message: ex.message, status: HttpStatus.BAD_REQUEST]
  }
}
