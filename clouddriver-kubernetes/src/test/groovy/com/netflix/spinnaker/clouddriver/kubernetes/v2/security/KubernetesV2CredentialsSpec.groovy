/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import spock.lang.Specification

class KubernetesV2CredentialsSpec extends Specification {
  Registry registry = Stub(Registry)
  KubectlJobExecutor kubectlJobExecutor = Stub(KubectlJobExecutor)
  String NAMESPACE = "my-namespace"

  private getBuilder() {
    return new KubernetesV2Credentials.Builder()
      .registry(registry)
      .jobExecutor(kubectlJobExecutor)
      .namespaces([NAMESPACE])
  }

  void "Built-in Kubernetes kinds are considered valid by default"() {
    when:
    KubernetesV2Credentials credentials = getBuilder()
      .checkPermissionsOnStartup(false)
      .build()

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Built-in Kubernetes kinds are considered valid by default when kinds is empty"() {
    when:
    KubernetesV2Credentials credentials = getBuilder()
      .checkPermissionsOnStartup(false)
      .kinds([])
      .build()

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Only explicitly listed kinds are valid when kinds is not empty"() {
    when:
    KubernetesV2Credentials credentials = getBuilder()
      .checkPermissionsOnStartup(false)
      .kinds(["deployment"])
      .build()

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == false
  }

  void "Explicitly omitted kinds are not valid"() {
    when:
    KubernetesV2Credentials credentials = getBuilder()
      .checkPermissionsOnStartup(false)
      .omitKinds(["deployment"])
      .build()

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == false
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Kinds that are not readable are considered invalid"() {
    when:
    KubernetesV2Credentials credentials = getBuilder()
      .checkPermissionsOnStartup(true)
      .build()

    then:
    kubectlJobExecutor.list(_, { it.contains(KubernetesKind.DEPLOYMENT) }, _, _) >> {
      throw new KubectlJobExecutor.KubectlException()
    }
    kubectlJobExecutor.list(_, { !it.contains(KubernetesKind.DEPLOYMENT) }, _, _) >> {
      return Collections.emptyList()
    }
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == false
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }
}
