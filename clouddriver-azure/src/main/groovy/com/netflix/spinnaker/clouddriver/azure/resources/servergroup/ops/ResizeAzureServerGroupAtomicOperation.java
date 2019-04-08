/*
 * Copyright 2019 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities;
import com.netflix.spinnaker.clouddriver.azure.resources.cluster.view.AzureClusterProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription;
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.ResizeAzureServerGroupDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class ResizeAzureServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP";
  private final ResizeAzureServerGroupDescription description;
  @Autowired
  private AzureClusterProvider azureClusterProvider;

  public AzureClusterProvider getAzureClusterProvider() {
    return azureClusterProvider;
  }

  public void setAzureClusterProvider(AzureClusterProvider azureClusterProvider) {
    this.azureClusterProvider = azureClusterProvider;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public ResizeAzureServerGroupAtomicOperation(ResizeAzureServerGroupDescription description) {
    this.description = description;
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-dev-v000", "targetSize": 2, "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/azure/ops
   */
  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Resize Azure Server Group Operation...");

    final String region = description.getRegion();
    if (StringGroovyMethods.asBoolean(description.getServerGroupName()))
      description.setName(description.getServerGroupName());
    if (!StringGroovyMethods.asBoolean(description.getApplication())) {
      final String name = description.getAppName();
      description.setApplication(StringGroovyMethods.asBoolean(name) ? name : Names.parseName(description.getName()).getApp());
    }

    final int targetSize = description.getTargetSize() instanceof Number ? description.getTargetSize() : description.getCapacity().getDesired();
    getTask().updateStatus(BASE_PHASE, "Resizing server group " + description.getName() + " in " + region + " to target size " + String.valueOf(targetSize) + "...");

    if (!DefaultGroovyMethods.asBoolean(description.getCredentials())) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.");
    }


    ArrayList<String> errList = new ArrayList<String>();

    try {
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.getApplication(), region);
      AzureServerGroupDescription serverGroupDescription = description.getCredentials().getComputeClient().getServerGroup(resourceGroupName, description.getName());

      if (!DefaultGroovyMethods.asBoolean(serverGroupDescription)) {
        getTask().updateStatus(BASE_PHASE, "Resize Server Group Operation failed: could not find server group " + description.getName() + " in " + region);
        errList.add("could not find server group " + description.getName() + " in " + region);
      } else {
        try {
          description.getCredentials().getComputeClient().resizeServerGroup(resourceGroupName, description.getName(), targetSize);
          getTask().updateStatus(BASE_PHASE, "Done resizing Azure server group " + description.getName() + " in " + region + ".");
        } catch (Exception e) {
          getTask().updateStatus(BASE_PHASE, "Resizing server group " + description.getName() + " failed: " + e.getMessage());
          errList.add("Failed to resize server group " + description.getName() + ": " + e.getMessage());
        }
      }
    } catch (Exception e) {
      getTask().updateStatus(BASE_PHASE, "Resizing server group " + description.getName() + " failed: " + e.getMessage());
      errList.add("Failed to resize server group " + description.getName() + ": " + e.getMessage());
    }

    if (errList.isEmpty()) {
      getTask().updateStatus(BASE_PHASE, "Resize Azure Server Group Operation for " + description.getName() + " succeeded.");
    } else {
      errList.add(" Go to Azure Portal for more info");
      throw new AtomicOperationException("Failed to resize " + description.getName(), errList);
    }

    return null;
  }
}
