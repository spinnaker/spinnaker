package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import org.openstack4j.model.image.v2.Image

class OpenstackImageV2Provider implements OpenstackImageProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  OpenstackIdentityProvider identityProvider;

  OpenstackImageV2Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider;
  }

  @Override
  List<OpenstackImage> listImages(String region, Map<String, String> filters) {
    handleRequest {
      getRegionClient(region).imagesV2().list(filters)?.collect { buildImage(it, region) }
    }
  }

  @Override
  List<OpenstackImage> listImages(String region) {
    handleRequest {
      getRegionClient(region).imagesV2().list()?.collect { buildImage(it, region) }
    }
  }

  static OpenstackImage buildImage(Image image, String region) {
    def properties = new HashMap()
    image.properties.each { properties[it.key] = it.value.toString() }
    OpenstackImage.builder()
      .id(image.id)
      .status(image.status?.value())
      .size(image.size)
      .location(image.directUrl)
      .createdAt(image.createdAt?.time)
      .updatedAt(image.updatedAt?.time)
      .properties(properties)
      .name(image.name)
      .region(region)
      .build()
  }
}
