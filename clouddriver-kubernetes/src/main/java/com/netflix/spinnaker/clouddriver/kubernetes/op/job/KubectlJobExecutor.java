/*
 * Copyright 2023 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.util.List;
import javax.annotation.Nonnull;

public interface KubectlJobExecutor {
  String logs(
      KubernetesCredentials credentials, String namespace, String podName, String containerName);

  String jobLogs(
      KubernetesCredentials credentials, String namespace, String jobName, String containerName);

  Void scale(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int replicas,
      Task task,
      String opName);

  List<String> delete(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesSelectorList labelSelectors,
      V1DeleteOptions deleteOptions,
      Task task,
      String opName);

  ImmutableList<KubernetesPodMetric> topPod(
      KubernetesCredentials credentials, String namespace, @Nonnull String pod);

  KubernetesManifest deploy(
      KubernetesCredentials credentials, KubernetesManifest manifest, Task task, String opName);

  KubernetesManifest replace(
      KubernetesCredentials credentials, KubernetesManifest manifest, Task task, String opName);

  KubernetesManifest create(
      KubernetesCredentials credentials, KubernetesManifest manifest, Task task, String opName);

  List<Integer> historyRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name);

  Void undoRollout(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int revision);

  Void pauseRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name);

  Void resumeRollout(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      Task task,
      String opName);

  Void rollingRestart(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      Task task,
      String opName);

  Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      List<JsonPatch> patches,
      Task task,
      String opName);

  Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest,
      Task task,
      String opName);

  ImmutableList<KubernetesManifest> list(
      KubernetesCredentials credentials,
      List<KubernetesKind> kinds,
      String namespace,
      KubernetesSelectorList selectors);

  KubernetesManifest get(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name);

  ImmutableList<KubernetesManifest> eventsFor(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name);
}
