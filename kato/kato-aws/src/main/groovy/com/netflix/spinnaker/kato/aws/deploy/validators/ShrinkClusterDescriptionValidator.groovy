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

import com.netflix.spinnaker.kato.aws.deploy.description.ShrinkClusterDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("shrinkClusterDescriptionValidator")
class ShrinkClusterDescriptionValidator extends AmazonDescriptionValidationSupport<ShrinkClusterDescription> {
  @Override
  void validate(List priorDescriptions, ShrinkClusterDescription description, Errors errors) {
    if (!description.application) {
      errors.rejectValue("application", "shrinkClusterDescription.application.empty")
    }
    if (!description.clusterName) {
      errors.rejectValue("clusterName", "shrinkClusterDescription.clusterName.empty")
    }
    validateRegions description, errors
  }
}
