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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonSnapshotDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class DeleteAmazonSnapshotAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_SNAPSHOT";

  private final DeleteAmazonSnapshotDescription description;

  public DeleteAmazonSnapshotAtomicOperation(DeleteAmazonSnapshotDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired
  private AmazonClientProvider amazonClientProvider;

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, String.format("Initializing Delete Snapshot operation for %s", description));
    amazonClientProvider
      .getAmazonEC2(description.getCredentials(), description.getRegion())
      .deleteSnapshot(new DeleteSnapshotRequest().withSnapshotId(description.getSnapshotId()));

    getTask().updateStatus(BASE_PHASE, String.format("Deleted Snapshot %s in %s",
      description.getSnapshotId(), description.getRegion()));
    return null;
  }

}
