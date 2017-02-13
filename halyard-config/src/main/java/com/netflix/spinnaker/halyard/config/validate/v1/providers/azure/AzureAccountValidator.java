/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.azure;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.resources.ResourcesOperations;
import com.netflix.spinnaker.clouddriver.azure.client.AzureBaseClient;
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.stereotype.Component;

@Component
public class AzureAccountValidator extends Validator<AzureAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder p, AzureAccount n) {
    String clientId = n.getClientId();
    String appKey = n.getAppKey();
    String tenantId = n.getTenantId();
    String subscriptionId = n.getSubscriptionId();
    String defaultResourceGroup = n.getDefaultResourceGroup();
    String defaultKeyVault = n.getDefaultKeyVault();

    ApplicationTokenCredentials credentials = AzureBaseClient.getTokenCredentials(clientId, tenantId, appKey);
    try {
      credentials.getToken();
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      if (errorMessage.contains("AADSTS90002")) {
        p.addProblem(Severity.ERROR, "Tenant Id '" + tenantId + "' is invalid.", "tenantId")
          .setRemediation("Follow instructions here https://aka.ms/azspinconfig to retrieve the tenantId for your subscription.");
      } else if (errorMessage.contains("AADSTS70001")) {
        p.addProblem(Severity.ERROR, "Client Id '" + clientId + "' is invalid for tenant '" + tenantId + "'.", "clientId")
          .setRemediation("Follow instructions here https://aka.ms/azspinconfig to create a service principal and retrieve the clientId.");
      } else if (errorMessage.contains("AADSTS70002")) {
        p.addProblem(Severity.ERROR, "AppKey is invalid.", "appKey.")
          .setRemediation("Follow instructions here https://aka.ms/azspinconfig to specify an appKey when creating a service principal.");
      } else {
        p.addProblem(Severity.ERROR, "Error instantiating Azure credentials: " + e.getMessage() + ".")
          .setRemediation("Follow instructions here https://aka.ms/azspinconfig to setup a service principal.");
      }
      return;
    }

    ResourcesOperations resourceOperations = new AzureResourceManagerClient(subscriptionId, credentials).getResourceOperations();
    try {
      resourceOperations.get(
        defaultResourceGroup,
        "",
        "",
        "Microsoft.KeyVault/vaults",
        defaultKeyVault,
        "2015-06-01").getBody();
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "The KeyVault '" + defaultKeyVault +
        "' does not exist in the Resource Group '" + defaultResourceGroup +
        "' for the Subscription '" + subscriptionId + "'.")
        .setRemediation("Follow instructions here https://aka.ms/azspinconfig to setup a default Resource Group and KeyVault.");
      return;
    }
  }
}