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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.azure;

class AzureCommandProperties {
  static final String CLIENT_ID_DESCRIPTION = "The clientId (also called appId) of your service principal.";

  static final String APP_KEY_DESCRIPTION = "The appKey (password) of your service principal.";

  static final String TENANT_ID_DESCRIPTION = "The tenantId that your service principal is assigned to.";

  static final String SUBSCRIPTION_ID_DESCRIPTION = "The subscriptionId that your service principal is assigned to.";

  static final String OBJECT_ID_DESCRIPTION = "The objectId of your service principal. This is only required if using Packer to bake Windows images.";

  static final String DEFAULT_RESOURCE_GROUP_DESCRIPTION = "The default resource group to contain any non-application specific resources.";

  static final String DEFAULT_KEY_VAULT_DESCRIPTION = "The name of a KeyVault that contains the default user name and password used to create VMs";
}
