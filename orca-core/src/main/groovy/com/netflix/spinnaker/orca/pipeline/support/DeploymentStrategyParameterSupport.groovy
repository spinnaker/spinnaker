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

package com.netflix.spinnaker.orca.pipeline.support

import com.netflix.spinnaker.orca.pipeline.model.Pipeline

class DeploymentStrategyParameterSupport {

  static void resolveStrategyParams(stage) {
    if (stage.execution instanceof Pipeline && stage.execution.trigger.parameters?.strategy == true) {
      Map trigger = ((Pipeline) stage.execution).trigger
      stage.context.cloudProvider = trigger.parameters.cloudProvider
      stage.context.cluster = trigger.parameters.cluster
      stage.context.credentials = trigger.parameters.credentials
      if (trigger.parameters.region) {
        stage.context.regions = [trigger.parameters.region]
      } else if (trigger.parameters.zone) {
        stage.context.zones = [trigger.parameters.zone]
      }
    }
  }

}
