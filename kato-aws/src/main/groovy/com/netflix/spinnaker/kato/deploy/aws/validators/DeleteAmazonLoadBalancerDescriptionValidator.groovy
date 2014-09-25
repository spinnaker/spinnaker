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

package com.netflix.spinnaker.kato.deploy.aws.validators

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.deploy.aws.description.DeleteAmazonLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("deleteAmazonLoadBalancerDescriptionValidator")
class DeleteAmazonLoadBalancerDescriptionValidator extends AmazonDescriptionValidationSupport<DeleteAmazonLoadBalancerDescription> {

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  void validate(List priorDescriptions, DeleteAmazonLoadBalancerDescription description, Errors errors) {
    validateRegions(description.regions, "deleteAmazonLoadBalancerDescription", errors)
    for (region in description.regions) {
      validateLoadBalancer region, description.loadBalancerName, description.credentials, errors
    }
  }

  void validateLoadBalancer(String region, String name, NetflixAmazonCredentials credentials, Errors errors) {
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(credentials, region)
    def descriptions = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: [name])).loadBalancerDescriptions
    if (!descriptions) {
      errors.rejectValue "name", "amazonLoadBalancerDescription.name.not.found", [region] as Object[], "Amazon Load Balancer ${name} not found in ${region} for ${credentials.name}"
    }
  }
}
