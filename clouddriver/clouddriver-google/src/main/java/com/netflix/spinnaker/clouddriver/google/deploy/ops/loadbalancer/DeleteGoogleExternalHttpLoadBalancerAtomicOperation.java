/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer;

import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription;

/**
 * Deletes regional external Application Load Balancer listeners owned by `EXTERNAL_MANAGED`.
 *
 * <p>The shared regional HTTP delete path handles URL maps that are referenced by both internal and
 * external managed schemes; this specialization supplies the scheme boundary used to avoid deleting
 * another regional HTTP-family load balancer's resources.
 */
public class DeleteGoogleExternalHttpLoadBalancerAtomicOperation
    extends DeleteGoogleInternalHttpLoadBalancerAtomicOperation {
  public DeleteGoogleExternalHttpLoadBalancerAtomicOperation(
      DeleteGoogleLoadBalancerDescription description) {
    super(description);
  }

  @Override
  protected String getBasePhase() {
    return "DELETE_EXTERNAL_HTTP_LOAD_BALANCER";
  }

  @Override
  protected String getLoadBalancerDescriptionLabel() {
    return "Regional External HTTP(S) load balancer";
  }

  @Override
  protected String getLoadBalancingScheme() {
    return "EXTERNAL_MANAGED";
  }
}
