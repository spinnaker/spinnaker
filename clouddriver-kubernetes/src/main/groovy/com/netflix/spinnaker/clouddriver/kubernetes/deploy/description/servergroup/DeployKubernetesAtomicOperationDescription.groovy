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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeployKubernetesAtomicOperationDescription extends KubernetesAtomicOperationDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  String namespace
  Integer targetSize
  List<String> loadBalancers
  List<String> securityGroups
  List<KubernetesContainerDescription> containers
}

@AutoClone
@Canonical
class KubernetesContainerPort {
  String name
  int containerPort
  String protocol
  String hostIp
  int hostPort
}

@AutoClone
@Canonical
class KubernetesContainerDescription {
  String name
  String image
  KubernetesResourceDescription requests
  KubernetesResourceDescription limits
  List<KubernetesContainerPort> ports
}

@AutoClone
@Canonical
class KubernetesResourceDescription {
  String memory
  String cpu
}
