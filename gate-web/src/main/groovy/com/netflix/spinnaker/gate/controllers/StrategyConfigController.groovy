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
@RequestMapping("/strategyConfigs")
class StrategyConfigController {

  @Autowired
  Front50Service front50Service

  @RequestMapping(method = RequestMethod.GET)
  Collection<Map> getAllStrategyConfigs() {
    return front50Service.getAllStrategyConfigs()
  }

  @RequestMapping(value = "/{strategyConfigId}/history", method = RequestMethod.GET)
  Collection<Map> getPipelineConfigHistory(@PathVariable("strategyConfigId") String strategyConfigId,
                                           @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return front50Service.getStrategyConfigHistory(strategyConfigId, limit)
  }
}
