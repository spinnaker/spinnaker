/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.deploy.handlers

import com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import org.apache.commons.codec.binary.Base64
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.Staging
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

/**
 * A deployment handler for Cloud Foundry. Inspired by cf-maven-plugin's {@literal AbstractPush}
 *
 *
 */
class CloudFoundryDeployHandler implements DeployHandler<CloudFoundryDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static final Integer DEFAULT_MEMORY = 512
  private static final Integer DEFAULT_APP_STARTUP_TIMEOUT = 5  // minutes

  private final String username
  private final String password

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private CloudFoundryClientFactory clientFactory

  CloudFoundryDeployHandler(CloudFoundryClientFactory clientFactory, String username, String password) {
    this.clientFactory = clientFactory
    this.username = username
    this.password = password
  }

  void setClientFactory(CloudFoundryClientFactory clientFactory) {
    this.clientFactory = clientFactory
  }

  @Override
  DeploymentResult handle(CloudFoundryDeployDescription description, List priorOutputs) {
    CloudFoundryClient client = clientFactory.createCloudFoundryClient(description.credentials, true)

    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()

    task.updateStatus BASE_PHASE, "Preparing deployment of ${description.serverGroupName}"
    if (description.urls.empty) {
      description.urls = ["${description.serverGroupName}.${client.defaultDomain.name}".toString()]
    }

    task.updateStatus BASE_PHASE, "Adding domains..."
    addDomains(client, description)

    task.updateStatus BASE_PHASE, "Creating services..."
    createServices(client)

    task.updateStatus BASE_PHASE, "Creating application ${description.serverGroupName}"

    createApplication(description.serverGroupName, description, client)

    try {
      uploadApplication(description.serverGroupName, description, client)
    } catch (CloudFoundryException e) {
      def message = "Error while creating application '%${description.serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
      throw new RuntimeException(message, e)
    }

    if (description?.targetSize) {
      task.updateStatus BASE_PHASE, "Setting the number of instances to ${description.targetSize}"
      try {
        client.updateApplicationInstances(description.serverGroupName, description.targetSize)
      } catch (CloudFoundryException e) {
        def message = "Error while setting number of instances for application '${description.serverGroupName}'. " +
            "Error message: '${e.message}'. Description: '${e.description}'"
        throw new RuntimeException(message, e)
      }
    }

    task.updateStatus BASE_PHASE, "Starting ${description.serverGroupName}"

    client.startApplication(description.serverGroupName)

    deploymentResult.serverGroupNames = [description.serverGroupName]
    deploymentResult.serverGroupNameByRegion[description.credentials.org] = description.serverGroupName
    deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

    deploymentResult
  }

  def addDomains(CloudFoundryClient client, CloudFoundryDeployDescription description) {
    def domains = client.domains
    def currentDomains = domains.collect { domain -> domain.name }
//    if (description.domains != null) {
//      description.domains.each { domain ->
//        if (!currentDomains.contains(domain)) {
//          client.addDomain(domain)
//          task.updateStatus BASE_PHASE, "Adding '${domain}' to list of registered domains"
//        }
//      }
//    }
  }

  def createServices(CloudFoundryClient client) {
    def currentServices = client.services
    def currentServiceNames = currentServices.collect { service -> service.name }
    // TODO Add support for creating services
  }

  def createApplication(String serverGroupName, CloudFoundryDeployDescription description, CloudFoundryClient client) {
    CloudApplication application = null
    try {
      application = client.getApplication(serverGroupName)
    } catch (HttpServerErrorException e) {
      if (e.statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
        task.updateStatus BASE_PHASE, "${serverGroupName} is unavailable."
      }
    } catch (CloudFoundryException e) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        def message = "Error while checking for existing application '${serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
        throw new RuntimeException(message, e)
      }
    }

    try {
      Staging staging = new Staging() // TODO evaluate if we need command, buildpack, etc.
      if (application == null) {
        client.createApplication(serverGroupName, staging, description.memory, description.urls, null)
//        // TODO Add support for configuring application env
//      } else {
//        client.stopApplication(description.application)
//        client.updateApplicationStaging(description.application, staging)
//        if (description.memory != null) {
//          client.updateApplicationMemory(description.application, description.memory)
//        }
//        // TODO Add support for updating application disk quotas
//        client.updateApplicationUris(description.application, description.urls)
//        // TODO Add support for updating application services
//        // TODO Add support for updating application env
      }
    } catch (CloudFoundryException e) {
      def message = "Error while creating application '${serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
      throw new RuntimeException(message, e)
    }
  }

  def uploadApplication(String serverGroupName, CloudFoundryDeployDescription description, CloudFoundryClient client) {
    try {
      def path = "${description.targetPackage.buildUrl}artifact/${description.targetPackage.relativePath}"
      HttpHeaders requestHeaders = new HttpHeaders()
      requestHeaders.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.encodeBase64String("${username}:${password}".getBytes()))

      def requestEntity = new HttpEntity<>(requestHeaders)

      def restTemplate = new RestTemplate()
      def factory = new SimpleClientHttpRequestFactory()
      factory.bufferRequestBody = false
      restTemplate.requestFactory = factory

      long contentLength = -1
      ResponseEntity<byte[]> responseBytes

      while (contentLength == -1 || contentLength != responseBytes?.headers?.getContentLength()) {
        if (contentLength > -1) {
          task.updateStatus BASE_PHASE, "Downloaded ${contentLength} bytes, but ${responseBytes.headers.getContentLength()} expected! Retry..."
        }
        responseBytes = restTemplate.exchange(path, HttpMethod.GET, requestEntity, byte[])
        contentLength = responseBytes != null ? responseBytes.getBody().length : 0;
      }

      task.updateStatus BASE_PHASE, "Successfully downloaded ${contentLength} bytes"

      File file = File.createTempFile(description.targetPackage.package, null)
      FileOutputStream fout = new FileOutputStream(file)
      fout.write(responseBytes.body)
      fout.close()

      task.updateStatus BASE_PHASE, "Uploading ${contentLength} bytes to ${serverGroupName}"

      client.uploadApplication(serverGroupName, description.targetPackage.package, file.newInputStream())
    } catch (IOException e) {
      throw new IllegalStateException("Error uploading application => ${e.message}.", e)
    }
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof CloudFoundryDeployDescription
  }
}
