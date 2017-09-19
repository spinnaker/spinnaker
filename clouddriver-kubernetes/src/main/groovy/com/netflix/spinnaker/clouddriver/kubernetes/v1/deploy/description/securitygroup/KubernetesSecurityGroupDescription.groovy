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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.KubernetesAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class KubernetesSecurityGroupDescription extends KubernetesAtomicOperationDescription {
  String securityGroupName
  String app
  String stack
  String detail
  String namespace

  KubernetesIngressBackend ingress
  List<KubernetesIngressTls> tls
  List<KubernetesIngressRule> rules
}

@AutoClone
@Canonical
class KubernetesIngressBackend {
  String serviceName
  int port
}

@AutoClone
@Canonical
class KubernetesIngressTls {
  List<String> hosts
  String secretName
}

@AutoClone
@Canonical
class KubernetesIngressRule {
  String host
  KubernetesIngressRuleValue value
}

@AutoClone
@Canonical
class KubernetesIngressRuleValue {
  KubernetesHttpIngressRuleValue http
}

@AutoClone
@Canonical
class KubernetesHttpIngressRuleValue {
  List<KubernetesHttpIngressPath> paths
}

@AutoClone
@Canonical
class KubernetesHttpIngressPath {
  String path
  KubernetesIngressBackend ingress
}
