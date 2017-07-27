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
import com.netflix.spinnaker.clouddriver.model.JobProvider
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

  @Autowired
  DcosJobProvider(DcosClientProvider dcosClientProvider, ObjectMapper objectMapper) {
    this.dcosClientProvider = dcosClientProvider
    this.objectMapper = objectMapper
  }

  @Override
  DcosJobStatus collectJob(String account, String location, String id) {
    def dcosClient = dcosClientProvider.getDcosClient(account, location)
    def jobId = new DcosSpinnakerJobId(id)
    def jobResponse = dcosClient.getJob(jobId.jobName, ['activeRuns','history'])

    return new DcosJobStatus(jobResponse, jobId.taskName, account, location)
  }

  @Override
  Map<String, Object> getFileContents(String account, String location, String id, String fileName) {
    // Note - location is secretly the Job ID within DC/OS, this is so we don't have to do any parsing of the id field
    // give to this function but still have all the information we need to get a file if need be.
    def dcosClient = dcosClientProvider.getDcosClient(account, location)
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
        return [:]
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
        return [:]
      } else {
        throw e
      }
    }
  }
}
