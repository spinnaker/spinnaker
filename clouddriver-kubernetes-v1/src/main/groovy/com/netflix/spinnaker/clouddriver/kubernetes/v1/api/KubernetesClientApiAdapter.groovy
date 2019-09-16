/*
 * Copyright 2017 Cisco, Inc.
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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.diff.JsonDiff
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesApiClientConfig
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesClientOperationException
import groovy.util.logging.Slf4j
import io.kubernetes.client.ApiClient
import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.AppsV1beta1Api
import io.kubernetes.client.apis.AutoscalingV1Api
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.apis.ExtensionsV1beta1Api
import io.kubernetes.client.models.V1DeleteOptions
import io.kubernetes.client.models.V1HorizontalPodAutoscaler
import io.kubernetes.client.models.V1NamespaceList
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.models.V1PodList
import io.kubernetes.client.models.V1Status
import io.kubernetes.client.models.V1beta1DaemonSet
import io.kubernetes.client.models.V1beta1DaemonSetList
import io.kubernetes.client.models.V1beta1StatefulSet
import io.kubernetes.client.models.V1beta1StatefulSetList

import java.util.concurrent.TimeUnit

@Slf4j
class KubernetesClientApiAdapter {

  String account

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100
  static final int API_CALL_TIMEOUT_SECONDS = 60
  static final int TERMINATION_GRACE_PERIOD_SECONDS = 30
  private final ObjectMapper mapper = new ObjectMapper();
  final Registry spectatorRegistry
  final Clock spectatorClock
  final ApiClient client
  final AppsV1beta1Api apiInstance
  final ExtensionsV1beta1Api extApi
  final AutoscalingV1Api scalerApi
  final CoreV1Api coreApi

  public spectatorRegistry() { return spectatorRegistry }

  KubernetesClientApiAdapter(String account, KubernetesApiClientConfig config, Registry spectatorRegistry) {
    if (!config) {
      throw new IllegalArgumentException("Config may not be null.")
    }

    this.account = account
    this.spectatorRegistry = spectatorRegistry
    this.spectatorClock = spectatorRegistry.clock()

    client = config.getApiCient()
    Configuration.setDefaultApiClient(client)
    apiInstance = new AppsV1beta1Api()
    extApi = new ExtensionsV1beta1Api()
    scalerApi = new AutoscalingV1Api()
    coreApi = new CoreV1Api()
  }

  KubernetesClientOperationException formatException(String operation, String namespace, ApiException e) {
    account ? new KubernetesClientOperationException(account, "$operation in $namespace", e) :
      new KubernetesClientOperationException("$operation in $namespace", e)
  }

  KubernetesClientOperationException formatException(String operation, ApiException e) {
    account ? new KubernetesClientOperationException(account, "$operation", e) :
      new KubernetesClientOperationException("$operation", e)
  }

  Boolean blockUntilResourceConsistent(Closure getResource) {
    Boolean isPodRunning = getResource()

    def wait = RETRY_INITIAL_WAIT_MILLIS
    def attempts = 0
    while (!isPodRunning ) {
      attempts += 1
      if (attempts > RETRY_COUNT) {
        return false
      }

      sleep(wait)
      wait = [wait * 2, RETRY_MAX_WAIT_MILLIS].min()

      isPodRunning = getResource()
    }

    return true
  }

  private <T> T exceptionWrapper(String methodName, String operationMessage, String namespace, Closure<T> doOperation) {
    T result = null
    Exception failure
    long startTime = spectatorClock.monotonicTime()

    try {
      result = doOperation()
    } catch (ApiException e) {
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

  List<V1beta1StatefulSet> getStatefulSets(String namespace) {
    exceptionWrapper("statefulSets.list", "Get Stateful Sets", namespace) {
      try {
        V1beta1StatefulSetList list = apiInstance.listNamespacedStatefulSet(namespace, true, null, null, null, null, null, null, API_CALL_TIMEOUT_SECONDS, false);
        String apiVersion = list.getApiVersion();
        for (V1beta1StatefulSet item : list.getItems()) {
          item.setApiVersion(apiVersion);
          item.setKind("StatefulSet");
        }

        return list.items
      } catch(Exception e){
        log.debug(e.message.toString())
      }
    }
  }

  List<V1beta1DaemonSet> getDaemonSets(String namespace) {
    exceptionWrapper("daemonSets.list", "Get Daemon Sets", namespace) {
      try {
        V1beta1DaemonSetList list = extApi.listNamespacedDaemonSet(namespace, null, null, null, null, null, null, null, API_CALL_TIMEOUT_SECONDS, false)
        String apiVersion = list.getApiVersion();
        for (V1beta1DaemonSet item : list.getItems()) {
          item.setApiVersion(apiVersion);
          item.setKind("DaemonSet");
        }

        return list.items
      } catch (ApiException e) {
        log.debug(e.message.toString())
      }
    }
  }

  V1beta1StatefulSet createStatefulSet(String namespace, V1beta1StatefulSet statefulSet) {
    exceptionWrapper("statefulSets.create", "Create Stateful Set ${statefulSet?.metadata?.name}", namespace) {
      return apiInstance.createNamespacedStatefulSet(namespace, statefulSet, false, null, null)
    }
  }

  V1beta1StatefulSet replaceStatfulSet(String name, String namespace, V1beta1StatefulSet statefulSet) {
    exceptionWrapper("statefulSets.replace", "Replace Stateful Set ${name}", namespace) {
      def deployedControllerSet = getStatefulSet(name, namespace)
      deployedControllerSet.spec.replicas = statefulSet.spec.replicas
      deployedControllerSet.spec.template = statefulSet.spec.template
      deployedControllerSet.spec.updateStrategy = statefulSet.spec.updateStrategy

      return apiInstance.replaceNamespacedStatefulSet(name, namespace, deployedControllerSet,  null, null)
    }
  }

  private Map[] determineJsonPatch(Object current, Object desired) {
    JsonNode desiredNode = mapper.convertValue(desired, JsonNode.class);
    JsonNode currentNode = mapper.convertValue(current, JsonNode.class);

    return mapper.convertValue(JsonDiff.asJson(currentNode, desiredNode), Map[].class);
  }

  V1beta1StatefulSet resizeStatefulSet(String name, String namespace, int targetSize) {
    exceptionWrapper("statefulSets.resize", "Resize Stateful Set $name", namespace) {
      V1beta1StatefulSet current = getStatefulSet(name, namespace)
      V1beta1StatefulSet desired = getStatefulSet(name, namespace)
      desired.spec.replicas = targetSize

      final Map[] jsonPatch = determineJsonPatch(current, desired);
      V1beta1StatefulSet statefulSet = apiInstance.patchNamespacedStatefulSet(name, namespace, jsonPatch, null, null)

      return statefulSet
    }
  }

  void hardDestroyStatefulSet(String name, String namespace, V1DeleteOptions deleteOptions, Boolean orphanDependents, String propagationPolicy) {
    exceptionWrapper("statefulSets.delete", "Delete Stateful Set $name", namespace) {
      V1beta1StatefulSet statefulSet = getStatefulSet(name, namespace)
      resizeStatefulSet(name, namespace, 0)

      getPods(namespace, statefulSet.metadata.labels).items.forEach({ item ->
        deletePod(item.metadata.name, namespace, null, null, null, true)
      })

      try {
        apiInstance.deleteNamespacedStatefulSet(name, namespace, null, deleteOptions ?: new V1DeleteOptions(), null, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, propagationPolicy)
      } catch (Exception e) {
        log.debug(e.message)
      }
    }
  }

  V1HorizontalPodAutoscaler getAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.get", "Get Autoscaler $name", namespace) {
      V1HorizontalPodAutoscaler result = null

      try {
        result = scalerApi.readNamespacedHorizontalPodAutoscalerStatus(name, namespace, null)
      } catch (Exception ex) {
        log.info "Unable to find autoscaler {$name in $namespace}: $ex."
      }

      return result
    }
  }

  V1HorizontalPodAutoscaler createAutoscaler(String namespace, V1HorizontalPodAutoscaler autoscaler) {
    exceptionWrapper("horizontalPodAutoscalers.create", "Create Autoscaler ${autoscaler?.metadata?.name}", namespace) {
      return scalerApi.createNamespacedHorizontalPodAutoscaler(namespace, autoscaler, false, null, null)
    }
  }

  V1beta1StatefulSet getStatefulSet(String statefulSetName, String namespace) {
    exceptionWrapper("statefulSets.create", "Get Stateful Set ${statefulSetName}", namespace) {
      try {
        return apiInstance.readNamespacedStatefulSet(statefulSetName, namespace, null, false, false)
      } catch (Exception e) {
        log.debug(e.message)
        return null
      }
    }
  }

  V1PodList getPods(String namespace, Map<String, String> labels) {
    exceptionWrapper("pods.list", "Get Pods matching $labels", namespace) {
      String label
      if (labels != null) {
        Map.Entry<String, String> entry = labels.entrySet().iterator().next()
        String key = entry.getKey()
        String value = entry.getValue()
        label = key + "=" + value
      }
      coreApi.listNamespacedPod(namespace, false, null, null, null, label, null, null, API_CALL_TIMEOUT_SECONDS,false)
    }
  }

  boolean deleteAutoscaler(String namespace, String name, V1DeleteOptions deleteOptions, Boolean orphanDependents, String propagationPolicy)  {
    exceptionWrapper("horizontalPodAutoscalers.delete", "Destroy Autoscaler $name", namespace) {
      return scalerApi.deleteNamespacedHorizontalPodAutoscaler(name, namespace, null, deleteOptions, null, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, propagationPolicy);
    }
  }

  V1beta1DaemonSet createDaemonSet(String namespace, V1beta1DaemonSet daemonSet) {
    exceptionWrapper("DaemonSet.create", "Create Daemon Set ${daemonSet?.metadata?.name}", namespace) {
      return extApi.createNamespacedDaemonSet(namespace, daemonSet, false, null, null)
    }
  }

  V1beta1DaemonSet replaceDaemonSet(String name, String namespace, V1beta1DaemonSet daemonSet) {
    exceptionWrapper("DaemonSet.replace", "Replace Daemon Set ${name}", namespace) {
      def deployedControllerSet = getDaemonSet(name, namespace)
      deployedControllerSet.spec.template = daemonSet.spec.template
      deployedControllerSet.spec.updateStrategy = daemonSet.spec.updateStrategy

      return extApi.replaceNamespacedDaemonSet(name, namespace, deployedControllerSet, null, null)
    }
  }

  V1beta1DaemonSet getDaemonSet(String name, String namespace) {
    exceptionWrapper("DaemonSet.get", "Get Daemon Set ${name}", namespace) {
      try {
        return extApi.readNamespacedDaemonSet(name, namespace, null, true, false)
      } catch (Exception e) {
        log.debug(e.message)
        return null
      }
    }
  }

  void hardDestroyDaemonSet(String name, String namespace, V1DeleteOptions deleteoptions, Boolean orphanDependents, String propagationPolicy) {
    exceptionWrapper("daemonSets.delete", "Hard Destroy Daemon Set ${name}", namespace) {
      def deployedControllerSet = getDaemonSet(name, namespace)

      V1Status status
      try {
        status = extApi.deleteNamespacedDaemonSet(name, namespace, null, deleteoptions ?: new V1DeleteOptions(), null, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, propagationPolicy);
      } catch (Exception e) {
        log.debug(e.message)
      }

      getPods(namespace, deployedControllerSet.metadata.labels).items.forEach({ item ->
        deletePod(item.metadata.name, namespace, null, null, null, true)
      })
    }
  }

  List<String> getNamespacesByName() {
    exceptionWrapper("namespaces.list", "Get Namespaces", null) {
      V1NamespaceList result = coreApi.listNamespace(false, null, null, null, null, null, null, 30, false)
      return result.items.collect { n -> n.getMetadata().getName() }
    }
  }

  void deletePod(String name, String namespace, V1DeleteOptions deleteOptions, Boolean orphanDependents, String propagationPolicy, Boolean force) {
    exceptionWrapper("pod.delete", "Destroy Pod ${name}", namespace) {
      V1Status status
      try {
        if (force) {
          deleteOptions = new V1DeleteOptions()
          deleteOptions.kind = "DeleteOptions"
          deleteOptions.apiVersion "v1"
          deleteOptions.gracePeriodSeconds = 0
        }

        status = coreApi.deleteNamespacedPod(name, namespace, null, deleteOptions ?: new V1DeleteOptions(), null, TERMINATION_GRACE_PERIOD_SECONDS, null, null)
      } catch (Exception e) {
        log.debug(e.message)
      }
    }
  }

  V1Pod getPodStatus(String name, String namespace) {
    exceptionWrapper("pods.status", "Get pod status ${name}", namespace) {
      V1Pod pod
      try {
        pod = coreApi.readNamespacedPodStatus(name, namespace, null)
      } catch (Exception e) {
        log.debug(e.message)
      }
    }
  }

  String getControllerKind(String name, String namespace, String controllerKind) {
    exceptionWrapper("getControllerType", "Get controller type ${name}", namespace) {
      def deployedControllerSet
      if (controllerKind == KubernetesUtil.CONTROLLERS_STATEFULSET_KIND) {
        deployedControllerSet = getStatefulSet(name, namespace)
      } else if (controllerKind == KubernetesUtil.CONTROLLERS_DAEMONSET_KIND) {
        deployedControllerSet = getDaemonSet(name, namespace)
      } else {
        deployedControllerSet = getStatefulSet(name, namespace)
        if (!deployedControllerSet) {
          deployedControllerSet = getDaemonSet(name, namespace)
        }
      }

      return deployedControllerSet?.kind
    }
  }
}

