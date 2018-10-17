/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDisableManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestTraffic;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanLoadBalance;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.HasPods;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class KubernetesDisableManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesDisableManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final String accountName;
  private static final String OP_NAME = "DISABLE_KUBERNETES_MANIFEST";

  public KubernetesDisableManifestOperation(KubernetesDisableManifestDescription description, KubernetesResourcePropertyRegistry registry) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.accountName = description.getCredentials().getName();
    this.registry = registry;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public List<String> determineLoadBalancers(KubernetesManifest target) {
    getTask().updateStatus(OP_NAME, "Getting load balancer list to disable...");
    List<String> result = description.getLoadBalancers();
    if (result != null && !result.isEmpty()) {
      getTask().updateStatus(OP_NAME, "Using supplied list [" + String.join(", ", result) + "]");
    } else {
      KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(target);
      result = traffic.getLoadBalancers();
      getTask().updateStatus(OP_NAME, "Using annotated list [" + String.join(", ", result) + "]");
    }

    return result;
  }

  private void disable(String loadBalancerName, KubernetesManifest target) {
    Pair<KubernetesKind, String> name;
    try {
      name = KubernetesManifest.fromFullResourceName(loadBalancerName);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Load balancers must be specified in the form '<kind> <name>', e.g. 'service my-service'", e);
    }

    CanLoadBalance loadBalancerHandler = CanLoadBalance.lookupProperties(registry, accountName, name);
    KubernetesManifest loadBalancer = credentials.get(name.getLeft(), target.getNamespace(), name.getRight());

    List<JsonPatch> patch = loadBalancerHandler.detachPatch(loadBalancer, target);

    getTask().updateStatus(OP_NAME, "Patching target for '" + loadBalancerName + '"');
    credentials.patch(target.getKind(), target.getNamespace(), target.getName(), KubernetesPatchOptions.json(), patch);

    HasPods podHandler = null;
    try {
      podHandler = HasPods.lookupProperties(registry, accountName, target.getKind());
    } catch (IllegalArgumentException e) {
      // this is OK, the workload might not have pods
    }

    if (podHandler != null) {
      getTask().updateStatus(OP_NAME, "Patching pods for '" + loadBalancerName + '"');
      List<KubernetesManifest> pods = podHandler.pods(credentials, target);
      // todo(lwander) parallelize, this will get slow for large workloads
      for (KubernetesManifest pod : pods) {
        patch = loadBalancerHandler.detachPatch(loadBalancer, pod);
        credentials.patch(pod.getKind(), pod.getNamespace(), pod.getName(), KubernetesPatchOptions.json(), patch);
      }
    }
  }

  @Override
  public OperationResult operate(List priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting disable operation...");
    KubernetesCoordinates coordinates = description.getPointCoordinates();
    KubernetesManifest target = credentials.get(coordinates.getKind(), coordinates.getNamespace(), coordinates.getName());
    determineLoadBalancers(target).forEach(l -> disable(l, target));

    getTask().updateStatus(OP_NAME, "Disable operation for " + coordinates + " succeeded");
    return null;
  }
}
