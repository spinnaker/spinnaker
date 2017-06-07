/*
 * Copyright 2017 Cerner Corporation
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.dcos

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class DcosServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = "dcos"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // TODO: this is side-effecty and not good... but it works.
    //
    // Have to do this here because during a deploy stage in a pipeline run, a disconnect between how the region is
    // sent in from deck (which may contain forward slashes) and how the region is formatted and written by clouddriver
    // (using underscores) causes the ParallelDeployStage to fail when trying to lookup server groups keyed by region.
    // The map contains a region with underscores, but the lookup occurs using a region with forward slashes.
    stage.context.region = stage.context.region.replaceAll('/', '_')

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    DcosContainerFinder.populateFromStage(operation, stage)

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }
}
