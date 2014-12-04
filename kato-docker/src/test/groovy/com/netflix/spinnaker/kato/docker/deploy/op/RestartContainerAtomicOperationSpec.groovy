/*
 * Copyright 2014 Netflix, Inc.
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
 */

package com.netflix.spinnaker.kato.docker.deploy.op

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.docker.deploy.description.RestartContainerDescription
import com.netflix.spinnaker.kato.docker.deploy.op.RestartContainerAtomicOperation
import com.netflix.spinnaker.kato.docker.security.DockerAccountCredentials
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class RestartContainerAtomicOperationSpec extends Specification {

  def creds = new DockerAccountCredentials(name: "local", url: "http://myUrl")
  def description = new RestartContainerDescription(container: "abcd123", credentials: creds)
  @Subject
  RestartContainerAtomicOperation op = new RestartContainerAtomicOperation(description)
  @Shared
  RestTemplate restTemplate

  def setup() {
    TaskRepository.threadLocalTask.set(Stub(Task))
    restTemplate = Mock(RestTemplate)
    op.restTemplate = restTemplate
  }

  void "should call restart op container"() {
    when:
    op.operate([])

    then:
    1 * restTemplate.postForEntity("${creds.url}/containers/${description.container}/restart?t=${description.wait}".toString(), *_) >> {
      new ResponseEntity(HttpStatus.NO_CONTENT)
    }
  }

  void "should throw wrapped error when docker fails to restart the container"() {
    when:
    op.operate([])

    then:
    1 * restTemplate.postForEntity("${creds.url}/containers/${description.container}/restart?t=${description.wait}".toString(), *_) >> {
      new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    }
    thrown RestartContainerAtomicOperation.RestartContainerFailedException
  }

}
