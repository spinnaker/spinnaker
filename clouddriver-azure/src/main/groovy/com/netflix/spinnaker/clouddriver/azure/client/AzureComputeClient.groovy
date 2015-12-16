/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.client

import com.microsoft.azure.management.compute.ComputeManagementClient
import com.microsoft.azure.management.compute.ComputeManagementService
import com.microsoft.azure.management.compute.models.ImageReference
import com.microsoft.azure.utility.ComputeHelper
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic

import javax.imageio.ImageReader

@CompileStatic
public class AzureComputeClient extends AzureBaseClient {
  AzureComputeClient(String subscriptionId) {
    super(subscriptionId)
  }

  protected ComputeManagementClient getComputeClient(AzureCredentials creds) {
    ComputeManagementService.create(this.buildConfiguration(creds))
  }
}
