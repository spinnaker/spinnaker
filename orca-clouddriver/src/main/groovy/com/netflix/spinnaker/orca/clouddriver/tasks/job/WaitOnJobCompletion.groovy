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

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoRestService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class WaitOnJobCompletion extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  long timeout = TimeUnit.DAYS.toMillis(1)

  @Autowired
  KatoRestService katoRestService

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

    Map<String, Object> outputs = [:]
    if (jobs.size() > 1) {
      throw new IllegalStateException("At most one job location can be specified at a time.")
    }

    jobs.each { location, names ->
      if (!names) {
        return
      }

      if (names.size() > 1) {
        throw new IllegalStateException("At most one job can be run and monitored at a time.")
      }

      def name = names[0]
      def parsedName = Names.parseName(name)

      Map job = objectMapper.readValue(katoRestService.collectJob(parsedName.app, account, location, name, "delete").body.in(), new TypeReference<Map>() {})
      outputs.jobStatus = job

      switch ((String) job.jobState) {
        case "Succeeded":
          status = ExecutionStatus.SUCCEEDED
          outputs.completionDetails = job.completionDetails

          if (stage.context.propertyFile) {
            Map<String, Object> properties = [:]
            properties = katoRestService.getFileContents(parsedName.app, account, location, name, stage.context.propertyFile)
            if (properties.size() == 0) {
              throw new IllegalStateException("expected properties file ${stage.context.propertyFile} but one was not found or was empty")
            }
            outputs << properties
            outputs.propertyFileContents = properties
          }

          return

        case "Failed":
          status = ExecutionStatus.TERMINAL
          outputs.completionDetails = job.completionDetails
          return
      }
    }

    new TaskResult(status, outputs, outputs)
  }
}
