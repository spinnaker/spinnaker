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

package com.netflix.spinnaker.clouddriver.azure.common

import com.microsoft.azure.management.resources.ResourceManagementClient
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureUtilities {

  static final String PATH_SEPARATOR = "/"
  static final String NAME_SEPARATOR = "-"
  static final String VNET_NAME_PREFIX = "vnet-"
  static final String PUBLICIP_NAME_PREFIX = "pip-"
  static final String LBFRONTEND_NAME_PREFIX = "fe-"
  static final String DNS_NAME_PREFIX = "dns-"
  static final String IPCONFIG_NAME_PREFIX = "ipc-"

  static String getResourceNameFromID(String resourceId) {
    int idx = resourceId.lastIndexOf(PATH_SEPARATOR)
    if (idx > 0) {
      return resourceId.substring(idx + 1)
    }
    resourceId
  }

  static String getResourceGroupName(AzureResourceOpsDescription description) {
    description.appName + NAME_SEPARATOR + description.region
  }

  static String getResourceGroupName(String appName, String region) {
    appName + NAME_SEPARATOR + region.replace(' ', '').toLowerCase()
  }

  static String getResourceGroupLocation(AzureResourceOpsDescription description) {
    def resourceGroupName = getResourceGroupName(description)

    description.credentials.getResourceManagerClient().getResourceGroupLocation(resourceGroupName, description.getCredentials())
  }

  static String getResourceGroupNameFromResourceId(String resourceId) {
    def parts = resourceId.split(PATH_SEPARATOR)
    def idx = parts.findIndexOf {it == "resourceGroups"}
    def resourceGroupName = "unknown"

    if (idx > 0) {
      resourceGroupName = parts[idx + 1]
    }

    resourceGroupName
  }

  static String getAppNameFromResourceId(String resourceId) {
    getResourceGroupNameFromResourceId(resourceId).split(NAME_SEPARATOR).first()
  }

  static String getLocationFromResourceId(String resourceId) {
    getResourceGroupNameFromResourceId(resourceId).split(NAME_SEPARATOR).last()
  }

  static String getNameFromResourceId(String resourceId) {
    resourceId.split(PATH_SEPARATOR).last()
  }
}
