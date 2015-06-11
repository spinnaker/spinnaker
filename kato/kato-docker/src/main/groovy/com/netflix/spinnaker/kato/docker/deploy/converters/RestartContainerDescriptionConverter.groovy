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

package com.netflix.spinnaker.kato.docker.deploy.converters

import com.netflix.spinnaker.kato.docker.deploy.description.RestartContainerDescription
import com.netflix.spinnaker.kato.docker.deploy.op.RestartContainerAtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("restartContainerDescription")
class RestartContainerDescriptionConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  RestartContainerAtomicOperation convertOperation(Map input) {
    new RestartContainerAtomicOperation(convertDescription(input))
  }

  @Override
  RestartContainerDescription convertDescription(Map input) {
    input.credentials = getCredentialsObject(input.credentials as String)
    new RestartContainerDescription(input)
  }
}
