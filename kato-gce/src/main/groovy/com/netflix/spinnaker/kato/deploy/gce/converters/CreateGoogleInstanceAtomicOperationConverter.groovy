/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.deploy.gce.converters

import com.netflix.spinnaker.kato.deploy.gce.ops.CreateGoogleInstanceAtomicOperation
import com.netflix.spinnaker.kato.deploy.gce.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("createGoogleInstanceDescription")
class CreateGoogleInstanceAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new CreateGoogleInstanceAtomicOperation(convertDescription(input))
  }

  CreateGoogleInstanceDescription convertDescription(Map input) {
    input.accountName = input.credentials

    if (input.accountName) {
      // The value returned by getCredentialsObject() is of type GoogleNamedAccountCredentials. The credentials property
      // of BasicGoogleDeployDescription, one of which we are about to construct below, is of type GoogleCredentials.
      // Since GoogleNamedAccountCredentials exposes a property named 'credentials', of the desired type
      // GoogleCredentials, we just need to dereference it. If we don't, GroovyCastExceptions ensue.
      input.credentials = getCredentialsObject(input.accountName as String)?.getCredentials()
    }

    new CreateGoogleInstanceDescription(input)
  }
}
