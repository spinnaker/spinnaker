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

package com.netflix.spinnaker.kato.docker.deploy.handlers

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.docker.deploy.description.DockerDeployDescription
import com.netflix.spinnaker.kato.docker.model.Image
import com.netflix.spinnaker.kato.docker.security.DockerAccountCredentials
import com.netflix.spinnaker.kato.docker.services.RegistryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DockerDeployHandlerSpec extends Specification {

  @Shared
  RegistryService registryService

  @Shared
  RestTemplate restTemplate

  @Shared
  DockerAccountCredentials credentials = new DockerAccountCredentials(name: "local", url: "http://myUrl")

  @Subject handler = new DockerDeployHandler()

  def setup() {
    TaskRepository.threadLocalTask.set(Stub(Task))
    registryService = Mock(RegistryService)
    restTemplate = Mock(RestTemplate)
    handler.registryService = registryService
    handler.restTemplate = restTemplate
  }

  void "should create container, then start it"() {
    setup:
    def description = new DockerDeployDescription(application: "foo", version: "bar", credentials: credentials)

    when:
    handler.handle(description, [])

    then:
    1 * registryService.getImage(_, description.application, description.version) >> new Image(id: "1234")
    1 * restTemplate.exchange("${credentials.url}/containers/create".toString(), _, _, _, _) >> { String url, method, headers, resp, body ->
      assert body.Image == "1234"
      new ResponseEntity<>([Id: "abcd"], HttpStatus.CREATED)
    }
    1 * restTemplate.exchange("${credentials.url}/containers/abcd/start".toString(), *_) >> {
      new ResponseEntity<>(HttpStatus.NO_CONTENT)
    }
  }

  void "should throw an error if the image cannot be resolved"() {
    setup:
    def description = new DockerDeployDescription(application: "foo", version: "bar", credentials: credentials)

    when:
    handler.handle(description, [])

    then:
    1 * registryService.getImage(_, description.application, description.version)>> null
    thrown DockerDeployHandler.DockerDeployFailedException
  }
}
