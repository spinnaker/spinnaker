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
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.docker.deploy.description.DockerDeployDescription
import com.netflix.spinnaker.kato.docker.security.Docker
import com.netflix.spinnaker.kato.docker.services.RegistryService
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Component
class DockerDeployHandler implements DeployHandler<DockerDeployDescription> {
  private static final String APP_ENV_KEY = "SPINNAKER_APP"
  private static final String STACK_ENV_KEY = "SPINNAKER_STACK"
  private static final String SEQ_ENV_VAR = "SPINNAKER_PUSH"
  private static final String BASE_PHASE = "DOCKER_DEPLOY"

  private static final HttpHeaders headers = new HttpHeaders() {
    {
      put "Content-type", ["application/json"]
    }
  }

  @Autowired
  RegistryService registryService

  @Autowired
  RestTemplate restTemplate

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof DockerDeployDescription
  }

  private static status(String msg) {
    task.updateStatus BASE_PHASE, msg
  }

  @Override
  DeploymentResult handle(DockerDeployDescription description, List priorOutputs) {
    status "Initializing Docker Deployment for ${description.application}/${description.version} to ${description.credentials.name}..."
    try {
      status "Looking up image id..."
      def image = registryService.getImage(description.credentials.credentials, description.application, description.version)
      if (!image) {
        throw new DockerDeployableNotFoundException("Couldn't find deployable for ${description.application} with version ${description.version} in registry!")
      } else {
        status "Captured image id: ${image.id}."
      }

      def ports = description.config.ports.collectEntries { [("${it.port}/${it.proto}"): [:]] }

      def containerConfig = [
        Hostname       : "",
        Domainname     : "",
        User           : "",
        Memory         : description.config.memory.size,
        MemorySwap     : description.config.memory.swap,
        CpuShares      : description.config.cpu.shares,
        Cpuset         : description.config.cpu.set,
        AttachStdin    : true,
        AttachStdout   : true,
        AttachStderr   : true,
        PortSpecs      : null,
        Tty            : false,
        OpenStdin      : false,
        StdinOnce      : false,
        Image          : image.id,
        NetworkDisabled: false
      ]

      def sequence = getAncestorContainer(description.application, description.stack, description.credentials.credentials)
      containerConfig.Env = ["${APP_ENV_KEY}=${description.application}".toString(),
                             "${SEQ_ENV_VAR}=${String.format("v%03d", sequence)}".toString()]
      if (description.stack) {
        containerConfig.Env << "${STACK_ENV_KEY}=${description.stack}".toString()
      }
      if (description.envVars) {
        containerConfig.Env.addAll(description.envVars.collect { k,v -> "$k=$v".toString() })
      }
      if (description.command) {
        containerConfig.Cmd = ['/bin/bash', '-c', description.command]
      }
      if (ports) {
        containerConfig.ExposedPorts = ports
      }

      def entity = new HttpEntity(containerConfig, headers)
      status "Executing deployment..."
      def response = restTemplate.exchange("${description.credentials.credentials.url}/containers/create", HttpMethod.POST, entity, Map, containerConfig)
      if (response.statusCode != HttpStatus.CREATED) {
        def msg = convertResponse(response.statusCode)
        throw new DockerDeployFailedException(msg)
      }
      String containerId = response.body.Id
      status "Docker deployed successfully. Container id: ${containerId}"
      status "Starting container ${containerId}"

      def body = [PublishAllPorts: description.publishPorts]
      if (description.config.ports) {
        body.PortBindings = description.config.ports.collectEntries { [("${it.port}/${it.proto}"): [[HostPort: it.hostPort]]] }
      }
      response = restTemplate.exchange("${description.credentials.credentials.url}/containers/${containerId}/start", HttpMethod.POST, new HttpEntity(body, headers), Map)
      if (response.statusCode != HttpStatus.NO_CONTENT) {
        def msg = convertStartResponse(response.statusCode)
        throw new DockerDeployFailedException(msg)
      }

      status "Container started successfully."
      new DeploymentResult(serverGroupNames: [containerId])
    } catch (HttpClientErrorException e) {
      def msg = convertResponse(e.statusCode)
      throw new DockerDeployFailedException(msg)
    } catch (Exception e) {
      throw new DockerDeployFailedException("Failed to deploy container for unknown reason", e)
    }
  }

  private static String convertResponse(HttpStatus status) {
    switch (status) {
      case HttpStatus.NOT_FOUND:
        "no such container"
        break
      case HttpStatus.NOT_ACCEPTABLE:
        "impossible to attach (container not running)"
        break
      case HttpStatus.INTERNAL_SERVER_ERROR:
        "server error"
        break
      default:
        "ok"
    }
  }

  private static String convertStartResponse(HttpStatus status) {
    switch (status) {
      case HttpStatus.NOT_MODIFIED:
        "container already started"
        break
      case HttpStatus.NOT_FOUND:
        "no such container"
        break
      case HttpStatus.INTERNAL_SERVER_ERROR:
        "server err"
        break
      default:
        "ok"
    }
  }

  private Integer getAncestorContainer(String app, String stack, Docker docker) {
    int seq = 0
    try {
      List<Map> allContainers = restTemplate.getForObject("${docker.url}/containers/json", List)
      for (container in allContainers) {
        Map fullContainerObj = restTemplate.getForObject("${docker.url}/containers/${container.Id}/json", Map)
        List<String> env = fullContainerObj.Config.Env
        def appName = env.find { it.split('=')[0] == APP_ENV_KEY }
        def stackName = env.find { it.split('=')[0] == STACK_ENV_KEY }
        def sequence = env.find { it.split('=')[0] == SEQ_ENV_VAR }
        if (appName == app && (stack && stack == stackName) && sequence) {
          seq = Integer.valueOf(sequence)
        }
      }
    } catch (e) {
      e.printStackTrace()
      throw new DockerDeployFailedException("Error while resolving container ancestry", e)
    }
    seq
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Docker deployable was not found")
  @InheritConstructors
  static class DockerDeployableNotFoundException extends RuntimeException {}

  @InheritConstructors
  static class DockerDeployFailedException extends RuntimeException {}
}
