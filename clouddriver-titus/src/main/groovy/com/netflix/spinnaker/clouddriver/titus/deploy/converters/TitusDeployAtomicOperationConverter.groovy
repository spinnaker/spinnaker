/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@TitusOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
class TitusDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Autowired
  AwsLookupUtil awsLookupUtil

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  @Override
  TitusDeployDescription convertDescription(Map input) {
    // Backwards-compatibility for when the Titus provider blindly accepted any container
    // attribute value, when in reality this can only be string values. Now that the
    // description is Java, this can cause Jackson's object mapper to throw exceptions if
    // left unconverted.
    if (input.containerAttributes != null) {
      input.containerAttributes.forEach { k, v ->
        if (!(v instanceof String)) {
          input.containerAttributes.put(k, v.toString())
        }
      }
    }

    def converted = objectMapper.convertValue(input, TitusDeployDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)

    if (converted.securityGroups != null && !converted.securityGroups.isEmpty()) {
      converted.setSecurityGroupNames(
        awsLookupUtil.convertSecurityGroupsToNames(converted.account, converted.region, converted.securityGroups)
      )
    }

    converted
  }
}
