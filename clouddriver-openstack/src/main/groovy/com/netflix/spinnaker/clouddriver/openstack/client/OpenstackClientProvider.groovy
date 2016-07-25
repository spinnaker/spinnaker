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

/**
 * Provides access to the Openstack API.
 */
class OpenstackClientProvider {

  @Delegate
  OpenstackIdentityProvider identityProvider

  @Delegate
  OpenstackComputeV2Provider computeProvider

  @Delegate
  OpenstackNetworkingProvider networkingProvider

  @Delegate
  OpenstackOrchestrationProvider orchestrationProvider

  @Delegate
  OpenstackImageProvider imageProvider

  public OpenstackClientProvider(OpenstackIdentityProvider identityProvider,
                                 OpenstackComputeV2Provider computeProvider,
                                 OpenstackNetworkingProvider networkingProvider,
                                 OpenstackOrchestrationProvider orchestrationProvider,
                                 OpenstackImageProvider imageProvider) {
    this.identityProvider = identityProvider
    this.computeProvider = computeProvider
    this.networkingProvider = networkingProvider
    this.orchestrationProvider = orchestrationProvider
    this.imageProvider = imageProvider
  }

}
