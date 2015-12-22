/*
 * Copyright 2015 Pivotal Inc.
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
package com.netflix.spinnaker.clouddriver.cf.deploy.converters

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import org.springframework.stereotype.Component

/**
 * Converter for a Cloud Foundry deploy operation
 *
 *
 */
@CloudFoundryOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("cloudFoundryDeployDescription")
class CloudFoundryDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  @Override
  CloudFoundryDeployDescription convertDescription(Map input) {
    if (input.containsKey('loadBalancers') && input.loadBalancers instanceof List) {
      input.loadBalancers = input.loadBalancers.join(',')
    }

    def converted = objectMapper.convertValue(input, CloudFoundryDeployDescription)

    converted.credentials = getCredentialsObject(input.credentials as String)

    /**
     * Convert supported template options, e.g. s3://example.com/{{job}}-{{buildNumber}}/
     */
    def options = ['job', 'buildNumber']

    options.each { option ->
      if (converted?.trigger?."$option" != null) {
        converted.repository = converted.repository.replace("{{${option}}}", converted?.trigger?."$option" as String)
      }
    }

    converted
  }
}
