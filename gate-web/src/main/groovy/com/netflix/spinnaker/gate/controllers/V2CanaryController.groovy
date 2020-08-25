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
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.*

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

  @ApiOperation(value = 'Retrieve a list of descriptors for use in populating the canary config ui')
  @RequestMapping(value = '/metadata/metricsService', method = RequestMethod.GET)
  List listMetricsServiceMetadata(@RequestParam(required = false) final String filter,
                                  @RequestParam(required = false) final String metricsAccountName) {
    v2CanaryService.listMetricsServiceMetadata(filter, metricsAccountName)
  }

  @ApiOperation(value = 'Retrieve a list of all configured canary judges')
  @RequestMapping(value = '/judges', method = RequestMethod.GET)
  List listJudges() {
    v2CanaryService.listJudges()
  }

  @ApiOperation(value = 'Start a canary execution')
  @RequestMapping(value = '/canary/{canaryConfigId:.+}', method = RequestMethod.POST)
  Map initiateCanary(@PathVariable String canaryConfigId,
                     @RequestBody Map executionRequest,
                     @RequestParam(value = 'application', required = false) String application,
                     @RequestParam(value = 'parentPipelineExecutionId', required = false) String parentPipelineExecutionId,
                     @RequestParam(value = 'metricsAccountName', required = false) String metricsAccountName,
                     @RequestParam(value = 'storageAccountName', required = false) String storageAccountName,
                     @RequestParam(value = 'configurationAccountName', required = false) String configurationAccountName) {
    v2CanaryService.initiateCanary(canaryConfigId,
                                   executionRequest,
                                   application,
                                   parentPipelineExecutionId,
                                   metricsAccountName,
                                   storageAccountName,
                                   configurationAccountName)
  }

  @ApiOperation(value = 'Start a canary execution with the supplied canary config')
  @RequestMapping(value = '/canary', method = RequestMethod.POST)
  Map initiateCanaryWithConfig(@RequestBody Map adhocExecutionRequest,
                               @RequestParam(value = 'application', required = false) String application,
                               @RequestParam(value = 'parentPipelineExecutionId', required = false) String parentPipelineExecutionId,
                               @RequestParam(value = 'metricsAccountName', required = false) String metricsAccountName,
                               @RequestParam(value = 'storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.initiateCanaryWithConfig(adhocExecutionRequest,
      application,
      parentPipelineExecutionId,
      metricsAccountName,
      storageAccountName)
  }

  // TODO: Change callers to the new endpoint sans canary config id in Spinnaker 1.17.x.
  @ApiOperation(value = '(DEPRECATED) Retrieve a canary result')
  @RequestMapping(value = '/canary/{canaryConfigId}/{canaryExecutionId}', method = RequestMethod.GET)
  @Deprecated
  Map getCanaryResult(@PathVariable String canaryConfigId /* unused */,
                      @PathVariable String canaryExecutionId,
                      @RequestParam(value='storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.getCanaryResults(canaryExecutionId, storageAccountName)
  }

  @ApiOperation(value = 'Retrieve a canary result')
  @RequestMapping(value = '/canary/{canaryExecutionId}', method = RequestMethod.GET)
  Map getCanaryResult(@PathVariable String canaryExecutionId,
                      @RequestParam(value='storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.getCanaryResults(canaryExecutionId, storageAccountName)
  }

  @ApiOperation(value = 'Retrieve a list of an application\'s canary results')
  @RequestMapping(value = '/{application}/executions', method = RequestMethod.GET)
  List getCanaryResultsByApplication(@PathVariable String application,
                                     @RequestParam(value='limit') int limit,
                                     @RequestParam(value='page', defaultValue='1') int page,
                                     @ApiParam('Comma-separated list of statuses, e.g.: RUNNING, SUCCEEDED, TERMINAL')
                                     @RequestParam(value='statuses', required = false) String statuses,
                                     @RequestParam(value='storageAccountName', required = false) String storageAccountName) {
    v2CanaryService.getCanaryResultsByApplication(application, limit, page, statuses, storageAccountName)
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
