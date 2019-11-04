/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.alicloud

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class AliCloudServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  final String cloudProvider = "alicloud";

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    List<Map> list = operation.scalingConfigurations
    if(list.size() == 0){
      throw new IllegalStateException("No Configuration could be found in ${stage.context.region}.")
    }
    withImageFromPrecedingStage(stage, null, cloudProvider) {
      for(int i = 0; i < list.size(); i ++){
        Map map = list.get(i)
        map.imageId = map.imageId ?: it.imageId
      }
    }

    withImageFromDeploymentDetails(stage, null, cloudProvider) {
      for(int i = 0; i < list.size(); i ++){
        Map map = list.get(i)
        map.imageId = map.imageId ?: it.imageId
      }
    }

    if (!list.get(0).imageId) {
      throw new IllegalStateException("No imageId could be found in ${stage.context.region}.")
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }

  @Override
  boolean isKatoResultExpected() {
    return false
  }

  @Override
  String getCloudProvider() {
    return cloudProvider
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("AlibabaCloud");
  }
}
