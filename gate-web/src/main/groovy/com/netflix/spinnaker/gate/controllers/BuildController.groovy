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

import com.netflix.spinnaker.gate.services.BuildService
import groovy.transform.CompileStatic
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.HttpServletRequest

@CompileStatic
@RestController
class BuildController {
  /*
   * Job names can have '/' in them if using the Jenkins Folder plugin.
   * Because of this, always put the job name at the end of the URL.
   */
  @Autowired
  BuildService buildService

  @ApiOperation(value = "Get build masters", response = List.class)
  @RequestMapping(value = "v2/builds", method = RequestMethod.GET)
  List<String> getBuildMasters() {
    buildService.getBuildMasters()
  }

  @ApiOperation(value = "Get jobs for build master", response = List.class)
  @RequestMapping(value = "/v2/builds/{buildMaster}/jobs", method = RequestMethod.GET)
  List<String> getJobsForBuildMaster(@PathVariable("buildMaster") String buildMaster) {
    buildService.getJobsForBuildMaster(buildMaster)
  }

  @ApiOperation(value = "Get job config", response = HashMap.class)
  @RequestMapping(value = "/v2/builds/{buildMaster}/jobs/**", method = RequestMethod.GET)
  Map getJobConfig(@PathVariable("buildMaster") String buildMaster, HttpServletRequest request) {
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(5).join('/')
    buildService.getJobConfig(buildMaster, job)
  }

  @ApiOperation(value = "Get builds for build master", response = List.class)
  @RequestMapping(value = "/v2/builds/{buildMaster}/builds/**", method = RequestMethod.GET)
  List getBuilds(@PathVariable("buildMaster") String buildMaster, HttpServletRequest request) {
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(5).join('/')
    buildService.getBuilds(buildMaster, job)
  }

  @ApiOperation(value = "Get build for build master", response = HashMap.class)
  @RequestMapping(value = "/v2/builds/{buildMaster}/build/{number}/**", method = RequestMethod.GET)
  Map getBuild(@PathVariable("buildMaster") String buildMaster, @PathVariable("number") String number, HttpServletRequest request) {
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(6).join('/')
    buildService.getBuild(buildMaster, job, number)
  }

}
