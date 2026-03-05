package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.ops.description

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class UpsertAzureImageTagsDescription extends AzureResourceOpsDescription {
  String imageName
  String imageId
  String resourceGroupName
  Collection<String> regions
  Map<String, String> tags
  boolean isCustomImage = false
  boolean isGalleryImage = false
}
