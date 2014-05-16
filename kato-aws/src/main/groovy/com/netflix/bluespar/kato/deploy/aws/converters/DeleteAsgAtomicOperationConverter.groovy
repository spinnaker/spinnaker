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

package com.netflix.bluespar.kato.deploy.aws.converters

import com.netflix.bluespar.kato.deploy.aws.description.DeleteAsgDescription
import com.netflix.bluespar.kato.deploy.aws.ops.DeleteAsgAtomicOperation
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("deleteAsgDescription")
class DeleteAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeleteAsgAtomicOperation(convertDescription(input))
  }

  DeleteAsgDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new DeleteAsgDescription(input)
  }
}
