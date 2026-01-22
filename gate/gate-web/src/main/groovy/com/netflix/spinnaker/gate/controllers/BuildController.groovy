/*
 * Copyright 2014 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import jakarta.servlet.http.HttpServletRequest

@Slf4j
@CompileStatic
@RestController
class BuildController {

  private final static String DEPRECATION_NOTICE_MESSAGE = "Invocation of deprecated endpoint"

  /*
   * Job names can have '/' in them if using the Jenkins Folder plugin.
   * Because of this, always put the job name at the end of the URL.
   */
  @Autowired
  BuildService buildService

  @Operation(summary = "Get build masters", description = "Deprecated, use the v3 endpoint instead")
  @RequestMapping(value = "v2/builds", method = RequestMethod.GET)
  List<String> getBuildMasters(@RequestParam(value = "type", defaultValue = "") String type) {
    log.debug(DEPRECATION_NOTICE_MESSAGE)
    buildService.getBuildMasters(type)
  }

  @Operation(summary = "Get jobs for build master", description = "Deprecated, use the v3 endpoint instead")
  @RequestMapping(value = "/v2/builds/{buildMaster}/jobs", method = RequestMethod.GET)
  List<String> getJobsForBuildMaster(@PathVariable("buildMaster") String buildMaster) {
    log.debug(DEPRECATION_NOTICE_MESSAGE)
    buildService.getJobsForBuildMaster(buildMaster)
  }

  @Operation(summary = "Get job config", description = "Deprecated, use the v3 endpoint instead")
  @RequestMapping(value = "/v2/builds/{buildMaster}/jobs/**", method = RequestMethod.GET)
  Map getJobConfig(@PathVariable("buildMaster") String buildMaster, HttpServletRequest request) {
    log.debug(DEPRECATION_NOTICE_MESSAGE)
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(5).join('/')
    buildService.getJobConfig(buildMaster, job)
  }

  @Operation(summary = "Get builds for build master", description = "Deprecated, use the v3 endpoint instead")
  @RequestMapping(value = "/v2/builds/{buildMaster}/builds/**", method = RequestMethod.GET)
  List getBuilds(@PathVariable("buildMaster") String buildMaster, HttpServletRequest request) {
    log.debug(DEPRECATION_NOTICE_MESSAGE)
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(5).join('/')
    buildService.getBuilds(buildMaster, job)
  }

  @Operation(summary = "Get build for build master", description = "Deprecated, use the v3 endpoint instead")
  @RequestMapping(value = "/v2/builds/{buildMaster}/build/{number}/**", method = RequestMethod.GET)
  Map getBuild(@PathVariable("buildMaster") String buildMaster, @PathVariable("number") String number, HttpServletRequest request) {
    log.debug(DEPRECATION_NOTICE_MESSAGE)
    def job = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().split('/').drop(6).join('/')
    buildService.getBuild(buildMaster, job, number)
  }

  /**
   * Version 3 of the builds API: The `**` path parameter breaks any Swagger-generated client.
   *
   * In this version, the job name is moved from a path parameter to a required query parameter wherever its used.
   */

  @Operation(summary = "Get build masters")
  @RequestMapping(value = "v3/builds", method = RequestMethod.GET)
  List<String> v3GetBuildMasters(@RequestParam(value = "type", defaultValue = "") String type) {
    buildService.getBuildMasters(type)
  }

  @Operation(summary = "Get jobs for build master")
  @RequestMapping(value = "/v3/builds/{buildMaster}/jobs", method = RequestMethod.GET)
  List<String> v3GetJobsForBuildMaster(@PathVariable("buildMaster") String buildMaster) {
    buildService.getJobsForBuildMaster(buildMaster)
  }

  @Operation(summary = "Get job config")
  @RequestMapping(value = "/v3/builds/{buildMaster}/job", method = RequestMethod.GET)
  Map v3GetJobConfig(@PathVariable("buildMaster") String buildMaster,
                     @RequestParam(value = "job", required = true) String job) {
    buildService.getJobConfig(buildMaster, job)
  }

  @Operation(summary = "Get builds for build master")
  @RequestMapping(value = "/v3/builds/{buildMaster}/builds", method = RequestMethod.GET)
  List v3GetBuilds(@PathVariable("buildMaster") String buildMaster,
                   @RequestParam(value = "job", required = true) String job) {
    buildService.getBuilds(buildMaster, job)
  }

  @Operation(summary = "Get build for build master")
  @RequestMapping(value = "/v3/builds/{buildMaster}/build/{number}", method = RequestMethod.GET)
  Map v3GetBuild(@PathVariable("buildMaster") String buildMaster,
                 @PathVariable("number") String number,
                 @RequestParam(value = "job", required = true) String job) {
    buildService.getBuild(buildMaster, job, number)
  }

}
