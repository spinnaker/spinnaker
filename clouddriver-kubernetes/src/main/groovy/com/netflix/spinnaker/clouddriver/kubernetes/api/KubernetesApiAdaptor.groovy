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

package com.netflix.spinnaker.clouddriver.kubernetes.api

import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient

class KubernetesApiAdaptor {
  KubernetesClient client

  KubernetesApiAdaptor(KubernetesClient client) {
    if (!client) {
      throw new IllegalArgumentException("Client may not be null.")
    }
    this.client = client
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    client.replicationControllers().inNamespace(namespace).list().items
  }

  List<Pod> getPods(String namespace, String replicationControllerName) {
    client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    client.replicationControllers().inNamespace(namespace).create(replicationController)
  }

  Service getService(String namespace, String service) {
    client.services().inNamespace(namespace).withName(service).get()
  }

  Service createService(String namespace, Service service) {
    client.services().inNamespace(namespace).create(service)
  }

  Service replaceService(String namespace, String name, Service service) {
    client.services().inNamespace(namespace).withName(name).replace(service)
  }

  Secret getSecret(String namespace, String secret) {
    client.secrets().inNamespace(namespace).withName(secret).get()
  }

  Boolean deleteSecret(String namespace, String secret) {
    client.secrets().inNamespace(namespace).withName(secret).delete()
  }

  Secret createSecret(String namespace, Secret secret) {
    client.secrets().inNamespace(namespace).create(secret)
  }

  Namespace getNamespace(String namespace) {
    client.namespaces().withName(namespace).get()
  }

  Namespace createNamespace(Namespace namespace) {
    client.namespaces().create(namespace)
  }
}
