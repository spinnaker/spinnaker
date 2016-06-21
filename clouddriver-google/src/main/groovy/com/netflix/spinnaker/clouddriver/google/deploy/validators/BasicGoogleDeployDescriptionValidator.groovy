/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@GoogleOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicGoogleDeployDescriptionValidator")
class BasicGoogleDeployDescriptionValidator extends DescriptionValidator<BasicGoogleDeployDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Override
  void validate(List priorDescriptions, BasicGoogleDeployDescription description, Errors errors) {
    def helper = new StandardGceAttributeValidator("basicGoogleDeployDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateImage(description.image)
    helper.validateInstanceType(description.instanceType,
                                description.regional ? description.region : description.zone)
    helper.validateInstanceTypeDisks(googleDeployDefaults.determineInstanceTypeDisk(description.instanceType),
                                     description.disks)
    helper.validateAuthScopes(description.authScopes)

    if (description.regional) {
      helper.validateRegion(description.region)
    } else {
      helper.validateZone(description.zone)
    }

    helper.validateName(description.application, "application")
    helper.validateNotEmpty(description.targetSize, "targetSize")
    helper.validateNonNegativeLong(description.targetSize ?: 0, "targetSize")

    description.autoscalingPolicy?.with {
      helper.validateNonNegativeLong(minNumReplicas, "autoscalingPolicy.minNumReplicas")
      helper.validateNonNegativeLong(maxNumReplicas, "autoscalingPolicy.maxNumReplicas")
      helper.validateNonNegativeLong(coolDownPeriodSec, "autoscalingPolicy.coolDownPeriodSec")
      helper.validateMaxNotLessThanMin(minNumReplicas,
                                       maxNumReplicas,
                                       "autoscalingPolicy.minNumReplicas",
                                       "autoscalingPolicy.maxNumReplicas")
    }
  }
}
