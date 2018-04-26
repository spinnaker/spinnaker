/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.gate.security.RequestContext
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelineConfigs")
class PipelineConfigController {
  private static final String HYSTRIX_GROUP = "pipelineConfigs"

  @Autowired
  Front50Service front50Service

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @RequestMapping(method = RequestMethod.GET)
  Collection<Map> getAllPipelineConfigs() {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "getAllPipelineConfigs") {
      front50Service.getAllPipelineConfigs()
    }.execute()
  }

  @RequestMapping(value = "/{pipelineConfigId}/history", method = RequestMethod.GET)
  Collection<Map> getPipelineConfigHistory(@PathVariable("pipelineConfigId") String pipelineConfigId,
                                           @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "getPipelineConfigHistory") {
      front50Service.getPipelineConfigHistory(pipelineConfigId, limit)
    }.execute()
  }

  @RequestMapping(value = "/{pipelineConfigId}/convertToTemplate", method = RequestMethod.GET)
  String convertPipelineConfigToPipelineTemplate(@PathVariable("pipelineConfigId") String pipelineConfigId) {
    Map pipelineConfig = HystrixFactory.newMapCommand(HYSTRIX_GROUP, "getPipelineConfig") {
      front50Service.getAllPipelineConfigs().find { (pipelineConfigId == it.get("id")) }
    }.execute()
    if (pipelineConfig == null) {
      throw new NotFoundException("Pipeline config '${pipelineConfigId}' could not be found")
    }
    String template = orcaServiceSelector.withContext(RequestContext.get()).convertToPipelineTemplate(pipelineConfig).body.in().text
    return template
  }
}
