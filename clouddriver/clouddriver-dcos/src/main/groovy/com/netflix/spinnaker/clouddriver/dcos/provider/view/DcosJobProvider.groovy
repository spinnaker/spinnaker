/*
 * Copyright 2017 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerJobId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosJobStatus
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.model.JobProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOSException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosJobProvider implements JobProvider<DcosJobStatus> {
  private static final LOGGER = LoggerFactory.getLogger(DcosJobProvider)
  private static final JOB_FRAMEWORK = "metronome"

  final String platform = DcosCloudProvider.ID

  private final DcosClientProvider dcosClientProvider
  private final ObjectMapper objectMapper
  private final AccountCredentialsProvider credentialsProvider

  @Autowired
  DcosJobProvider(DcosClientProvider dcosClientProvider, ObjectMapper objectMapper, AccountCredentialsProvider credentialsProvider) {
    this.dcosClientProvider = dcosClientProvider
    this.objectMapper = objectMapper
    this.credentialsProvider = credentialsProvider
  }

  @Override
  DcosJobStatus collectJob(String account, String location, String id) {
    def credentials = credentialsProvider.getCredentials(account)

    // Because of how the job endpoint within Clouddriver works (by looping through ALL providers for valid job
    // statuses), we want to protect against non-DCOS credentials and return null so that we don't break the job
    // endpoint by throwing an exception (which will return a 500 to the caller).
    if (!(credentials instanceof DcosAccountCredentials)) {
      return null
    }

    def dcosClient = dcosClientProvider.getDcosClient(credentials, location)
    def jobId = new DcosSpinnakerJobId(id)
    def jobResponse = dcosClient.getJob(jobId.jobName, ['activeRuns','history'])

    return new DcosJobStatus(jobResponse, jobId.taskName, account, location)
  }

  @Override
  Map<String, Object> getFileContents(String account, String location, String id, String fileName) {
    def credentials = credentialsProvider.getCredentials(account)

    // Similar to above of how the job endpoint within Clouddriver works (by looping through ALL providers for a valid
    // map), we want to protect against non-DCOS credentials and return an empty map so that we don't break the
    // job endpoint by throwing an exception (which will return a 500 to the caller).
    if (!(credentials instanceof DcosAccountCredentials)) {
      return null
    }

    def dcosClient = dcosClientProvider.getDcosClient(credentials, location)
    def jobId = new DcosSpinnakerJobId(id)
    def taskName = jobId.mesosTaskName
    def masterState = dcosClient.getMasterState()

    def metronomeFramework = masterState.getFrameworks().find {
      framework -> (JOB_FRAMEWORK == framework.getName())
    }

    def jobTask = metronomeFramework.getCompleted_tasks().find {
      task -> (taskName == task.getName())
    }

    def agentState = dcosClient.getAgentState(jobTask.getSlave_id())

    metronomeFramework = agentState.getCompleted_frameworks().find {
      framework -> (JOB_FRAMEWORK == framework.getName())
    }

    def jobExecutor = metronomeFramework.getCompleted_executors().find {
      executor -> (jobTask.getId() == executor.getId())
    }

    def filePath = "/var/lib/mesos/slave/slaves/${jobTask.getSlave_id()}/frameworks/${jobTask.getFramework_id()}/executors/${jobExecutor.getId()}/runs/${jobExecutor.getContainer()}/${fileName}".toString()

    try {
      def file = dcosClient.getAgentSandboxFileAsString(jobTask.getSlave_id(), filePath)

      if (!file.isPresent()) {
        return null
      }

      final contents = file.get()
      if (filePath.contains(".json")) {
        return objectMapper.readValue(contents, Map)
      }

      def properties = new Properties()

      properties.load(new ByteArrayInputStream(contents.getBytes()))

      return properties as Map<String, Object>
    } catch (DCOSException e) {
      if (e.status == 404) {
        LOGGER.warn("File [${fileName}] does not exist for job [${location}.${id}].")
        return null
      } else {
        throw e
      }
    }
  }

  @Override
  void cancelJob(String account, String location, String id) { }
}
