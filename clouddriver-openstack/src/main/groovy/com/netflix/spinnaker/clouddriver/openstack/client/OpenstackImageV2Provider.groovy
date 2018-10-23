/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
