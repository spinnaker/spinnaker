/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import java.util.List;

public class DisableServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ModifyServiceDescription, Void> {

  public DisableServiceAtomicOperation(ModifyServiceDescription description) {
    super(description, "DISABLE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Disable Amazon ECS Server Group Operation...");
    disableService();
    return null;
  }

  private void disableService() {
    AmazonECS ecs = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    String cluster = getCluster(service, account);

    updateTaskStatus(String.format("Disabling %s server group for %s.", service, account));
    UpdateServiceRequest request =
        new UpdateServiceRequest().withCluster(cluster).withService(service).withDesiredCount(0);
    ecs.updateService(request);
    updateTaskStatus(String.format("Server group %s disabled for %s.", service, account));
  }
}
