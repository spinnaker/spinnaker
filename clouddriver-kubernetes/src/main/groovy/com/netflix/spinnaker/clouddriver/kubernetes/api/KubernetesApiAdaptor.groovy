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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesOperationException
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.extensions.Job
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException

import java.util.concurrent.TimeUnit

@Slf4j
class KubernetesApiAdaptor {
  io.fabric8.kubernetes.client.Config config
  String account

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100

  KubernetesApiAdaptor(String account, io.fabric8.kubernetes.client.Config config) {
    if (!config) {
      throw new IllegalArgumentException("Config may not be null.")
    }
    this.config = config
    this.account = account
  }

  KubernetesOperationException formatException(String operation, String namespace, KubernetesClientException e) {
    account ? new KubernetesOperationException(account, "$operation in $namespace", e) :
      new KubernetesOperationException("$operation in $namespace", e)
  }

  KubernetesOperationException formatException(String operation, KubernetesClientException e) {
    account ? new KubernetesOperationException(account, "$operation", e) :
      new KubernetesOperationException("$operation", e)
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

  /*
   * TODO(lwander): Delete once https://github.com/fabric8io/kubernetes-client/issues/408 is resolved
   *
   * We need to recreate the client everytime we use the API because it relies on a singleton adaptor lookup
   * that overwrites configs when more than a single cluster is configured. By recreating it, we ensure that the
   * adaptor lookup table is up-to-date.
   */
  KubernetesClient client() {
    new DefaultKubernetesClient(this.config)
  }

  /*
   * Atomically create a new client, and pass it to the given doOperation closure to operate against the kubernetes API
   */
  private <T> T atomicWrapper(String operationMessage, String namespace, Closure<T> doOperation) {
    // Outside the try {} block, because in the case of an exception being thrown here, we don't want to try unlocking
    // the mutex in the finally block.
    SharedMutex.lock()
    T result = null
    Exception failure
    try {
      result = doOperation(client())
    } catch (KubernetesClientException e) {
      if (namespace) {
        failure = formatException(operationMessage, namespace, e)
      } else {
        failure = formatException(operationMessage, e)
      }
    } catch (Exception e) {
      failure = e
    } finally {
      SharedMutex.unlock()
      if (failure) {
        throw failure
      } else {
        return result
      }
    }
  }

  Ingress createIngress(String namespace, Ingress ingress) {
    atomicWrapper("Create Ingress ${ingress?.metadata?.name}", namespace) { KubernetesClient client ->
      client.extensions().ingresses().inNamespace(namespace).create(ingress)
    }
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    atomicWrapper("Replace Ingress ${name}", namespace) { KubernetesClient client ->
      client.extensions().ingresses().inNamespace(namespace).withName(name).replace(ingress)
    }
  }

  Ingress getIngress(String namespace, String name) {
    atomicWrapper("Get Ingress $name", namespace) { KubernetesClient client ->
      client.extensions().ingresses().inNamespace(namespace).withName(name).get()
    }
  }

  boolean deleteIngress(String namespace, String name) {
    atomicWrapper("Delete Ingress $name", namespace) { KubernetesClient client ->
      client.extensions().ingresses().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Ingress> getIngresses(String namespace) {
    atomicWrapper("Get Ingresses", namespace) { KubernetesClient client ->
      client.extensions().ingresses().inNamespace(namespace).list().items
    }
  }

  List<ReplicaSet> getReplicaSets(String namespace) {
    atomicWrapper("Get Replica Sets", namespace) { KubernetesClient client ->
      client.extensions().replicaSets().inNamespace(namespace).list().items
    }
  }

  List<Pod> getReplicaSetPods(String namespace, String replicaSetName) {
    atomicWrapper("Get Replica Set Pods for $replicaSetName", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicaSetName).list().items
    }
  }

  ReplicaSet getReplicaSet(String namespace, String serverGroupName) {
    atomicWrapper("Get Replica Set $serverGroupName", namespace) { KubernetesClient client ->
      client.extensions().replicaSets().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  List<Pod> getJobPods(String namespace, String jobName) {
    atomicWrapper("Get Job Pods for $jobName", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.JOB_LABEL, jobName).list().items
    }
  }

  Pod getPod(String namespace, String name) {
    atomicWrapper("Get Pod $name", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withName(name).get()
    }
  }

  List<Pod> getPods(String namespace, Map<String, String> labels) {
    atomicWrapper("Get Pods matching $labels", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withLabels(labels).list().items
    }
  }

  boolean deletePod(String namespace, String name) {
    atomicWrapper("Delete Pod $name", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getPods(String namespace) {
    atomicWrapper("Get Pods", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).list().items
    }
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    atomicWrapper("Get Replication Controllers", namespace) { KubernetesClient client ->
      client.replicationControllers().inNamespace(namespace).list().items
    }
  }

  List<Pod> getReplicationControllerPods(String namespace, String replicationControllerName) {
    atomicWrapper("Get Replication Controller Pods for $replicationControllerName", namespace) { KubernetesClient client ->
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
    }
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    atomicWrapper("Get Replication Controller $serverGroupName", namespace) { KubernetesClient client ->
      client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    atomicWrapper("Create Replication Controller ${replicationController?.metadata?.name}", namespace) { KubernetesClient client ->
      client.replicationControllers().inNamespace(namespace).create(replicationController)
    }
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    atomicWrapper("Resize Replication Controller $name to $size", namespace) { KubernetesClient client ->
      client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
    }
  }

  boolean hardDestroyReplicationController(String namespace, String name) {
    atomicWrapper("Hard Destroy Replication Controller $name", namespace) { KubernetesClient client ->
      client.replicationControllers().inNamespace(namespace).withName(name).delete()
    }
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    atomicWrapper("Toggle Pod Labels to $value for $name", namespace) { KubernetesClient client ->
      def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().done()
    }
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    atomicWrapper("Toggle Replication Controller Labels to $value for $name", namespace) { KubernetesClient client ->
      def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    }
  }

  Service getService(String namespace, String service) {
    atomicWrapper("Get Service $service", namespace) { KubernetesClient client ->
      client.services().inNamespace(namespace).withName(service).get()
    }
  }

  Service createService(String namespace, Service service) {
    atomicWrapper("Create Service $service", namespace) { KubernetesClient client ->
      client.services().inNamespace(namespace).create(service)
    }
  }

  boolean deleteService(String namespace, String name) {
    atomicWrapper("Delete Service $name", namespace) { KubernetesClient client ->
      client.services().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Service> getServices(String namespace) {
    atomicWrapper("Get Services", namespace) { KubernetesClient client ->
      client.services().inNamespace(namespace).list().items
    }
  }

  Service replaceService(String namespace, String name, Service service) {
    atomicWrapper("Replace Service $name", namespace) { KubernetesClient client ->
      client.services().inNamespace(namespace).withName(name).replace(service)
    }
  }

  Secret getSecret(String namespace, String secret) {
    atomicWrapper("Get Secret $secret", namespace) { KubernetesClient client ->
      client.secrets().inNamespace(namespace).withName(secret).get()
    }
  }

  Boolean deleteSecret(String namespace, String secret) {
    atomicWrapper("Delete Secret $secret", namespace) { KubernetesClient client ->
      client.secrets().inNamespace(namespace).withName(secret).delete()
    }
  }

  Secret createSecret(String namespace, Secret secret) {
    atomicWrapper("Create Secret $secret", namespace) { KubernetesClient client ->
      client.secrets().inNamespace(namespace).create(secret)
    }
  }

  Namespace getNamespace(String namespace) {
    atomicWrapper("Get Namespace $namespace", null) { KubernetesClient client ->
      client.namespaces().withName(namespace).get()
    }
  }

  List<Namespace> getNamespaces() {
    atomicWrapper("Get Namespaces", null) { KubernetesClient client ->
      client.namespaces().list().items
    }
  }

  List<String> getNamespacesByName() {
    atomicWrapper("Get Namespaces", null) { KubernetesClient client ->
      client.namespaces().list().items.collect {
        it.metadata.name
      }
    }
  }

  Namespace createNamespace(Namespace namespace) {
    atomicWrapper("Create Namespace $namespace", null) { KubernetesClient client ->
      client.namespaces().create(namespace)
    }
  }

  Job createJob(String namespace, Job job) {
    atomicWrapper("Create Job ${job?.metadata?.name}", namespace) { KubernetesClient client ->
      client.extensions().jobs().inNamespace(namespace).create(job)
    }
  }

  List<Job> getJobs(String namespace) {
    atomicWrapper("Get Jobs", namespace) { KubernetesClient client ->
      client.extensions().jobs().inNamespace(namespace).list().items
    }
  }

  Job getJob(String namespace, String name) {
    atomicWrapper("Get Job $name", namespace) { KubernetesClient client ->
      client.extensions().jobs().inNamespace(namespace).withName(name).get()
    }
  }

  boolean hardDestroyJob(String namespace, String name) {
    atomicWrapper("Hard Destroy Job $name", namespace) { KubernetesClient client ->
      client.extensions().jobs().inNamespace(namespace).withName(name).delete()
    }
  }
}
