/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.cf

import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryDeployServiceTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryMonitorKatoServicesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryWaitForDeployServiceTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class CloudFoundryDeployServiceStagePreprocessorSpec extends Specification {
  @Subject preprocessor = new CloudFoundryDeployServiceStagePreprocessor()

  def "ensure that the correct tasks are added for deploying a Cloud Foundry service"() {
    given:
    TaskNode.Builder builder = new TaskNode.Builder(TaskNode.GraphType.FULL)
    Stage stage = new Stage(Execution.newPipeline("orca"), "deployService", [
      "cloudProvider": "my-cloud",
      "manifest": [
        "type": "direct"
      ]
    ])

    when:
    preprocessor.addSteps(builder, stage)

    then:
    builder.graph.size() == 3
    (builder.graph.get(0) as TaskNode.TaskDefinition).name == "deployService"
    (builder.graph.get(0) as TaskNode.TaskDefinition).implementingClass == CloudFoundryDeployServiceTask
    (builder.graph.get(1) as TaskNode.TaskDefinition).name == "monitorDeployService"
    (builder.graph.get(1) as TaskNode.TaskDefinition).implementingClass == CloudFoundryMonitorKatoServicesTask
    (builder.graph.get(2) as TaskNode.TaskDefinition).name == "waitForDeployService"
    (builder.graph.get(2) as TaskNode.TaskDefinition).implementingClass == CloudFoundryWaitForDeployServiceTask
  }
}
