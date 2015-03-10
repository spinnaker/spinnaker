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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.IgorService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class BuildService {
  private static final String GROUP = "builds"

  @Autowired(required = false)
  IgorService igorService

  List<String> getBuildMasters() {
    if (!igorService) {
      return []
    }
    HystrixFactory.newListCommand(GROUP, "masters", true) {
      igorService.getBuildMasters()
    } execute()
  }


  List<String> getJobsForBuildMaster(String buildMaster) {
    if (!igorService) {
      return []
    }
    HystrixFactory.newListCommand(GROUP, "jobsForBuildMaster",true) {
      igorService.getJobsForBuildMaster(buildMaster)
    } execute()
  }

  Map getJobConfig(String buildMaster, String job) {
    if (!igorService) {
      return [:]
    }
    HystrixFactory.newMapCommand(GROUP, "jobConfig",true) {
      igorService.getJobConfig(buildMaster, job)
    } execute()
  }
}
