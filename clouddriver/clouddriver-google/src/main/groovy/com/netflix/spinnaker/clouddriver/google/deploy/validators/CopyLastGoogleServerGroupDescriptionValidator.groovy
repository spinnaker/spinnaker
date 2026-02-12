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
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component("copyLastGoogleServerGroupDescriptionValidator")
class CopyLastGoogleServerGroupDescriptionValidator extends DescriptionValidator<BasicGoogleDeployDescription> {
  @Autowired
  CredentialsRepository<GoogleNamedAccountCredentials> credentialsRepository

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired(required = false)
  GoogleClusterProvider googleClusterProvider

  @Override
  void validate(List priorDescriptions, BasicGoogleDeployDescription description, ValidationErrors errors) {
    // Passing 'copyLastGoogleServerGroupDescription' rather than 'basicGoogleDeployDescription'
    // here is a judgement call. The intent is to provide the context in which the validation
    // is performed rather than the actual type name being validated. The string is lower-cased
    // so isnt the literal typename anyway.
    def helper = new StandardGceAttributeValidator("copyLastGoogleServerGroupDescription", errors)

    helper.validateCredentials(description.accountName, credentialsRepository)
    helper.validateInstanceTypeDisks(googleDeployDefaults.determineInstanceTypeDisk(description.instanceType),
                                     description.disks)
    helper.validateAuthScopes(description.authScopes)

    if (description.instanceType) {
      helper.validateInstanceType(description.instanceType,
                                  description.regional ? description.region : description.zone,
                                  description.credentials)
    }

    if (description.minCpuPlatform) {
      helper.validateMinCpuPlatform(description.minCpuPlatform,
                                    description.regional ? description.region : description.zone,
                                    description.credentials)
    }

    // partnerMetadata is not supported under the stable v1 compute API.
    if (description.partnerMetadata) {
      errors.rejectValue("partnerMetadata",
        "copyLastGoogleServerGroupDescription.partnerMetadata.notSupportedInV1",
        "partnerMetadata is not supported under the stable v1 compute API and will not be propagated to GCE.")
    }

    def effectiveDescription = resolveEffectiveDescription(description)
    InstanceFlexibilityPolicyValidationSupport.rejectIssues(
      errors,
      "instanceFlexibilityPolicy",
      "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy",
      InstanceFlexibilityPolicyValidationSupport.validate(effectiveDescription))
  }

  private BasicGoogleDeployDescription resolveEffectiveDescription(BasicGoogleDeployDescription description) {
    if (!googleClusterProvider
      || !description?.accountName
      || !description?.source?.region
      || !description?.source?.serverGroupName) {
      return description
    }

    def ancestorServerGroup = googleClusterProvider.getServerGroup(
      description.accountName,
      description.source.region,
      description.source.serverGroupName)
    if (!ancestorServerGroup) {
      return description
    }

    BasicGoogleDeployDescription effectiveDescription = description.clone()
    effectiveDescription.regional =
      description.regional != null ? description.regional : ancestorServerGroup.regional
    effectiveDescription.distributionPolicy =
      description.distributionPolicy != null
        ? description.distributionPolicy
        : ancestorServerGroup.distributionPolicy
    effectiveDescription.instanceFlexibilityPolicy =
      description.instanceFlexibilityPolicy != null
        ? description.instanceFlexibilityPolicy
        : ancestorServerGroup.instanceFlexibilityPolicy

    return effectiveDescription
  }
}
