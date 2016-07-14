/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.converters.SerializeApplicationAtomicOperationConverter

import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.converters.GoogleAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.google.deploy.description.ResizeGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.SerializeApplicationDescription.SerializeApplicationDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.SerializeApplicationAtomicOperation.SerializeApplicationAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.SERIALIZE_APPLICATION)
@Component("serializeApplicationDescription")
class SerializeApplicationAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new SerializeApplicationAtomicOperation(convertDescription(input))
  }

  @Override
  SerializeApplicationDescription convertDescription(Map input) {
    GoogleAtomicOperationConverterHelper.convertDescription(input, this, SerializeApplicationDescription)
  }

}
