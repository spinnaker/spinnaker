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

package com.netflix.asgard.kato.deploy.aws.converters

import com.netflix.asgard.kato.deploy.DeployAtomicOperation
import com.netflix.asgard.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.asgard.kato.orchestration.AtomicOperation
import com.netflix.asgard.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("basicAmazonDeployDescription")
class BasicAmazonDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicAmazonDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new BasicAmazonDeployDescription(input)
  }
}
