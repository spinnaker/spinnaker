/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonSnapshotDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteAmazonSnapshotAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_SNAPSHOT";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final DeleteAmazonSnapshotDescription description;
  private final Registry registry;
  private final Id deleteSnapshotTaskId;

  public DeleteAmazonSnapshotAtomicOperation(
      DeleteAmazonSnapshotDescription description, Registry registry) {
    this.description = description;
    this.registry = registry;
    this.deleteSnapshotTaskId = registry.createId("tasks.DeleteAmazonSnapshot");
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired private AmazonClientProvider amazonClientProvider;

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format("Initializing Delete Snapshot operation for %s", description));
    try {
      amazonClientProvider
          .getAmazonEC2(description.getCredentials(), description.getRegion())
          .deleteSnapshot(new DeleteSnapshotRequest().withSnapshotId(description.getSnapshotId()));
    } catch (Exception e) {
      registry.counter(deleteSnapshotTaskId.withTag("success", false)).increment();
      log.error(String.format("Failed to delete snapshotId %s", description.getSnapshotId()), e);
      throw e;
    }
    registry.counter(deleteSnapshotTaskId.withTag("success", true)).increment();

    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Deleted Snapshot %s in %s", description.getSnapshotId(), description.getRegion()));
    return null;
  }
}
