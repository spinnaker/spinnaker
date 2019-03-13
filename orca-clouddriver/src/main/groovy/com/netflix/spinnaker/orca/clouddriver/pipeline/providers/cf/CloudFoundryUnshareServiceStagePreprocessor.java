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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.cf;

import com.netflix.spinnaker.orca.clouddriver.pipeline.servicebroker.UnshareServiceStagePreprocessor;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryMonitorKatoServicesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryUnshareServiceTask;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryUnshareServiceStagePreprocessor implements UnshareServiceStagePreprocessor {
  @Override
  public boolean supports(Stage stage) {
    return "cloudfoundry".equals(stage.mapTo(StageData.class).getCloudProvider());
  }

  @Override
  public void addSteps(TaskNode.Builder builder, Stage stage) {
    builder
      .withTask("unshareService", CloudFoundryUnshareServiceTask.class)
      .withTask("monitorUnshareService", CloudFoundryMonitorKatoServicesTask.class);
  }
}
