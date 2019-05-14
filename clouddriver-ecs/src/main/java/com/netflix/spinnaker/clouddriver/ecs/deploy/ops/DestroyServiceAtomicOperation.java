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
import com.amazonaws.services.ecs.model.DeleteServiceRequest;
import com.amazonaws.services.ecs.model.DeleteServiceResult;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DestroyServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ModifyServiceDescription, Void> {
  @Autowired EcsCloudMetricService ecsCloudMetricService;

  public DestroyServiceAtomicOperation(ModifyServiceDescription description) {
    super(description, "DESTROY_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Destroy Amazon ECS Server Group Operation...");
    AmazonECS ecs = getAmazonEcsClient();

    String ecsClusterName =
        containerInformationService.getClusterName(
            description.getServerGroupName(), description.getAccount(), description.getRegion());

    updateTaskStatus("Removing MetricAlarms from " + description.getServerGroupName() + ".");
    ecsCloudMetricService.deleteMetrics(
        description.getServerGroupName(), description.getAccount(), description.getRegion());
    updateTaskStatus("Done removing MetricAlarms from " + description.getServerGroupName() + ".");

    UpdateServiceRequest updateServiceRequest = new UpdateServiceRequest();
    updateServiceRequest.setService(description.getServerGroupName());
    updateServiceRequest.setDesiredCount(0);
    updateServiceRequest.setCluster(ecsClusterName);

    updateTaskStatus("Scaling " + description.getServerGroupName() + " server group down to 0.");
    ecs.updateService(updateServiceRequest);

    DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest();
    deleteServiceRequest.setService(description.getServerGroupName());
    deleteServiceRequest.setCluster(ecsClusterName);

    updateTaskStatus("Deleting " + description.getServerGroupName() + " server group.");
    DeleteServiceResult deleteServiceResult = ecs.deleteService(deleteServiceRequest);

    updateTaskStatus(
        "Deleting "
            + deleteServiceResult.getService().getTaskDefinition()
            + " task definition belonging to the server group.");
    ecs.deregisterTaskDefinition(
        new DeregisterTaskDefinitionRequest()
            .withTaskDefinition(deleteServiceResult.getService().getTaskDefinition()));

    return null;
  }
}
