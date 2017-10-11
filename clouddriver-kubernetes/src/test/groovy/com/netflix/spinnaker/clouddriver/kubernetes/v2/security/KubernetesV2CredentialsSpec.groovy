/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import io.kubernetes.client.models.V1Service
import io.kubernetes.client.models.V1beta1Ingress
import io.kubernetes.client.models.V1beta1ReplicaSet
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesV2CredentialsSpec extends Specification {
  @Unroll
  void "correctly annotates #kind & #apiVersion"() {
    expect:
    def result = KubernetesV2Credentials.annotateMissingFields(obj, clazz, apiVersion, kind)
    result.kind == kind?.name
    result.apiVersion == apiVersion?.name

    where:
    clazz                   | obj                     | apiVersion                              | kind
    V1beta1ReplicaSet.class | new V1beta1ReplicaSet() | KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.REPLICA_SET
    V1beta1ReplicaSet.class | new V1beta1ReplicaSet() | null                                    | KubernetesKind.REPLICA_SET
    V1beta1ReplicaSet.class | new V1beta1ReplicaSet() | KubernetesApiVersion.EXTENSIONS_V1BETA1 | null
    V1beta1ReplicaSet.class | new V1beta1ReplicaSet() | null                                    | null
    V1beta1Ingress.class    | new V1beta1Ingress()    | KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.INGRESS
    V1Service.class         | new V1Service()         | KubernetesApiVersion.V1                 | KubernetesKind.SERVICE
  }
}
