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

import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/pipelineConfigs")
class PipelineConfigController {
  @Autowired
  Front50Service front50Service

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @ApiOperation(value = "Get all pipeline configs.", response = List.class)
  @RequestMapping(method = RequestMethod.GET)
  Collection<Map> getAllPipelineConfigs() {
    return front50Service.getAllPipelineConfigs()
  }

  @ApiOperation(value = "Get pipeline config history.", response = List.class)
  @RequestMapping(value = "/{pipelineConfigId}/history", method = RequestMethod.GET)
  Collection<Map> getPipelineConfigHistory(@PathVariable("pipelineConfigId") String pipelineConfigId,
                                           @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return front50Service.getPipelineConfigHistory(pipelineConfigId, limit)
  }

  @ApiOperation(value = "Convert a pipeline config to a pipeline template.", response = String.class)
  @RequestMapping(value = "/{pipelineConfigId}/convertToTemplate", method = RequestMethod.GET)
  String convertPipelineConfigToPipelineTemplate(@PathVariable("pipelineConfigId") String pipelineConfigId) {
    Map pipelineConfig = front50Service.getAllPipelineConfigs().find { (pipelineConfigId == it.get("id")) }
    if (pipelineConfig == null) {
      throw new NotFoundException("Pipeline config '${pipelineConfigId}' could not be found")
    }
    String template = orcaServiceSelector.select().convertToPipelineTemplate(pipelineConfig).body.in().text
    return template
  }
}
