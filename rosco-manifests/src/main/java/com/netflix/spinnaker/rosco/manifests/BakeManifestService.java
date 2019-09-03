/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public abstract class BakeManifestService<T extends BakeManifestRequest> {
  private final JobExecutor jobExecutor;

  public BakeManifestService(JobExecutor jobExecutor) {
    this.jobExecutor = jobExecutor;
  }

  public abstract Artifact bake(T bakeManifestRequest) throws IOException;

  public abstract Class<T> requestType();

  public abstract boolean handles(String type);

  protected byte[] doBake(BakeRecipe recipe) {
    BakeStatus bakeStatus = null;
    JobRequest jobRequest =
        new JobRequest(
            recipe.getCommand(),
            new ArrayList<>(),
            UUID.randomUUID().toString(),
            AuthenticatedRequest.getSpinnakerExecutionId().orElse(null),
            false);

    String jobId = jobExecutor.startJob(jobRequest);
    bakeStatus = jobExecutor.updateJob(jobId);
    while (bakeStatus == null || bakeStatus.getState() == BakeStatus.State.RUNNING) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ie) {
        jobExecutor.cancelJob(jobId);
        Thread.currentThread().interrupt();
      }
      bakeStatus = jobExecutor.updateJob(jobId);
    }
    if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
      throw new IllegalStateException("Bake failed: " + bakeStatus.getLogsContent());
    }
    return bakeStatus.getOutputContent().getBytes();
  }
}
