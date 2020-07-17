/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.StartStopAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired


abstract class AbstractStartStopAppengineDescriptionValidator extends DescriptionValidator<StartStopAppengineDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AppengineClusterProvider appengineClusterProvider

  abstract String getDescriptionName()

  @Override
  void validate(List priorDescriptions, StartStopAppengineDescription description, ValidationErrors errors) {
    def helper = new StandardAppengineAttributeValidator(descriptionName, errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    def nameNotEmpty = helper.validateNotEmpty(description.serverGroupName, "serverGroupName")

    if (nameNotEmpty) {
      helper.validateServingStatusCanBeChanged(description.serverGroupName,
                                               description.credentials,
                                               appengineClusterProvider,
                                               "serverGroupName")
    }
  }
}
