/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.gate.services.V2CanaryService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping('/v2/canaries')
@ConditionalOnExpression('${services.kayenta.enabled:false}')
class V2CanaryController {

  @Autowired
  V2CanaryService v2CanaryService

  @ApiOperation(value = 'Retrieve a list of configured Kayenta accounts')
  @RequestMapping(value = '/credentials', method = RequestMethod.GET)
  List listCredentials() {
    v2CanaryService.getCredentials()
  }

  @ApiOperation(value = 'Retrieve a list of all configured canary judges')
  @RequestMapping(value = '/judges', method = RequestMethod.GET)
  List listJudges() {
    v2CanaryService.listJudges()
  }

  @ApiOperation(value = 'Retrieve a canary result')
  @RequestMapping(value = '/canary/{canaryConfigId}/{canaryExecutionId}', method = RequestMethod.GET)
  Map getCanaryResult(@PathVariable String canaryConfigId,
                      @PathVariable String canaryExecutionId,
                      @RequestParam(value='storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.getCanaryResults(canaryExecutionId, storageAccountName)
  }


  // TODO(dpeach): remove this endpoint when a Kayenta endpoint for
  // retrieving a single metric set pair exists.
  @ApiOperation(value = 'Retrieve a metric set pair list')
  @RequestMapping(value = '/metricSetPairList/{metricSetPairListId}', method = RequestMethod.GET)
  List getMetricSetPairList(@PathVariable String metricSetPairListId,
                            @RequestParam(value='storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.getMetricSetPairList(metricSetPairListId, storageAccountName)
  }
}
