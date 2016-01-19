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
import com.microsoft.azure.management.resources.ResourceManagementClient
import com.microsoft.azure.management.resources.ResourceManagementService
import com.microsoft.windowsazure.Configuration
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic

@CompileStatic
public class AzureBaseClient {
  final String subscriptionId

  protected AzureBaseClient(String subscriptionId) {
    this.subscriptionId = subscriptionId
  }

  protected Configuration buildConfiguration(AzureCredentials creds) throws Exception {
    try {
      String baseUri = creds.MANAGEMENT_URL
      return ManagementConfiguration.configure(
        null,
        new Configuration(),
        new URI(baseUri),
        this.subscriptionId,
        creds.getAccessToken())
    } catch (Exception e) {
      throw new RuntimeException("Failed to create Azure Resource Management Service", e)
    }
  }
}
