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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers.actions

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.titus.JobType
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.TitusJobNameResolver
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.titus.JobType.BATCH
import static com.netflix.spinnaker.clouddriver.titus.JobType.SERVICE

class TitusJobNameResolverSpec extends Specification {

  def setup() {
    Task task = Mock(Task) {
      getId() >> "taskid"
      getRequestId() >> "requestid"
    }
    TaskRepository.threadLocalTask.set(task)
  }

  @Unroll
  def "resolves job names"() {
    given:
    TitusClient titusClient = Mock() {
      findJobsByApplication(_) >> {
        [
          new Job(name: "spindemo-v001", submittedAt: new Date()),
          new Job(name: "spindemo-test-v001", submittedAt: new Date()),
          new Job(name: "spindemo-test-titus-v999", submittedAt: new Date()),
        ]
      }
    }
    SubmitJobRequest submitJobRequest = new SubmitJobRequest().with {
      it.jobType = description.jobType
      it
    }

    when:
    String result = TitusJobNameResolver.resolveJobName(titusClient, description, submitJobRequest)

    then:
    result == expected

    where:
    description                           || expected
    description(BATCH, "test", "free")    || "spindemo"
    description(SERVICE, "test", "free")  || "spindemo-test-free-v000"
    description(SERVICE, null, null)      || "spindemo-v002"
    description(SERVICE, "test", null)    || "spindemo-test-v002"
    description(SERVICE, "test", "titus") || "spindemo-test-titus-v000"
  }

  private static TitusDeployDescription description(JobType jobType, String stack, String freeFormDetails) {
    return description(jobType, stack, freeFormDetails, null)
  }

  private static TitusDeployDescription description(
    JobType jobType,
    String stack,
    String freeFormDetails,
    Integer sequence
  ) {
    return new TitusDeployDescription().with {
      it.jobType = jobType.value()
      it.application = "spindemo"
      it.stack = stack
      it.freeFormDetails = freeFormDetails
      it.region = "us-east-1"

      if (sequence != null) {
        it.sequence = sequence
      }

      it
    }
  }
}
