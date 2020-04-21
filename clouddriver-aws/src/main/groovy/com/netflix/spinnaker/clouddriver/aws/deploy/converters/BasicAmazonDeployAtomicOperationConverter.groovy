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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicAmazonDeployDescription")
class BasicAmazonDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicAmazonDeployDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, BasicAmazonDeployDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)

    if (converted.securityGroups != null && !converted.securityGroups.isEmpty()) {
      for (Map.Entry<String, List<String>> entry : converted.availabilityZones) {
        String region = entry.key

        RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
          regionScopedProviderFactory.forRegion(converted.credentials, region)

        SecurityGroupService securityGroupService = regionScopedProvider.getSecurityGroupService()

        converted.securityGroupNames.addAll(
          securityGroupService.resolveSecurityGroupNamesByStrategy(converted.securityGroups) { List<String> ids ->
            securityGroupService.getSecurityGroupNamesFromIds(ids)
          }
        )
      }
    }

    return converted
  }
}
