/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
public class WaitOnJobCompletion extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  long timeout = TimeUnit.DAYS.toMillis(1)

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  static final String REFRESH_TYPE = "Job"

  @Override
  TaskResult execute(Stage stage) {
    String account = getCredentials(stage)
    Map<String, List<String>> jobs = stage.context."deploy.jobs"

    def status = ExecutionStatus.RUNNING;

    if (!jobs) {
      throw new IllegalStateException("No jobs in stage context.")
    }

    jobs.each { location, names ->
      if (!names) {
        return
      }

      Map job = objectMapper.readValue(oortService.getJob("*", account, location, names[0]).body.in(), new TypeReference<Map>() {})

      switch ((String)job.jobState) {
        case "Succeeded":
          status = ExecutionStatus.SUCCEEDED
          return

        case "Failed":
          status = ExecutionStatus.TERMINAL
          return
      }
    }

    new DefaultTaskResult(status)
  }
}
