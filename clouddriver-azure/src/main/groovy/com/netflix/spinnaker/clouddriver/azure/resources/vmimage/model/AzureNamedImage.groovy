package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model

class AzureNamedImage {
  String imageName
  Boolean isCustom = false
  String publisher
  String offer
  String sku
  String version
  String account
  String region
  String uri
  String ostype
}
