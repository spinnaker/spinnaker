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

import com.netflix.spinnaker.clouddriver.model.JobProvider
import com.netflix.spinnaker.clouddriver.model.JobStatus
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/applications/{application}/jobs")
class JobController {

  @Autowired(required = false)
  List<JobProvider> jobProviders

  @Autowired
  MessageSource messageSource

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE') and hasPermission(#account, 'ACCOUNT', 'WRITE')")
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
      throw new NotFoundException("Job not found (account: ${account}, location: ${location}, id: ${id})")
    }
    jobMatches.first()
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE') and hasPermission(#account, 'ACCOUNT', 'WRITE')")
  @ApiOperation(value = "Collect a JobStatus", notes = "Collects the output of the job, may modify the job.")
  @RequestMapping(value = "/{account}/{location}/{id:.+}", method = RequestMethod.DELETE)
  void cancelJob(@ApiParam(value = "Application name", required = true) @PathVariable String application,
                 @ApiParam(value = "Account job was created by", required = true) @PathVariable String account,
                 @ApiParam(value = "Namespace, region, or zone job is running in", required = true) @PathVariable String location,
                 @ApiParam(value = "Unique identifier of job being looked up", required = true) @PathVariable String id) {
    jobProviders.forEach {
      it.cancelJob(account, location, id)
    }
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE') and hasPermission(#account, 'ACCOUNT', 'WRITE')")
  @ApiOperation(value = "Collect a file from a job", notes = "Collects the file result of a job.")
  @RequestMapping(value = "/{account}/{location}/{id}/{fileName:.+}", method = RequestMethod.GET)
  Map<String, Object> getFileContents(
                       @ApiParam(value = "Application name", required = true) @PathVariable String application,
                       @ApiParam(value = "Account job was created by", required = true) @PathVariable String account,
                       @ApiParam(value = "Namespace, region, or zone job is running in", required = true) @PathVariable String location,
                       @ApiParam(value = "Unique identifier of job being looked up", required = true) @PathVariable String id,
                       @ApiParam(value = "File name to look up", required = true) @PathVariable String fileName
  ) {
    jobProviders.findResults {
      it.getFileContents(account, location, id, fileName)
    }.first()
  }
}
