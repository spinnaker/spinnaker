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
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException

import java.util.concurrent.TimeUnit

@Slf4j
class KubernetesApiAdaptor {
  KubernetesClient client
  String account

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100

  KubernetesApiAdaptor(String account, KubernetesClient client) {
    if (!client) {
      throw new IllegalArgumentException("Client may not be null.")
    }
    this.client = client
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

  Ingress createIngress(String namespace, Ingress ingress) {
    try {
      client.extensions().ingress().inNamespace(namespace).create(ingress)
    } catch (KubernetesClientException e) {
      throw formatException("Create Ingress ${ingress?.metadata?.name}", namespace, e)
    }
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    try {
      client.extensions().ingress().inNamespace(namespace).withName(name).replace(ingress)
    } catch (KubernetesClientException e) {
      throw formatException("Replace Ingress $name", namespace, e)
    }
  }

  Ingress getIngress(String namespace, String name) {
    try {
      client.extensions().ingress().inNamespace(namespace).withName(name).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Ingress $name", namespace, e)
    }
  }

  boolean deleteIngress(String namespace, String name) {
    try {
      client.extensions().ingress().inNamespace(namespace).withName(name).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Delete Ingress $name", namespace, e)
    }
  }

  List<Ingress> getIngresses(String namespace) {
    try {
      client.extensions().ingress().inNamespace(namespace).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Ingresses", namespace, e)
    }
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    try {
      client.replicationControllers().inNamespace(namespace).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Replication Controllers", namespace, e)
    }
  }

  List<Pod> getReplicationControllerPods(String namespace, String replicationControllerName) {
    try {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Replication Controller Pods for $replicationControllerName", namespace, e)
    }
  }

  List<Pod> getJobPods(String namespace, String jobName) {
    try {
      client.pods().inNamespace(namespace).withLabel(KubernetesUtil.JOB_LABEL, jobName).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Job Pods for $jobName", namespace, e)
    }
  }

  Pod getPod(String namespace, String name) {
    try {
      client.pods().inNamespace(namespace).withName(name).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Pod $name", namespace, e)
    }
  }

  boolean deletePod(String namespace, String name) {
    try {
      client.pods().inNamespace(namespace).withName(name).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Delete Pod $name", namespace, e)
    }
  }

  List<Pod> getPods(String namespace) {
    try {
      client.pods().inNamespace(namespace).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Pods", namespace, e)
    }
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    try {
      client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Replication Controller $serverGroupName", namespace, e)
    }
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    try {
      client.replicationControllers().inNamespace(namespace).create(replicationController)
    } catch (KubernetesClientException e) {
      throw formatException("Create Replication Controller ${replicationController?.metadata?.name}", namespace, e)
    }
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    try {
      client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
    } catch (KubernetesClientException e) {
      throw formatException("Resize Replication Controller $name to $size", namespace, e)
    }
  }

  boolean hardDestroyReplicationController(String namespace, String name) {
    try {
      client.replicationControllers().inNamespace(namespace).withName(name).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Hard Destroy Replication Controller $name", namespace, e)
    }
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    try {
      def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().done()
    } catch (KubernetesClientException e) {
      throw formatException("Toggle Pod Labels to $value for $name", namespace, e)
    }
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    try {
      def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

      keys.each {
        edit.removeFromLabels(it.toString())
        edit.addToLabels(it.toString(), value.toString())
      }

      edit.endMetadata().endTemplate().endSpec().done()
    } catch (KubernetesClientException e) {
      throw formatException("Toggle Replication Controller Labels to $value for $name", namespace, e)
    }
  }

  Service getService(String namespace, String service) {
    try {
      client.services().inNamespace(namespace).withName(service).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Service $service", namespace, e)
    }
  }

  Service createService(String namespace, Service service) {
    try {
      client.services().inNamespace(namespace).create(service)
    } catch (KubernetesClientException e) {
      throw formatException("Create Service $service", namespace, e)
    }
  }

  boolean deleteService(String namespace, String name) {
    try {
      client.services().inNamespace(namespace).withName(name).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Delete Service $name", namespace, e)
    }
  }

  List<Service> getServices(String namespace) {
    try {
      client.services().inNamespace(namespace).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Services", namespace, e)
    }
  }

  Service replaceService(String namespace, String name, Service service) {
    try {
      client.services().inNamespace(namespace).withName(name).replace(service)
    } catch (KubernetesClientException e) {
      throw formatException("Replace Service $name", namespace, e)
    }
  }

  Secret getSecret(String namespace, String secret) {
    try {
      client.secrets().inNamespace(namespace).withName(secret).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Secret $secret", namespace, e)
    }
  }

  Boolean deleteSecret(String namespace, String secret) {
    try {
      client.secrets().inNamespace(namespace).withName(secret).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Delete Secret $secret", namespace, e)
    }
  }

  Secret createSecret(String namespace, Secret secret) {
    try {
      client.secrets().inNamespace(namespace).create(secret)
    } catch (KubernetesClientException e) {
      throw formatException("Create Secret $secret", namespace, e)
    }
  }

  Namespace getNamespace(String namespace) {
    try {
      client.namespaces().withName(namespace).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Namespace $namespace", e)
    }
  }

  Namespace createNamespace(Namespace namespace) {
    try {
      client.namespaces().create(namespace)
    } catch (KubernetesClientException e) {
      throw formatException("Create Namespace $namespace", e)
    }
  }

  Job createJob(String namespace, Job job) {
    try {
      client.extensions().jobs().inNamespace(namespace).create(job)
    } catch (KubernetesClientException e) {
      throw formatException("Create Job ${job?.metadata?.name}", namespace, e)
    }
  }

  List<Job> getJobs(String namespace) {
    try {
      client.extensions().jobs().inNamespace(namespace).list().items
    } catch (KubernetesClientException e) {
      throw formatException("Get Jobs", namespace, e)
    }
  }

  Job getJob(String namespace, String name) {
    try {
      client.extensions().jobs().inNamespace(namespace).withName(name).get()
    } catch (KubernetesClientException e) {
      throw formatException("Get Job $name", namespace, e)
    }
  }

  boolean hardDestroyJob(String namespace, String name) {
    try {
      client.extensions().jobs().inNamespace(namespace).withName(name).delete()
    } catch (KubernetesClientException e) {
      throw formatException("Hard Destroy Job $name", namespace, e)
    }
  }
}
