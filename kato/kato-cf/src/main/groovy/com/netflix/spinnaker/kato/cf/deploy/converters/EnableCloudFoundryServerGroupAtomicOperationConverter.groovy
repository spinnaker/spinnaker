/*
 * Copyright 2014 Pivotal, Inc.
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

package com.netflix.spinnaker.kato.cf.deploy.converters

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.kato.cf.deploy.description.EnableDisableCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.EnableCloudFoundryServerGroupAtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@CloudFoundryOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableCloudFoundryServerGroupDescription")
class EnableCloudFoundryServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new EnableCloudFoundryServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  EnableDisableCloudFoundryServerGroupDescription convertDescription(Map input) {
    log.info "Enabling for ${input}"
    new EnableDisableCloudFoundryServerGroupDescription([
        serverGroupName     : input.serverGroupName,
        zone                : input.containsKey('zones') ? input.zones[0] : input.containsKey('zone') ? input.zone : input.region,
        nativeLoadBalancers : input.nativeLoadBalancers,
        credentials         : getCredentialsObject(input.credentials as String)
    ])
  }
}
