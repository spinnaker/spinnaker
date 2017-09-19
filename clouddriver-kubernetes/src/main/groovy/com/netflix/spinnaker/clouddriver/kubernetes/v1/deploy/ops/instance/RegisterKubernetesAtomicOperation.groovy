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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.instance.AbstractRegistrationKubernetesAtomicOperationDescription

/*
 * curl -X POST -H "Content-Type: application/json" -d '[ { "registerInstancesWithLoadBalancer": { "loadBalancers": ["kub-test-lb"], "instanceIds": ["kub-test-v000-beef"], "namespace": "default", "credentials": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
 */
class RegisterKubernetesAtomicOperation extends AbstractRegistrationKubernetesAtomicOperation {
  String basePhase = 'REGISTER'

  String action = 'true'

  String verb = 'registering'

  RegisterKubernetesAtomicOperation(AbstractRegistrationKubernetesAtomicOperationDescription description) {
    super(description)
  }
}
