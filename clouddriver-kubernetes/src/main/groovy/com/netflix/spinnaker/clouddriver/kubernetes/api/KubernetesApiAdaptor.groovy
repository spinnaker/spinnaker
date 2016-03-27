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
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.client.KubernetesClient

import java.util.concurrent.TimeUnit

class KubernetesApiAdaptor {
  KubernetesClient client

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100

  KubernetesApiAdaptor(KubernetesClient client) {
    if (!client) {
      throw new IllegalArgumentException("Client may not be null.")
    }
    this.client = client
  }

  /*
   * Exponential backoff strategy for waiting on changes to replication controllers
   */
  Boolean blockUntilReplicationControllerConsistent(ReplicationController desired) {
    def current = getReplicationController(desired.metadata.namespace, desired.metadata.name)

    def wait = RETRY_INITIAL_WAIT_MILLIS
    def attempts = 0
    while (current.status.observedGeneration < desired.status.observedGeneration) {
      attempts += 1
      if (attempts > RETRY_COUNT) {
        return false
      }

      sleep(wait)
      wait = [wait * 2, RETRY_MAX_WAIT_MILLIS].min()

      current = getReplicationController(desired.metadata.namespace, desired.metadata.name)
    }

    return true
  }

  Ingress createIngress(String namespace, Ingress ingress) {
    client.extensions().ingress().inNamespace(namespace).create(ingress)
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    client.extensions().ingress().inNamespace(namespace).withName(name).replace(ingress)
  }

  Ingress getIngress(String namespace, String name) {
    client.extensions().ingress().inNamespace(namespace).withName(name).get()
  }

  boolean deleteIngress(String namespace, String name) {
    client.extensions().ingress().inNamespace(namespace).withName(name).delete()
  }

  List<Ingress> getIngresses(String namespace) {
    client.extensions().ingress().inNamespace(namespace).list().items
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    client.replicationControllers().inNamespace(namespace).list().items
  }

  List<Pod> getPods(String namespace, String replicationControllerName) {
    client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
  }

  Pod getPod(String namespace, String name) {
    client.pods().inNamespace(namespace).withName(name).get()
  }

  boolean deletePod(String namespace, String name) {
    client.pods().inNamespace(namespace).withName(name).delete()
  }

  List<Pod> getPods(String namespace) {
    client.pods().inNamespace(namespace).list().items
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    client.replicationControllers().inNamespace(namespace).create(replicationController)
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
  }

  boolean hardDestroyReplicationController(String namespace, String name) {
    client.replicationControllers().inNamespace(namespace).withName(name).delete()
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
      edit.addToLabels(it, value)
    }

    edit.endMetadata().done()
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
      edit.addToLabels(it, value)
    }

    edit.endMetadata().endTemplate().endSpec().done()
  }

  Service getService(String namespace, String service) {
    client.services().inNamespace(namespace).withName(service).get()
  }

  Service createService(String namespace, Service service) {
    client.services().inNamespace(namespace).create(service)
  }

  boolean deleteService(String namespace, String name) {
    client.services().inNamespace(namespace).withName(name).delete()
  }

  List<Service> getServices(String namespace) {
    client.services().inNamespace(namespace).list().items
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
