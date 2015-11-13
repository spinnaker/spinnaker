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

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.aws.deploy.description.AllowLaunchDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("allowLaunchDescriptionValidator")
class AllowLaunchDescriptionValidator extends DescriptionValidator<AllowLaunchDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, AllowLaunchDescription description, Errors errors) {
    if (!description.amiName) {
      errors.rejectValue("amiName", "allowLaunchDescription.amiName.empty")
    }
    if (!description.region) {
      errors.rejectValue("region", "allowLaunchDescription.region.empty")
    }
    if (!description.account) {
      errors.rejectValue("account", "allowLaunchDescription.account.empty")
    } else if (!accountCredentialsProvider.all.collect { it.name }.contains(description.account)) {
      errors.rejectValue("account", "allowLaunchDescription.account.not.configured")
    }
  }
}
