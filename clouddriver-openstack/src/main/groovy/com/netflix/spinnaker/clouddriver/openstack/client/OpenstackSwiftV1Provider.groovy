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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import org.apache.http.protocol.HTTP
import org.openstack4j.core.transport.HttpResponse
import org.openstack4j.model.common.DLPayload
import org.springframework.http.HttpStatus

class OpenstackSwiftV1Provider implements OpenstackSwiftProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  OpenstackIdentityProvider identityProvider

  OpenstackSwiftV1Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  String readSwiftObject(String region, String container, String name) {
    handleRequest {
      DLPayload payload = getRegionClient(region).objectStorage().objects().download(container, name)
      HttpResponse response = payload?.httpResponse
      if (!response) {
        throw new OpenstackResourceNotFoundException("Unable to find Swift object ${container}/${name} in region ${region}")
      }

      // Testing against HTTP OK is a bit limited, but we want to actually read the response
      if (response.status != HttpStatus.OK.value()) {
        throw new OpenstackProviderException("Failed to read the Swift object ${container}/${name} in region ${region}; status=${response.status}")
      }

      // TODO consider checking content type before reading the response to ensure it is text
      return response.getEntity(String)
    }
  }
}
