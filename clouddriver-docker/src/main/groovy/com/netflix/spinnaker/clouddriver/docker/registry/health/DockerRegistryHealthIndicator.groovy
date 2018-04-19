/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.health

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.core.AlwaysUpHealthIndicator
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.ResponseStatus

class DockerRegistryHealthIndicator extends AlwaysUpHealthIndicator {

  AccountCredentialsProvider accountCredentialsProvider

  DockerRegistryHealthIndicator(Registry registry, AccountCredentialsProvider accountCredentialsProvider) {
    super(registry, "docker")
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    updateHealth {
      Set<DockerRegistryNamedAccountCredentials> dockerRegistryCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof DockerRegistryNamedAccountCredentials
      } as Set<DockerRegistryNamedAccountCredentials>

      for (DockerRegistryNamedAccountCredentials accountCredentials in dockerRegistryCredentialsSet) {
        DockerRegistryCredentials dockerRegistryCredentials = accountCredentials.credentials

        dockerRegistryCredentials.client.checkV2Availability()
      }
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with DockerRegistry.")
  @InheritConstructors
  static class DockerRegistryIOException extends RuntimeException {}
}

