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
  static final String CLIENT_ID_DESCRIPTION =
      "The clientId (also called appId) of your service principal.";

  static final String APP_KEY_DESCRIPTION = "The appKey (password) of your service principal.";

  static final String TENANT_ID_DESCRIPTION =
      "The tenantId that your service principal is assigned to.";

  static final String SUBSCRIPTION_ID_DESCRIPTION =
      "The subscriptionId that your service principal is assigned to.";

  static final String OBJECT_ID_DESCRIPTION =
      "The objectId of your service principal. This is only required if using Packer to bake Windows images.";

  static final String DEFAULT_RESOURCE_GROUP_DESCRIPTION =
      "The default resource group to contain any non-application specific resources.";

  static final String DEFAULT_KEY_VAULT_DESCRIPTION =
      "The name of a KeyVault that contains the user name, password, and ssh public key used to create VMs";

  static final String PACKER_RESOURCE_GROUP_DESCRIPTION =
      "The resource group to use if baking images with Packer.";

  static final String PACKER_STORAGE_ACCOUNT_DESCRIPTION =
      "The storage account to use if baking images with Packer.";

  static final String IMAGE_PUBLISHER_DESCRIPTION =
      "The Publisher name for your base image. See https://aka.ms/azspinimage to get a list of images.";

  static final String IMAGE_OFFER_DESCRIPTION =
      "The offer for your base image. See https://aka.ms/azspinimage to get a list of images.";

  static final String IMAGE_SKU_DESCRIPTION =
      "The SKU for your base image. See https://aka.ms/azspinimage to get a list of images.";

  static final String IMAGE_VERSION_DESCRIPTION =
      "The version of your base image. This defaults to 'latest' if not specified.";

  static final String REGIONS_DESCRIPTION = "The Azure regions this Spinnaker account will manage.";

  static final String USE_SSH_PUBLIC_KEY_DESCRIPTION =
      "Whether to use SSH public key to provision the linux vm. The default value is true which means using the ssh public key. Setting it to false means using the password instead.";
}
