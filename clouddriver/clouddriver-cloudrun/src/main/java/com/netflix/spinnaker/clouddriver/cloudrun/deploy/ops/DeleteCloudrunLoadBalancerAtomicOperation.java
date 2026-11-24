/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeleteCloudrunLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteCloudrunLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final DeleteCloudrunLoadBalancerDescription description;
  CloudrunServiceClient serviceClient;

  @Autowired(required = false)
  CloudrunConfigurationProperties configurationProperties;

  @Autowired CloudrunLoadBalancerProvider provider;

  public DeleteCloudrunLoadBalancerAtomicOperation(
      DeleteCloudrunLoadBalancerDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing deletion of load balancer " + description.getLoadBalancerName() + "...");

    String loadBalancerName = description.getLoadBalancerName();
    CloudrunLoadBalancer loadBalancer =
        provider.getLoadBalancer(description.getAccountName(), loadBalancerName);
    if (loadBalancer == null) {
      throw new CloudrunOperationException(
          "Failed to get load balancer by account "
              + description.getAccountName()
              + " and load balancer name : "
              + loadBalancerName);
    } else {
      String region = loadBalancer.getRegion();
      try {
        if (serviceClient == null) {
          serviceClient =
              CloudrunServiceClient.forAccount(
                  description.getCredentials(), configurationProperties);
        }
        serviceClient.deleteService(region, loadBalancerName);
      } catch (Exception e) {
        throw new CloudrunOperationException(
            "Failed to delete load balancer " + loadBalancerName + ": " + e.getMessage(), e);
      }
      getTask()
          .updateStatus(BASE_PHASE, "Successfully deleted load balancer " + loadBalancerName + ".");
      return null;
    }
  }
}
