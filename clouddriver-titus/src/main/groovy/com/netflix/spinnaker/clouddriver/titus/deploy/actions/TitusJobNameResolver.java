/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;

/** Helper class for resolving Titus job names. */
class TitusJobNameResolver {

  static String resolveJobName(
      TitusClient titusClient,
      TitusDeployDescription description,
      SubmitJobRequest submitJobRequest) {
    if (JobType.isEqual(submitJobRequest.getJobType(), JobType.BATCH)) {
      submitJobRequest.withJobName(description.getApplication());
      return description.getApplication();
    }

    String nextServerGroupName;
    TitusServerGroupNameResolver serverGroupNameResolver =
        new TitusServerGroupNameResolver(titusClient, description.getRegion());
    if (description.getSequence() != null) {
      nextServerGroupName =
          serverGroupNameResolver.generateServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              description.getSequence(),
              false);
    } else {
      nextServerGroupName =
          serverGroupNameResolver.resolveNextServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              false);
    }
    submitJobRequest.withJobName(nextServerGroupName);

    return nextServerGroupName;
  }
}
