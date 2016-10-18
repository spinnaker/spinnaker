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

package com.netflix.spinnaker.orca.clouddriver.pipeline.instance

import com.netflix.spinnaker.orca.clouddriver.tasks.instance.CaptureInstanceUptimeTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.VerifyInstanceUptimeTask
import com.netflix.spinnaker.orca.commands.InstanceUptimeCommand
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.RebootInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class RebootInstancesStage implements StageDefinitionBuilder {
  @Autowired(required = false)
  InstanceUptimeCommand instanceUptimeCommand

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)

    if (instanceUptimeCommand) {
      builder.withTask("captureInstanceUptime", CaptureInstanceUptimeTask)
    }

    builder
      .withTask("rebootInstances", RebootInstancesTask)
      .withTask("monitorReboot", MonitorKatoTask)
      .withTask("waitForDownInstances", WaitForDownInstanceHealthTask)

    if (instanceUptimeCommand) {
      builder.withTask("verifyInstanceUptime", VerifyInstanceUptimeTask)
    }

    builder
      .withTask("waitForUpInstances", WaitForUpInstanceHealthTask)
  }
}
