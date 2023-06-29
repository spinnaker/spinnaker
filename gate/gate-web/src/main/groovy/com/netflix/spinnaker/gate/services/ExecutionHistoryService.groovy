/*
 * Copyright 2015 Netflix, Inc.
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

import com.google.common.base.Preconditions
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.ObjectUtils

@Component
@Slf4j
class ExecutionHistoryService {
  @Autowired
  OrcaServiceSelector orcaServiceSelector

  List getTasks(String app, Integer page, Integer limit, String statuses) {
    Preconditions.checkNotNull(app)

    Retrofit2SyncCall.execute(orcaServiceSelector.select().getTasks(app, page, limit, statuses))
  }

  List getPipelines(String app, Integer limit, String statuses, Boolean expand, String pipelineNameFilter = null) {
    Preconditions.checkNotNull(app)
    def pipelines = Retrofit2SyncCall.execute(orcaServiceSelector.select().getPipelines(app, limit, statuses, expand))
    if (!ObjectUtils.isEmpty(pipelineNameFilter)) {
      /*
      Doing a typecast here on the name property because we don't have the pipeline execution json
      modeled in Gate. It would be good to add that modeling, but it's not worth to do it now.
      The right thing to do here is to pass through the pipelineNameFilter to orca, and have orca
      do the filtering. We need a temporary solution in Gate (so no reason to go crazy with the implementation),
      but will pass through the param to Orca eventually.
       */
      def originalPipelinesSize = pipelines.size()
      pipelines = pipelines.findAll {(it.name as String).toLowerCase().contains(pipelineNameFilter.toLowerCase())}
      log.debug(
        "found {} executions for application {} after filtering by pipelineNameFilter {}. Un-filtered execution size {}",
        pipelines.size(),
        app,
        pipelineNameFilter,
        originalPipelinesSize)
      return pipelines
    }
    log.debug("received {} executions for application: {}", pipelines.size(), app)
    return pipelines
  }
}
