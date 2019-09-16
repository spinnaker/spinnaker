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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.api

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.DoneableHorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.DoneableSecret
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
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
  final Registry spectatorRegistry
  final Clock spectatorClock

  public spectatorRegistry() { return spectatorRegistry }

  KubernetesApiAdaptor(String account, io.fabric8.kubernetes.client.Config config, Registry spectatorRegistry) {
    if (!config) {
      throw new IllegalArgumentException("Config may not be null.")
    }
    this.config = config
    this.account = account
    this.client = new DefaultKubernetesClient(this.config)
    this.spectatorRegistry = spectatorRegistry
    this.spectatorClock = spectatorRegistry.clock()
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
  private <T> T exceptionWrapper(String methodName, String operationMessage, String namespace, Closure<T> doOperation) {
    T result = null
    Exception failure
    long startTime = spectatorClock.monotonicTime()

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

      def tags = ["method": methodName,
                  "account": account,
                  "namespace" : namespace ? namespace : "none",
                  "success": failure ? "false": "true"]
      if (failure) {
        tags["reason"] = failure.class.simpleName
      }

      spectatorRegistry.timer(
              spectatorRegistry.createId("kubernetes.api", tags))
              .record(spectatorClock.monotonicTime() - startTime, TimeUnit.NANOSECONDS)

      if (failure) {
        throw failure
      } else {
        return result
      }
    }
  }

  List<Event> getEvents(String namespace, HasMetadata object) {
    exceptionWrapper("events.list", "Get Events", namespace) {
      client.events().inNamespace(namespace).withField("involvedObject.uid", object.metadata.uid).list().items
    }
  }

  Map<String, List<Event>> getEvents(String namespace, String type) {
    exceptionWrapper("events.list", "Get Events", namespace) {
      def events = client.events().inNamespace(namespace).withField("involvedObject.kind", type).list().items
      def eventMap = [:].withDefault { _ -> [] }
      events.each { Event event ->
        eventMap[event.involvedObject.name] += [event]
      }
      return eventMap
    }
  }

  Ingress createIngress(String namespace, Ingress ingress) {
    exceptionWrapper("ingresses.create", "Create Ingress ${ingress?.metadata?.name}", namespace) {
      client.extensions().ingresses().inNamespace(namespace).create(ingress)
    }
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    exceptionWrapper("ingresses.replace", "Replace Ingress ${name}", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).replace(ingress)
    }
  }

  Ingress getIngress(String namespace, String name) {
    exceptionWrapper("ingresses.get", "Get Ingress $name", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).get()
    }
  }

  boolean deleteIngress(String namespace, String name) {
    exceptionWrapper("ingresses.delete", "Delete Ingress $name", namespace) {
      client.extensions().ingresses().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Ingress> getIngresses(String namespace) {
    exceptionWrapper("ingresses.list", "Get Ingresses", namespace) {
      client.extensions().ingresses().inNamespace(namespace).list().items
    }
  }

  List<ReplicaSet> getReplicaSets(String namespace) {
    exceptionWrapper("replicaSets.list", "Get Replica Sets", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).list().items
    }
  }

  List<ReplicaSet> getReplicaSets(String namespace, Map<String, String> labels) {
    exceptionWrapper("replicaSets.list", "Get Replica Sets", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withLabels(labels).list().items
    }
  }

  boolean hardDestroyReplicaSet(String namespace, String name) {
    exceptionWrapper("replicaSets.delete", "Hard Destroy Replica Set $name", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getReplicaSetPods(String namespace, String replicaSetName) {
    exceptionWrapper("pods.list", "Get Replica Set Pods for $replicaSetName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.SERVER_GROUP_LABEL, replicaSetName).list().items
    }
  }

  ReplicaSet getReplicaSet(String namespace, String serverGroupName) {
    exceptionWrapper("replicaSets.get", "Get Replica Set $serverGroupName", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  ReplicaSet resizeReplicaSet(String namespace, String name, int size) {
    exceptionWrapper("replicaSets.scale", "Resize Replica Set $name to $size", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).withName(name).scale(size)
    }
  }

  ReplicaSet createReplicaSet(String namespace, ReplicaSet replicaSet) {
    exceptionWrapper("replicaSets.create", "Create Replica Set ${replicaSet?.metadata?.name}", namespace) {
      client.extensions().replicaSets().inNamespace(namespace).create(replicaSet)
    }
  }

  List<Pod> getJobPods(String namespace, String jobName) {
    exceptionWrapper("pods.list", "Get JobStatus Pods for $jobName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.JOB_LABEL, jobName).list().items
    }
  }

  Pod getPod(String namespace, String name) {
    exceptionWrapper("pods.get", "Get Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).get()
    }
  }

  List<Pod> getPods(String namespace, Map<String, String> labels) {
    exceptionWrapper("pods.list", "Get Pods matching $labels", namespace) {
      client.pods().inNamespace(namespace).withLabels(labels).list().items
    }
  }

  boolean deletePod(String namespace, String name) {
    exceptionWrapper("pods.delete", "Delete Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Pod> getPods(String namespace) {
    exceptionWrapper("pods.list", "Get Pods", namespace) {
      client.pods().inNamespace(namespace).list().items
    }
  }

  String getLog(String namespace, String name, String containerId) {
    exceptionWrapper("pod.logs", "Get Logs $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).inContainer(containerId).getLog()
    }
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    exceptionWrapper("replicationControllers.list", "Get Replication Controllers", namespace) {
      client.replicationControllers().inNamespace(namespace).list().items
    }
  }

  List<Pod> getReplicationControllerPods(String namespace, String replicationControllerName) {
    exceptionWrapper("pods.list", "Get Replication Controller Pods for $replicationControllerName", namespace) {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.SERVER_GROUP_LABEL, replicationControllerName).list().items
    }
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    exceptionWrapper("replicationControllers.get", "Get Replication Controller $serverGroupName", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
    }
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    exceptionWrapper("replicationControllers.create", "Create Replication Controller ${replicationController?.metadata?.name}", namespace) {
      client.replicationControllers().inNamespace(namespace).create(replicationController)
    }
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    exceptionWrapper("replicationControllers.scale", "Resize Replication Controller $name to $size", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
    }
  }

  boolean hardDestroyReplicationController(String namespace, String name) {
    exceptionWrapper("replicationControllers.delete", "Hard Destroy Replication Controller $name", namespace) {
      client.replicationControllers().inNamespace(namespace).withName(name).delete()
    }
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("pods.edit", "Toggle Pod Labels to $value for $name", namespace) {
      def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().done()
    }
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("replicationControllers.edit", "Toggle Replication Controller Labels to $value for $name", namespace) {
      def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    }
  }

  ReplicaSet toggleReplicaSetSpecLabels(String namespace, String name, List<String> keys, String value) {
    exceptionWrapper("replicaSets.edit", "Toggle Replica Set Labels to $value for $name", namespace) {
      def edit = client.extensions().replicaSets().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    }
  }

  Service getService(String namespace, String service) {
    exceptionWrapper("services.get", "Get Service $service", namespace) {
      client.services().inNamespace(namespace).withName(service).get()
    }
  }

  Service createService(String namespace, Service service) {
    exceptionWrapper("services.create", "Create Service $service", namespace) {
      client.services().inNamespace(namespace).create(service)
    }
  }

  boolean deleteService(String namespace, String name) {
    exceptionWrapper("services.delete","Delete Service $name", namespace) {
      client.services().inNamespace(namespace).withName(name).delete()
    }
  }

  List<Service> getServices(String namespace) {
    exceptionWrapper("services.list", "Get Services", namespace) {
      client.services().inNamespace(namespace).list().items
    }
  }

  Service replaceService(String namespace, String name, Service service) {
    exceptionWrapper("services.replace", "Replace Service $name", namespace) {
      client.services().inNamespace(namespace).withName(name).replace(service)
    }
  }

  Secret getSecret(String namespace, String secret) {
    exceptionWrapper("secrets.get", "Get Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).withName(secret).get()
    }
  }

  DoneableSecret editSecret(String namespace, String secret) {
    exceptionWrapper("secrets.edit", "Edit Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).withName(secret).edit()
    }
  }

  Secret createSecret(String namespace, Secret secret) {
    exceptionWrapper("secrets.create", "Create Secret $secret", namespace) {
      client.secrets().inNamespace(namespace).create(secret)
    }
  }

  List<Secret> getSecrets(String namespace) {
    exceptionWrapper("secrets.list", "Get Secrets", namespace) {
      client.secrets().inNamespace(namespace).list().items
    }
  }

  List<ServiceAccount> getServiceAccounts(String namespace) {
    exceptionWrapper("serviceAccounts.list", "Get Service Accounts", namespace) {
      client.serviceAccounts().inNamespace(namespace).list().items
    }
  }

  List<ConfigMap> getConfigMaps(String namespace) {
   exceptionWrapper("configMaps.list", "Get Config Maps", namespace) {
      client.configMaps().inNamespace(namespace).list().items
    }
  }

  Namespace getNamespace(String namespace) {
    exceptionWrapper("namespaces.get", "Get Namespace $namespace", null) {
      client.namespaces().withName(namespace).get()
    }
  }

  List<Namespace> getNamespaces() {
    exceptionWrapper("namespaces.list", "Get Namespaces", null) {
      client.namespaces().list().items
    }
  }

  List<String> getNamespacesByName() {
    exceptionWrapper("namespaces.list", "Get Namespaces", null) {
      client.namespaces().list().items.collect {
        it.metadata.name
      }
    }
  }

  Namespace createNamespace(Namespace namespace) {
    exceptionWrapper("namespaces.create", "Create Namespace $namespace", null) {
      client.namespaces().create(namespace)
    }
  }

  Pod createPod(String namespace, Pod pod) {
    exceptionWrapper("pods.create", "Create Pod ${pod?.metadata?.name}", namespace) {
      client.pods().inNamespace(namespace).create(pod)
    }
  }

  List<Job> getJobs(String namespace) {
    exceptionWrapper("jobs.list", "Get Jobs", namespace) {
      client.extensions().jobs().inNamespace(namespace).list().items
    }
  }

  Job getJob(String namespace, String name) {
    exceptionWrapper("jobs.get", "Get JobStatus $name", namespace) {
      client.extensions().jobs().inNamespace(namespace).withName(name).get()
    }
  }

  boolean hardDestroyPod(String namespace, String name) {
    exceptionWrapper("pods.delete", "Hard Destroy Pod $name", namespace) {
      client.pods().inNamespace(namespace).withName(name).delete()
    }
  }

  HorizontalPodAutoscaler createAutoscaler(String namespace, HorizontalPodAutoscaler autoscaler) {
    exceptionWrapper("horizontalPodAutoscalers.create", "Create Autoscaler ${autoscaler?.metadata?.name}", namespace) {
      client.autoscaling().horizontalPodAutoscalers().inNamespace(namespace).create(autoscaler)
    }
  }

  DoneableHorizontalPodAutoscaler editAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.edit", "Edit Autoscaler $name", namespace) {
      client.autoscaling().horizontalPodAutoscalers().inNamespace(namespace).withName(name).edit()
    }
  }

  HorizontalPodAutoscaler getAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.get", "Get Autoscaler $name", namespace) {
      client.autoscaling().horizontalPodAutoscalers().inNamespace(namespace).withName(name).get()
    }
  }

  Map<String, HorizontalPodAutoscaler> getAutoscalers(String namespace, String kind) {
    exceptionWrapper("horizontalPodAutoscalers.list", "Get Autoscalers", namespace) {
      def items = client.autoscaling().horizontalPodAutoscalers().inNamespace(namespace).list().items ?: []
      items.collectEntries { def autoscaler ->
        autoscaler.spec.scaleTargetRef.kind == kind ? [(autoscaler.metadata.name): autoscaler] : [:]
      }
    }
  }

  boolean deleteAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.delete", "Destroy Autoscaler $name", namespace) {
      client.autoscaling().horizontalPodAutoscalers().inNamespace(namespace).withName(name).delete()
    }
  }

  Deployment getDeployment(String namespace, String name) {
    exceptionWrapper("deployments.get", "Get Deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).get()
    }
  }

  List<Deployment> getDeployments(String namespace) {
    exceptionWrapper("deployments.list", "Get Deployments", namespace) {
      client.extensions().deployments().inNamespace(namespace).list().items
    }
  }

  Deployment resizeDeployment(String namespace, String name, int size) {
    exceptionWrapper("deployments.scale", "Resize Deployment $name to $size", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).scale(size)
    }
  }

  Deployment createDeployment(String namespace, Deployment deployment) {
    exceptionWrapper("deployments.create", "Create Deployment $deployment.metadata.name", namespace) {
      client.extensions().deployments().inNamespace(namespace).create(deployment)
    }
  }

  DoneableDeployment editDeployment(String namespace, String name) {
    exceptionWrapper("deployments.edit", "Edit deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).edit()
    }
  }

  ReplicaSet annotateReplicaSet(String namespace, String name, String key, String value) {
    exceptionWrapper("replicaSets.annotate", "Annotate replica set $name", namespace) {
      def rs = client.extensions().replicaSets().inNamespace(namespace).withName(name).cascading(false).edit()
      return rs.editMetadata().addToAnnotations(key, value).endMetadata().done()
    }
  }

  ReplicationController annotateReplicationController(String namespace, String name, String key, String value) {
    exceptionWrapper("replicationControllers.annotate", "Annotate replication controller $name", namespace) {
      def rc = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit()
      return rc.editMetadata().addToAnnotations(key, value).endMetadata().done()
    }
  }

  boolean deleteDeployment(String namespace, String name) {
    exceptionWrapper("deployments.delete", "Delete Deployment $name", namespace) {
      client.extensions().deployments().inNamespace(namespace).withName(name).delete()
    }
  }

  static boolean hasDeployment(ReplicaSet replicaSet) {
    return replicaSet?.metadata?.annotations?.any { k, v -> k.startsWith(DEPLOYMENT_ANNOTATION) }
  }

  static String getDeploymentRevision(Deployment deployment) {
    return deployment?.metadata?.annotations?.get("$DEPLOYMENT_ANNOTATION/revision".toString())
  }

  static String getDeploymentRevision(ReplicaSet replicaSet) {
    return replicaSet?.metadata?.annotations?.get("$DEPLOYMENT_ANNOTATION/revision".toString())
  }
}
