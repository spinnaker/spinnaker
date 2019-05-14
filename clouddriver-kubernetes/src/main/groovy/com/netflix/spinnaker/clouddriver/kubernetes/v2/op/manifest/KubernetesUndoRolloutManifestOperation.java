/*
 * Copyright 2017 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesUndoRolloutManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanUndoRollout;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class KubernetesUndoRolloutManifestOperation implements AtomicOperation<Void> {
  private final KubernetesUndoRolloutManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final String accountName;
  private static final String OP_NAME = "UNDO_ROLLOUT_KUBERNETES_MANIFEST";

  public KubernetesUndoRolloutManifestOperation(
      KubernetesUndoRolloutManifestDescription description,
      KubernetesResourcePropertyRegistry registry) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.accountName = description.getCredentials().getName();
    this.registry = registry;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting undo rollout operation...");
    KubernetesCoordinates coordinates = description.getPointCoordinates();

    getTask().updateStatus(OP_NAME, "Looking up resource properties...");
    KubernetesResourceProperties properties = registry.get(accountName, coordinates.getKind());
    KubernetesHandler deployer = properties.getHandler();

    if (!(deployer instanceof CanUndoRollout)) {
      throw new IllegalArgumentException(
          "Resource with " + coordinates + " does not support undo rollout");
    }

    CanUndoRollout canUndoRollout = (CanUndoRollout) deployer;

    Integer revision = description.getRevision();
    if (description.getNumRevisionsBack() != null) {
      getTask().updateStatus(OP_NAME, "Looking up rollout history...");
      List<Integer> revisions =
          canUndoRollout.historyRollout(
              credentials, coordinates.getNamespace(), coordinates.getName());

      revisions.sort(Integer::compareTo);
      int numRevisions = revisions.size();
      int targetRevisionIndex = numRevisions - description.getNumRevisionsBack() - 1;
      if (targetRevisionIndex < 0) {
        throw new IllegalArgumentException(
            "There are "
                + numRevisions
                + " revision(s) in total, cannot rollback "
                + description.getNumRevisionsBack());
      }

      revision = revisions.get(targetRevisionIndex);
      getTask().updateStatus(OP_NAME, "Picked revision " + revision + "...");
    }

    getTask().updateStatus(OP_NAME, "Calling undo rollout operation...");
    canUndoRollout.undoRollout(
        credentials, coordinates.getNamespace(), coordinates.getName(), revision);

    return null;
  }
}
