/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.quip.InstanceHealthCheckTask
import com.netflix.spinnaker.orca.kato.tasks.quip.MonitorQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.TriggerQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.VerifyQuipTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class BulkQuickPatchStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("verifyQuipIsRunning", VerifyQuipTask)
      .withTask("triggerQuip", TriggerQuipTask)
      .withTask("monitorQuip", MonitorQuipTask)
      .withTask("instanceHealthCheck", InstanceHealthCheckTask)
      .withTask("waitForDiscoveryState", WaitForUpInstanceHealthTask)
  }
}
