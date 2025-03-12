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
package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.deploy.description.CreateNetworkInterfaceDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import org.springframework.stereotype.Component

@Component("createNetworkInterfaceDescriptionValidator")
class CreateNetworkInterfaceDescriptionValidator extends AmazonDescriptionValidationSupport<CreateNetworkInterfaceDescription> {
  @Override
  void validate(List priorDescriptions, CreateNetworkInterfaceDescription description, ValidationErrors errors) {
    Set<String> regions = description.availabilityZonesGroupedByRegion?.keySet()
    if (!regions) {
      errors.rejectValue "regions", "createNetworkInterfaceDescription.regions.not.supplied"
    }
    if (!description.availabilityZonesGroupedByRegion?.values()?.flatten()) {
      errors.rejectValue "availabilityZones", "createNetworkInterfaceDescription.availabilityZones.not.supplied"
    }
    if (!description.subnetType) {
      errors.rejectValue "subnetType", "createNetworkInterfaceDescription.subnetType.empty"
    }
    validateRegions(description, regions, "createNetworkInterfaceDescription", errors)
  }
}
