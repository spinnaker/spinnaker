/*
 * Copyright 2016 Lookout Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class EcsServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  final String cloudProvider = "ecs"
  final boolean katoResultExpected = false

  final Optional<String> healthProviderName = Optional.of("ecs")

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    def bakeStage = getPreviousStageWithImage(stage, operation.region, cloudProvider)

    if (bakeStage) {
      operation.put('dockerImageAddress', bakeStage.context.amiDetails.imageId.value.get(0).toString())
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }

}
