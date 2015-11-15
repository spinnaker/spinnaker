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

package com.netflix.spinnaker.clouddriver.azure.security;

import com.microsoft.azure.utility.AuthHelper
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient;
import com.netflix.spinnaker.clouddriver.azure.client.AzureNetworkClient;
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient;

class AzureCredentials {
  static final String MANAGEMENT_URL = "https://management.core.windows.net/"
  static final String AAD_URL = "https://login.windows.net/"

  final String tenantId
  final String clientId
  final String appKey
  final String project

  final AzureResourceManagerClient resourceManagerClient
  final AzureNetworkClient networkClient
  final AzureComputeClient computeClient

  AzureCredentials(String tenantId, String clientId, String appKey,
                   AzureResourceManagerClient resourceManagerClient,
                   AzureNetworkClient networkClient,
                   AzureComputeClient computeClient) {
    this.tenantId = tenantId
    this.clientId = clientId
    this.appKey = appKey
    this.project = "AzureProject"
    this.resourceManagerClient = resourceManagerClient
    this.networkClient = networkClient
    this.computeClient = computeClient

  }

  String getAccessToken() {
    try {
      return AuthHelper.getAccessTokenFromServicePrincipalCredentials(
        MANAGEMENT_URL,
        AAD_URL,
        this.tenantId,
        this.clientId,
        this.appKey
      ).accessToken
    } catch (Exception e) {
      throw new RuntimeException("Failed to create AccessTokenFromServicePrincipalCredentials", e)
    }
  }
}
