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
import com.netflix.spinnaker.kato.docker.deploy.description.StopContainerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class StopContainerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DOCKER_STOP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private static status(String msg) {
    task.updateStatus BASE_PHASE, msg
  }

  private StopContainerDescription description

  StopContainerAtomicOperation(StopContainerDescription description) {
    this.description = description
  }

  @Autowired
  RestTemplate restTemplate

  @Override
  Void operate(List priorOutputs) {
    status "Initializing stop container operation for ${description.container}. Will wait ${description.wait} seconds before shutdown."
    try {
      def resp = restTemplate.postForEntity("${description.credentials.url}/containers/${description.container}/stop?t=${description.wait}", null, Void)
      if (resp.statusCode != HttpStatus.NO_CONTENT) {
        def reason = convertReason(resp.statusCode)
        throw new StopContainerFailedException(reason)
      } else {
        status "Container stop initiated."
      }
    } catch (HttpClientErrorException e) {
      def msg = convertReason(e.statusCode)
      throw new StopContainerFailedException(msg, e)
    } catch (Exception e) {
      throw new StopContainerFailedException("Stopping container failed for unknown reason", e)
    }
    status "Container stop success."
  }

  private static String convertReason(HttpStatus status) {
    switch (status) {
      case HttpStatus.NOT_MODIFIED:
        "container already stopped"
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

  @ResponseStatus(value = HttpStatus.I_AM_A_TEAPOT, reason = "Stop container failed.")
  @InheritConstructors
  static class StopContainerFailedException extends RuntimeException {}
}
