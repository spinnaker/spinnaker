/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.bluespar.kato.security.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateImageResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.bluespar.kato.security.aws.AmazonClientProvider
import com.netflix.bluespar.kato.security.aws.AmazonCredentials
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class AmazonClientProviderSpec extends Specification {

  def "amazonec2 uses edda for reads and aws for writes"() {
    setup:
    def mockCreateImage = Mock(CreateImageResult)
    AmazonEC2.metaClass.createImage = {
      mockCreateImage
    }
    def restTemplate = Mock(RestTemplate)
    def objectMapper = Mock(ObjectMapper)
    def mockCredents = Mock(AWSCredentials)
    def provider = new AmazonClientProvider(edda: "http://edda.%s.%s.localhost.localdomain", restTemplate: restTemplate, objectMapper: objectMapper)
    def ec2 = provider.getAmazonEC2(new AmazonCredentials(mockCredents, "test"), "us-west-1")

    when:
    ec2.describeSubnets()

    then:
    1 * restTemplate.getForObject("http://edda.us-west-1.test.localhost.localdomain/REST/v2/aws/subnets;_expand", String) >> { "ok" }
    1 * objectMapper.readValue("ok", _)

    when:
    def response = ec2.createImage()

    then:
    response.is mockCreateImage
  }
}
