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

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.image.v2.ImageService
import org.openstack4j.model.image.v2.Image
import org.springframework.http.HttpStatus

class OpenstackImageV2ClientProviderSpec extends OpenstackClientProviderSpec {

  def "list images succeeds"() {
    setup:
    Map<String, String> filters = null
    ImageService imageService = Mock(ImageService)
    def imageLocation = "http://example.com/image.iso"
    Image image = Mock(Image) {
      getDirectUrl() >> imageLocation
    }

    when:
    List<OpenstackImage> result = provider.listImages(region, filters)

    then:
    1 * mockClient.imagesV2() >> imageService
    1 * imageService.list(filters) >> [image]

    and:
    result[0] instanceof OpenstackImage
    result[0].location == imageLocation
    noExceptionThrown()
  }

  def "list images exception"() {
    setup:
    Map<String, String> filters = null
    ImageService imageService = Mock(ImageService)
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listImages(region, filters)

    then:
    1 * mockClient.imagesV2() >> imageService
    1 * imageService.list(filters) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

}
