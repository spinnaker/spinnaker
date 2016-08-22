/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials

/**
 * Builds the appropriate {@link OpenstackClientProvider} based on the configuration.
 */
class OpenstackProviderFactory {

  static OpenstackClientProvider createProvider(OpenstackNamedAccountCredentials credentials) {
    OpenstackIdentityProvider identityProvider = new OpenstackIdentityV3Provider(credentials)
    OpenstackComputeV2Provider computeProvider = new OpenstackComputeV2Provider(identityProvider)
    OpenstackNetworkingProvider networkingProvider = new OpenstackNetworkingV2Provider(identityProvider)
    OpenstackOrchestrationProvider orchestrationProvider = new OpenstackOrchestrationV1Provider(identityProvider)
    OpenstackImageProvider imageProvider = new OpenstackImageV1Provider(identityProvider)
    OpenstackLoadBalancerProvider loadBalancerProvider = new OpenstackLoadBalancerV2Provider(identityProvider)
    new OpenstackClientProvider(identityProvider, computeProvider, networkingProvider, orchestrationProvider, imageProvider, loadBalancerProvider)
  }

}
