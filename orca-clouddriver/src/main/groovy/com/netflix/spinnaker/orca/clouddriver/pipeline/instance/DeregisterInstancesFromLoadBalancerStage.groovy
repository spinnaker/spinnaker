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


package com.netflix.spinnaker.orca.clouddriver.pipeline.instance

import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.DeregisterInstancesFromLoadBalancerTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DeregisterInstancesFromLoadBalancerStage implements StageDefinitionBuilder {
  @Autowired
  OortService oortService

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    //TODO(cfieber): locking support
    // this probably needs to move the determination of server group name for instances to a synthetic stage that runs before
    // and an acquire lock for each of the clusters for those server groups
    builder
      .withTask("deregisterInstances", DeregisterInstancesFromLoadBalancerTask)
      .withTask("monitorInstances", MonitorKatoTask)
      .withTask("waitForLoadBalancerState", WaitForDownInstanceHealthTask)
  }
}

