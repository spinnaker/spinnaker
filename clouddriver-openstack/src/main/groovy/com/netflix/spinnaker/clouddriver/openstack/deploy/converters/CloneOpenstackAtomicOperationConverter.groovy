/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.converters

import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.CloneOpenstackAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@OpenstackOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneOpenstackAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport{
  @Override
  AtomicOperation convertOperation(Map input) {
    new CloneOpenstackAtomicOperation(convertDescription(input))
  }

  @Override
  CloneOpenstackAtomicOperationDescription convertDescription(Map input) {
    OpenstackAtomicOperationConverterHelper.convertDescription(input, this, CloneOpenstackAtomicOperationDescription)
  }
}
