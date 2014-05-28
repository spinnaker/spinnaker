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

package com.netflix.spinnaker.kato.deploy.aws.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerAtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component("createAmazonLoadBalancerDescription")
class CreateAmazonLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Autowired
  ObjectMapper objectMapper

  @PostConstruct
  void init() {
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  @Override
  CreateAmazonLoadBalancerAtomicOperation convertOperation(Map input) {
    new CreateAmazonLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  CreateAmazonLoadBalancerDescription convertDescription(Map input) {
    def json = objectMapper.writeValueAsString(input)
    def description = objectMapper.readValue(json, CreateAmazonLoadBalancerDescription)
    description.credentials = (AmazonCredentials) getCredentialsObject(input.credentials as String)
    description
  }
}
