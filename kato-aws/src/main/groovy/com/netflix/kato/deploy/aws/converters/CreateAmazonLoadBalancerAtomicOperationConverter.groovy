package com.netflix.kato.deploy.aws.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerAtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.kato.security.aws.AmazonCredentials
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
    description.credentials = (AmazonCredentials)getCredentialsForEnvironment(input.credentials as String)
    description
  }
}
