/*
 * Copyright 2016 The original authors.
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

import com.microsoft.azure.management.compute.VirtualMachineImage;
import com.microsoft.azure.management.resources.Deployment;
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities;
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation;
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.KeyVaultSecret;
import com.netflix.spinnaker.clouddriver.azure.resources.network.view.AzureNetworkProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription;
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

class CreateAzureServerGroupWithAzureLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "CREATE_SERVER_GROUP";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final AzureServerGroupDescription description;

  @Autowired AzureNetworkProvider networkProvider;

  CreateAzureServerGroupWithAzureLoadBalancerAtomicOperation(
      AzureServerGroupDescription description) {
    this.description = description;
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d
   * '[{"createServerGroup":{"name":"taz-st1-d1","cloudProvider":"azure","application":"taz","stack":"st1","detail":"d1","vnet":"vnet-select","subnet":"subnet1","account":"azure-cred1","selectedProvider":"azure","capacity":{"useSourceCapacity":false,"min":1,"max":1},"credentials":"azure-cred1","region":"westus","loadBalancerName":"taz-ag1-d1","securityGroupName":"taz-secg1","user":"[anonymous]","upgradePolicy":"Manual","image":{"account":"azure-cred1","imageName":"UbuntuServer-14.04.3-LTS(Recommended)","isCustom":false,"offer":"UbuntuServer","ostype":null,"publisher":"Canonical","region":null,"sku":"14.04.3-LTS","uri":null,"version":"14.04.201602171"},"sku":{"name":"Standard_DS1_v2","tier":"Standard","capacity":1},"osConfig":{},"type":"createServerGroup"}}]'
   * localhost:7002/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  public Map operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Initializing deployment of server group with Azure Load Balancer %s in %s",
                description.getName(), description.getRegion()));

    List<String> errList = new ArrayList<>();
    String resourceGroupName = null;
    String virtualNetworkName = null;
    String subnetName = null;
    String serverGroupName = null;
    String loadBalancerPoolID = null;
    String inboundNatPoolID = null;

    try {

      getTask().updateStatus(BASE_PHASE, "Beginning server group deployment");

      // if this is not a custom image, then we need to go get the OsType from Azure
      if (!description.getImage().getIsCustom()) {
        VirtualMachineImage virtualMachineImage =
            description
                .getCredentials()
                .getComputeClient()
                .getVMImage(
                    description.getRegion(),
                    description.getImage().getPublisher(),
                    description.getImage().getOffer(),
                    description.getImage().getSku(),
                    description.getImage().getVersion());

        if (virtualMachineImage != null) {
          throw new RuntimeException(
              String.format(
                  "Invalid published image was selected; %s:%s:%s:%s does not exist",
                  description.getImage().getPublisher(),
                  description.getImage().getOffer(),
                  description.getImage().getSku(),
                  description.getImage().getVersion()));
        }

        if (description.getImage().getImageName() == null) {
          description.getImage().setImageName(virtualMachineImage.inner().name());
        }
        if (description.getImage().getOstype() == null) {
          description
              .getImage()
              .setOstype(virtualMachineImage.osDiskImage().operatingSystem().name());
        }
      }

      resourceGroupName =
          AzureUtilities.getResourceGroupName(
              description.getApplication(), description.getRegion());

      String loadBalancerName = description.getLoadBalancerName();

      virtualNetworkName = description.getVnet();
      subnetName = description.getSubnet();

      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "Using virtual network %s and subnet %s for server group %s",
                  virtualNetworkName, subnetName, description.getName()));

      // we will try to associate the server group with the selected virtual network and subnet
      description.setHasNewSubnet(false);

      AzureServerGroupNameResolver nameResolver =
          new AzureServerGroupNameResolver(
              description.getAccountName(), description.getRegion(), description.getCredentials());
      description.setName(
          nameResolver.resolveNextServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getDetail(),
              false));
      description.setClusterName(description.getClusterName());
      description.setAppName(description.getApplication());

      // Verify that it can be used for this server group/cluster. create a backend address pool
      // entry if it doesn't already exist
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "Create new backend address pool in Load Balancer: %s", loadBalancerName));
      loadBalancerPoolID =
          description
              .getCredentials()
              .getNetworkClient()
              .createLoadBalancerAPforServerGroup(
                  resourceGroupName, description.getLoadBalancerName(), description.getName());

      if (loadBalancerPoolID == null) {
        throw new RuntimeException(
            String.format(
                "Selected Load Balancer %s does not exist", description.getLoadBalancerName()));
      }

      // Create new inbound NAT pool for the server group
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format("Create new inbound NAT pool in Load Balancer: %s", loadBalancerName));
      inboundNatPoolID =
          description
              .getCredentials()
              .getNetworkClient()
              .createLoadBalancerNatPoolPortRangeforServerGroup(
                  resourceGroupName, description.getLoadBalancerName(), description.getName());

      if (inboundNatPoolID == null) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                String.format(
                    "Failed to create new inbound NAT pool in Load Balancer: %s, the task will continue",
                    loadBalancerName));
      }

      Map<String, Object> templateParameters = new HashMap<>();

      templateParameters.put(
          AzureServerGroupResourceTemplate.getAppGatewayAddressPoolParameterName(),
          loadBalancerPoolID);
      templateParameters.put(
          AzureServerGroupResourceTemplate.getVmUserNameParameterName(),
          new KeyVaultSecret(
              "VMUsername",
              description.getCredentials().getSubscriptionId(),
              description.getCredentials().getDefaultResourceGroup(),
              description.getCredentials().getDefaultKeyVault()));

      if (description.getCredentials().getUseSshPublicKey()) {
        templateParameters.put(
            AzureServerGroupResourceTemplate.getVmSshPublicKeyParameterName(),
            new KeyVaultSecret(
                "VMSshPublicKey",
                description.getCredentials().getSubscriptionId(),
                description.getCredentials().getDefaultResourceGroup(),
                description.getCredentials().getDefaultKeyVault()));
      } else {
        templateParameters.put(
            AzureServerGroupResourceTemplate.getVmPasswordParameterName(),
            new KeyVaultSecret(
                "VMPassword",
                description.getCredentials().getSubscriptionId(),
                description.getCredentials().getDefaultResourceGroup(),
                description.getCredentials().getDefaultKeyVault()));
      }

      templateParameters.put(
          AzureServerGroupResourceTemplate.getLoadBalancerAddressPoolParameterName(),
          loadBalancerPoolID);
      templateParameters.put(
          AzureServerGroupResourceTemplate.getLoadBalancerNatPoolParameterName(), inboundNatPoolID);

      // The empty "" cannot be assigned to the custom data otherwise Azure service will run into
      // error complaining "custom data must be in Base64".
      // So once there is no custom data, remove this template section rather than assigning a "".
      if (description.getOsConfig().getCustomData() != null
          && description.getOsConfig().getCustomData().length() > 0) {
        templateParameters.put(
            AzureServerGroupResourceTemplate.getCustomDataParameterName(),
            description.getOsConfig().getCustomData());
      }

      if (errList.isEmpty()) {
        getTask().updateStatus(BASE_PHASE, "Deploying server group");
        Deployment deployment =
            description
                .getCredentials()
                .getResourceManagerClient()
                .createResourceFromTemplate(
                    AzureServerGroupResourceTemplate.getTemplate(description),
                    resourceGroupName,
                    description.getRegion(),
                    description.getName(),
                    "serverGroup",
                    templateParameters);

        errList.addAll(
            AzureDeploymentOperation.checkDeploymentOperationStatus(
                getTask(),
                BASE_PHASE,
                description.getCredentials(),
                resourceGroupName,
                deployment.name()));
        serverGroupName = errList.isEmpty() ? description.getName() : null;
      }
    } catch (Exception e) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "Unexpected exception: Deployment of server group %s failed: %s",
                  description.getName(), e.getMessage()));
      errList.add(e.getMessage());
    }
    if (errList.isEmpty()) {
      if (description
          .getCredentials()
          .getNetworkClient()
          .isServerGroupDisabled(
              resourceGroupName, description.getLoadBalancerName(), description.getName())) {
        description
            .getCredentials()
            .getNetworkClient()
            .enableServerGroupWithLoadBalancer(
                resourceGroupName, description.getLoadBalancerName(), description.getName());
        getTask()
            .updateStatus(
                BASE_PHASE,
                String.format(
                    "Done enabling Azure server group %s in %s.",
                    description.getName(), description.getRegion()));

      } else {
        getTask()
            .updateStatus(
                BASE_PHASE,
                String.format(
                    "Azure server group %s in %s is already enabled.",
                    description.getName(), description.getRegion()));
      }

      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "Deployment for server group %s in %s has succeeded.",
                  description.getName(), description.getRegion()));
    } else {
      // cleanup any resources that might have been created prior to server group failing to deploy
      getTask()
          .updateStatus(BASE_PHASE, "Cleanup any resources created as part of server group upsert");
      try {
        if (serverGroupName != null && serverGroupName.length() > 0) {
          AzureServerGroupDescription sgDescription =
              description
                  .getCredentials()
                  .getComputeClient()
                  .getServerGroup(resourceGroupName, serverGroupName);
          if (sgDescription != null) {
            description
                .getCredentials()
                .getComputeClient()
                .destroyServerGroup(resourceGroupName, serverGroupName);
          }
        }
      } catch (Exception e) {
        String errMessage =
            String.format(
                "Unexpected exception: %s! Please log in into Azure Portal and manually delete any resource associated with the %s server group such as storage accounts, internal load balancer, public IP and subnets",
                e.getMessage(), description.getName());
        getTask().updateStatus(BASE_PHASE, errMessage);
        errList.add(errMessage);
      }

      try {
        if (loadBalancerPoolID != null) {
          description
              .getCredentials()
              .getNetworkClient()
              .removeLoadBalancerAPforServerGroup(
                  resourceGroupName, description.getLoadBalancerName(), description.getName());
        }
      } catch (Exception e) {
        String errMessage =
            String.format(
                "Unexpected exception: %s! Load balancer backend address pool entry %s associated with the %s server group could not be deleted",
                e.getMessage(), loadBalancerPoolID, description.getName());
        getTask().updateStatus(BASE_PHASE, errMessage);
        errList.add(errMessage);
      }

      try {
        if (inboundNatPoolID != null) {
          description
              .getCredentials()
              .getNetworkClient()
              .removeLoadBalancerNatPoolPortRangeforServerGroup(
                  resourceGroupName, description.getLoadBalancerName(), description.getName());
        }
      } catch (Exception e) {
        String errMessage =
            String.format(
                "Unexpected exception: %s! Load balancer inbound nat pool entry %s associated with the %s server group could not be deleted",
                e.getMessage(), inboundNatPoolID, description.getName());
        getTask().updateStatus(BASE_PHASE, errMessage);
        errList.add(errMessage);
      }

      throw new AtomicOperationException(
          String.format("%s deployment failed", description.getName()), errList);
    }

    LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>(2);
    LinkedHashMap<String, LinkedHashMap<String, String>> map1 =
        new LinkedHashMap<String, LinkedHashMap<String, String>>(1);
    LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(1);
    map2.put("name", description.getName());
    map1.put(description.getRegion(), map2);
    map.put("serverGroups", map1);
    map.put(
        "serverGroupNames",
        new ArrayList<String>(
            Arrays.asList(description.getRegion() + ":" + description.getName().toString())));
    return map;
  }
}
