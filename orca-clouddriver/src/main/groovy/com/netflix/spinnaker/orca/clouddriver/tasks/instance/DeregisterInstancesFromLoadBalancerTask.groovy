/*
 * Copyright 2016 Google, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeregisterInstancesFromLoadBalancerTask extends AbstractInstanceLoadBalancerRegistrationTask {
  TrafficGuard trafficGuard

  @Autowired
  DeregisterInstancesFromLoadBalancerTask(TrafficGuard trafficGuard) {
    this.trafficGuard = trafficGuard
  }

  @Override
  String getAction() {
    return "deregisterInstancesFromLoadBalancer"
  }

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    String serverGroupName = stage.context.serverGroupName ?: stage.context.asgName

    trafficGuard.verifyInstanceTermination(
      serverGroupName,
      MonikerHelper.monikerFromStage(stage, serverGroupName),
      stage.context.instanceIds as List<String>,
      account,
      Location.region(stage.context.region as String),
      cloudProvider,
      "Deregistering the requested instances from load balancer in")

    return super.execute(stage)
  }
}
