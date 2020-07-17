/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.validators.discovery

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.GoogleInstanceListDescription
import com.netflix.spinnaker.clouddriver.google.deploy.validators.StandardGceAttributeValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableInstancesInDiscoveryDescriptionValidator extends DescriptionValidator<GoogleInstanceListDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, GoogleInstanceListDescription description, ValidationErrors errors) {
    def helper = new StandardGceAttributeValidator("googleInstanceListDescription", errors)

    helper.validateNotEmpty(description.instanceIds, "instanceIds")
    helper.validateRegion(description.region, description.credentials)
  }
}
