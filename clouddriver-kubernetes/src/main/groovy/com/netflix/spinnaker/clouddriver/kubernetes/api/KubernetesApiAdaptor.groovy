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
import io.fabric8.kubernetes.api.model.extensions.Deployment
import io.fabric8.kubernetes.api.model.extensions.DoneableDeployment
import io.fabric8.kubernetes.api.model.extensions.HorizontalPodAutoscaler
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
  static final String DEPLOYMENT_ANNOTATION = "deployment.kubernetes.io"
  final KubernetesClient client

  KubernetesApiAdaptor(String account, io.fabric8.kubernetes.client.Config config) {
    if (!config) {
      throw new IllegalArgumentException("Config may not be null.")
    }
    this.config = config
    this.account = account
    this.client = new DefaultKubernetesClient(this.config)
  }

  KubernetesOperationException formatException(String operation, String namespace, KubernetesClientException e) {
    account ? new KubernetesOperationException(account, "$operation in $namespace", e) :
      new KubernetesOperationException("$operation in $namespace", e)
  }

  KubernetesOperationException formatException(String operation, KubernetesClientException e) {
    account ? new KubernetesOperationException(account, "$operation", e) :
      new KubernetesOperationException("$operation", e)
  }

  Boolean blockUntilResourceConsistent(Object desired, Closure<Long> getGeneration, Closure getResource) {
    def current = getResource()

    def wait = RETRY_INITIAL_WAIT_MILLIS
    def attempts = 0
    while (getGeneration(current) < getGeneration(desired)) {
      attempts += 1
      if (attempts > RETRY_COUNT) {
        return false
      }

      sleep(wait)
      wait = [wait * 2, RETRY_MAX_WAIT_MILLIS].min()

      current = getResource()
    }

    return true
  }

  /*
   * Atomically create a new client, and pass it to the given doOperation closure to operate against the kubernetes API
   */
  private <T> T exceptionWrapper(String operationMessage, String namespace, Closure<T> doOperation) {
    T result = null
    Exception failure
    try {
      result = doOperation()
    } catch (KubernetesClientException e) {
      if (namespace) {
        failure = formatException(operationMessage, namespace, e)
      } else {
        failure = formatException(operationMessage, e)
      }
    } catch (Exception e) {
      failure = e
    } finally {
      if (failure) {
        throw failure
      } else {
        return result
      }
    }
  }

  List<Event> getEvents(String namespace, HasMetadata object) {
    exceptionWrapper("Get Events", namespace) {
      client.events().inNamespace(namespace).withField("involvedObject.uid", object.metadata.uid).list().items
    }
  }

  Map<String, List<Event>> getEvents(String namespace, String type) {
    exceptionWrapper("Get Events", namespace) {
      def events = client.events().inNamespace(namespace).withField("involvedObject.kind", type).list().items
      def eventMap = [:].withDefault { _ -> [] }
      events.each { Event event ->
        eventMap[event.involvedObject.name] += [event]
      }
      return eventMap
    }
  }

  Ingress createIngress(String namespace, Ingress ingress) {
    exceptionWrapper("Create Ingress ${ingress?.metadata?.name}", namespace) {
      client.extensions().ingresses().inNamespace(namespace).create(ingress)
    }
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    exceptionWrapper("Replace Ingress ${name}", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).replace(ingress)
    }
  }

  Ingress getIngress(String namespace, String name) {
    exceptionWrapper("Get Ingress $name", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).get()
    }
  }

  boolean deleteIngress(String namespace, String name) {
    exceptionWrapper("Delete Ingress $name", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Ingress> getIngresses(String namespace) {
    exceptionWrapper("Get Ingresses", namespace) {
      client.extensions().ingresses().inNamespace(namespace).list().items
    }
  }

  List<ReplicaSet> getReplicaSets(String namespace) {
    exceptionWrapper("Get Replica Sets", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).list().items
    }
  }

  boolean hardDestroyReplicaSet(String namespace, String name) {
    exceptionWrapper("Hard Destroy Replica Set $name", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getReplicaSetPods(String namespace, String replicaSetName) {
    exceptionWrapper("Get Replica Set Pods for $replicaSetName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.SERVER_GROUP_LABEL, replicaSetName).list().items
    }
  }

  ReplicaSet getReplicaSet(String namespace, String serverGroupName) {
    exceptionWrapper("Get Replica Set $serverGroupName", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  ReplicaSet resizeReplicaSet(String namespace, String name, int size) {
    exceptionWrapper("Resize Replica Set $name to $size", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(name).scale(size)
    }
  }

  ReplicaSet createReplicaSet(String namespace, ReplicaSet replicaSet) {
    exceptionWrapper("Create Replica Set ${replicaSet?.metadata?.name}", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).create(replicaSet)
    }
  }

  List<Pod> getJobPods(String namespace, String jobName) {
    exceptionWrapper("Get JobStatus Pods for $jobName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.JOB_LABEL, jobName).list().items
    }
  }

  Pod getPod(String namespace, String name) {
    exceptionWrapper("Get Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).get()
    }
  }

  List<Pod> getPods(String namespace, Map<String, String> labels) {
    exceptionWrapper("Get Pods matching $labels", namespace) {
      client.pods().inNamespace(namespace).withLabels(labels).list().items
    }
  }

  boolean deletePod(String namespace, String name) {
    exceptionWrapper("Delete Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getPods(String namespace) {
    exceptionWrapper("Get Pods", namespace) {
      client.pods().inNamespace(namespace).list().items
    }
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    exceptionWrapper("Get Replication Controllers", namespace) {
      client.replicationControllers().inNamespace(namespace).list().items
    }
  }

  List<Pod> getReplicationControllerPods(String namespace, String replicationControllerName) {
    exceptionWrapper("Get Replication Controller Pods for $replicationControllerName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.SERVER_GROUP_LABEL, replicationControllerName).list().items
    }
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    exceptionWrapper("Get Replication Controller $serverGroupName", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    exceptionWrapper("Create Replication Controller ${replicationController?.metadata?.name}", namespace) {
      client.replicationControllers().inNamespace(namespace).create(replicationController)
    }
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    exceptionWrapper("Resize Replication Controller $name to $size", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
    }
  }

  boolean hardDestroyReplicationController(String namespace, String name) {
    exceptionWrapper("Hard Destroy Replication Controller $name", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(name).delete()
    }
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("Toggle Pod Labels to $value for $name", namespace) {
      def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().done()
    }
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("Toggle Replication Controller Labels to $value for $name", namespace) {
      def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    }
  }

  ReplicaSet toggleReplicaSetSpecLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("Toggle Replica Set Labels to $value for $name", namespace) {
      def edit = client.extensions().replicaSets().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    }
  }

  Service getService(String namespace, String service) {
    exceptionWrapper("Get Service $service", namespace) {
      client.services().inNamespace(namespace).withName(service).get()
    }
  }

  Service createService(String namespace, Service service) {
    exceptionWrapper("Create Service $service", namespace) {
      client.services().inNamespace(namespace).create(service)
    }
  }

  boolean deleteService(String namespace, String name) {
    exceptionWrapper("Delete Service $name", namespace) {
      client.services().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Service> getServices(String namespace) {
    exceptionWrapper("Get Services", namespace) {
      client.services().inNamespace(namespace).list().items
    }
  }

  Service replaceService(String namespace, String name, Service service) {
    exceptionWrapper("Replace Service $name", namespace) {
      client.services().inNamespace(namespace).withName(name).replace(service)
    }
  }

  Secret getSecret(String namespace, String secret) {
    exceptionWrapper("Get Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).withName(secret).get()
    }
  }

  Boolean deleteSecret(String namespace, String secret) {
    exceptionWrapper("Delete Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).withName(secret).delete()
    }
  }

  Secret createSecret(String namespace, Secret secret) {
    exceptionWrapper("Create Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).create(secret)
    }
  }

  Namespace getNamespace(String namespace) {
    exceptionWrapper("Get Namespace $namespace", null) {
      client.namespaces().withName(namespace).get()
    }
  }

  List<Namespace> getNamespaces() {
    exceptionWrapper("Get Namespaces", null) {
      client.namespaces().list().items
    }
  }

  List<String> getNamespacesByName() {
    exceptionWrapper("Get Namespaces", null) {
      client.namespaces().list().items.collect {
        it.metadata.name
      }
    }
  }

  Namespace createNamespace(Namespace namespace) {
    exceptionWrapper("Create Namespace $namespace", null) {
      client.namespaces().create(namespace)
    }
  }

  Pod createPod(String namespace, Pod pod) {
    exceptionWrapper("Create Pod ${pod?.metadata?.name}", namespace) {
      client.pods().inNamespace(namespace).create(pod)
    }
  }

  List<Job> getJobs(String namespace) {
    exceptionWrapper("Get Jobs", namespace) {
      client.extensions().jobs().inNamespace(namespace).list().items
    }
  }

  Job getJob(String namespace, String name) {
    exceptionWrapper("Get JobStatus $name", namespace) {
      client.extensions().jobs().inNamespace(namespace).withName(name).get()
    }
  }

  boolean hardDestroyPod(String namespace, String name) {
    exceptionWrapper("Hard Destroy Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).delete()
    }
  }

  HorizontalPodAutoscaler createAutoscaler(String namespace, HorizontalPodAutoscaler autoscaler) {
    exceptionWrapper("Create Autoscaler ${autoscaler?.metadata?.name}", namespace) {
      client.extensions().horizontalPodAutoscalers().inNamespace(namespace).create(autoscaler)
    }
  }

  HorizontalPodAutoscaler getAutoscaler(String namespace, String name) {
    exceptionWrapper("Get Autoscaler $name", namespace) {
      client.extensions().horizontalPodAutoscalers().inNamespace(namespace).withName(name).get()
    }
  }

  Map<String, HorizontalPodAutoscaler> getAutoscalers(String namespace, String kind) {
    exceptionWrapper("Get Autoscalers", namespace) {
      client.extensions().horizontalPodAutoscalers().inNamespace(namespace).list().items.collectEntries { def autoscaler ->
        autoscaler.spec.scaleRef.kind == kind ? [(autoscaler.metadata.name): autoscaler] : [:]
      }
    }
  }

  boolean deleteAutoscaler(String namespace, String name) {
    exceptionWrapper("Destroy Autoscaler $name", namespace) {
      client.extensions().horizontalPodAutoscalers().inNamespace(namespace).withName(name).delete()
    }
  }

  Deployment getDeployment(String namespace, String name) {
    exceptionWrapper("Get Deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).get()
    }
  }

  Deployment createDeployment(String namespace, Deployment deployment) {
    exceptionWrapper("Create Deployment $deployment.metadata.name", namespace) {
      client.extensions().deployments().inNamespace(namespace).create(deployment)
    }
  }

  DoneableDeployment editDeployment(String namespace, String name) {
    exceptionWrapper("Edit deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).edit()
    }
  }

  boolean deleteDeployment(String namespace, String name) {
    exceptionWrapper("Delete Deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getPodsForDeployment(String namespace, Deployment deployment) {
    def labels = deployment.spec.selector.matchLabels
    return getPods(namespace, labels)
  }

  List<Pod> getPodsForReplicaSet(String namespace, ReplicaSet replicaSet) {
    def labels = replicaSet.spec.selector.matchLabels
    return getPods(namespace, labels)
  }

  String getDeploymentRevision(Deployment deployment) {
    return deployment.metadata.annotations["$DEPLOYMENT_ANNOTATION/revision"]
  }
  
  String getDeploymentRevision(ReplicaSet replicaSet) {
    return replicaSet.metadata.annotations["$DEPLOYMENT_ANNOTATION/revision"]
  }

}
