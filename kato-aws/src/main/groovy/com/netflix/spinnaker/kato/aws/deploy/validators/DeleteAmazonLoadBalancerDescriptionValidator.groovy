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

package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("deleteAmazonLoadBalancerDescriptionValidator")
class DeleteAmazonLoadBalancerDescriptionValidator extends AmazonDescriptionValidationSupport<DeleteAmazonLoadBalancerDescription> {

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  void validate(List priorDescriptions, DeleteAmazonLoadBalancerDescription description, Errors errors) {
    validateRegions(description, description.regions, "deleteAmazonLoadBalancerDescription", errors)
    if (!description.loadBalancerName) {
      errors.rejectValue "loadBalancerName", "deleteAmazonLoadBalancerDescription.loadBalancerName.empty"
    }
  }

}
