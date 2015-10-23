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

import com.netflix.frigga.NameValidation
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
import org.cloudfoundry.client.lib.StartingInfo
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.InstanceInfo
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.Staging
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

import java.util.concurrent.TimeUnit

/**
 * A deployment handler for Cloud Foundry. Inspired by cf-maven-plugin's {@link org.cloudfoundry.maven.AbstractPush}
 *
 * @author Greg Turnquist
 */
class CloudFoundryDeployHandler implements DeployHandler<CloudFoundryDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static final Integer DEFAULT_MEMORY = 512
  private static final Integer DEFAULT_APP_STARTUP_TIMEOUT = 5

  private final String username
  private final String password // minutes

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
    CloudFoundryClient client = clientFactory.createCloudFoundryClient(description)

    def clusterName = combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster ${clusterName} in ${description.space}..."

    def nextSequence = description.targetPackage.buildNumber.toInteger() % 1000
    def serverGroupName = "${clusterName}-v".toString() + String.format("%03d", nextSequence)
    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()

    task.updateStatus BASE_PHASE, "Preparing deployment of ${serverGroupName}"
    if (description.urls.empty) {
      description.urls = ["${serverGroupName}.${client.defaultDomain.name}".toString()]
    }

    task.updateStatus BASE_PHASE, "Adding domains..."
    addDomains(client, description)

    task.updateStatus BASE_PHASE, "Creating services..."
    createServices(client)

    task.updateStatus BASE_PHASE, "Pushing app..."

    task.updateStatus BASE_PHASE, "Creating application ${serverGroupName}"

    createApplication(serverGroupName, description, client)

    task.updateStatus BASE_PHASE, "Uploading application"

    try {
      uploadApplication(serverGroupName, description, client)
    } catch (CloudFoundryException e) {
      def message = "Error while creating application '%${serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
      throw new RuntimeException(message, e)
    }

    if (description?.targetSize) {
      task.updateStatus BASE_PHASE, "Setting the number of instances to ${description.targetSize}"
      try {
        client.updateApplicationInstances(serverGroupName, description.targetSize)
      } catch (CloudFoundryException e) {
        def message = "Error while setting number of instances for application '${serverGroupName}'. " +
            "Error message: '${e.message}'. Description: '${e.description}'"
        throw new RuntimeException(message, e)
      }
    }

    task.updateStatus BASE_PHASE, "Starting ${serverGroupName}"

    client.startApplication(serverGroupName)

    // TODO Migrate this into the proper monitoring
    try {
      showStagingStatus(client.startApplication(serverGroupName), deploymentResult, client)

      showStartingStatus(description, client.getApplication(serverGroupName), deploymentResult, client)
      showStartResults(client.getApplication(serverGroupName), description.urls, deploymentResult, client)
    } catch (CloudFoundryException e) {
      def message = "Error while creating application '${serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
      throw new RuntimeException(message, e)
    }

    deploymentResult.serverGroupNames = [serverGroupName]
    deploymentResult.serverGroupNameByRegion[description.credentials.org] = serverGroupName
    deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

    deploymentResult
  }

  def combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
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
    task.updateStatus BASE_PHASE, String.format("Deploying resource %s", description.targetPackage)

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

      client.uploadApplication(serverGroupName, description.targetPackage.package, file.newInputStream())
    } catch (IOException e) {
      throw new IllegalStateException("Error uploading application => ${e.message}.", e)
    }
  }

  def showStagingStatus(StartingInfo startingInfo, DeploymentResult deploymentResult, CloudFoundryClient client) {
    if (startingInfo != null) {
      def offset = 0
      String staging = client.getStagingLogs(startingInfo, offset)
      while (staging != null) {
        task.updateStatus BASE_PHASE, staging
        deploymentResult.messages.add(staging)
        offset += staging.length()
        staging = client.getStagingLogs(startingInfo, offset)
      }
    }
  }

  def showStartingStatus(CloudFoundryDeployDescription description, CloudApplication app,
                         DeploymentResult deploymentResult, CloudFoundryClient client) {
    task.updateStatus BASE_PHASE, String.format("Checking status of application '%s'", description.application)

    long appStartupExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(DEFAULT_APP_STARTUP_TIMEOUT)

    while (System.currentTimeMillis() < appStartupExpiry) {
      def instances = client.getApplicationInstances(app)?.instances

      if (instances != null) {
        int expectedInstances = instances?.size() ?: 0
        int runningInstances = instances?.findAll { it.state == InstanceState.RUNNING }?.size() ?: 0
        int flappingInstances = instances?.findAll { it.state == InstanceState.FLAPPING }?.size() ?: 0

        showInstancesStatus(instances, runningInstances, expectedInstances, deploymentResult)

        if (flappingInstances > 0)
          break

        if (runningInstances == expectedInstances)
          break
      }

      try {
        Thread.sleep(1000)
      } catch (InterruptedException e) {
        // ignore
      }
    }

  }

  def showInstancesStatus(List<InstanceInfo> instances, int runningInstances, int expectedInstances, DeploymentResult deploymentResult) {
    def stateCounts = [:]

    instances?.each { instance ->
      String state = instance.state.toString()
      Integer stateCount = stateCounts[state] as Integer
      if (stateCount == null) {
        stateCounts[state] = 1
      } else {
        stateCounts[state] = stateCount + 1
      }
    }

    def stateStrings = stateCounts.collect { String key, int value ->
      "${value} ${key.toLowerCase()}"
    }

    def message = "${runningInstances} of ${expectedInstances} instances running (${stateStrings.join(',')})"
    task.updateStatus BASE_PHASE, message
    deploymentResult.messages.add(message)
  }

  def showStartResults(CloudApplication app, List<String> urls, DeploymentResult deploymentResult,
                       CloudFoundryClient client) {
    def instances = client.getApplicationInstances(app)?.instances

    int expectedInstances = instances?.size() ?: 0
    int runningInstances = instances?.findAll { it.state == InstanceState.RUNNING }?.size() ?: 0
    int flappingInstances = instances?.findAll { it.state == InstanceState.FLAPPING }?.size() ?: 0

    if (flappingInstances > 0) {
      throw new RuntimeException("Application start unsuccessful")
    } else if (runningInstances > 0) {
      if (urls == null || urls.empty) {
        task.updateStatus BASE_PHASE, "Application '${app.name}' is available"
      } else {
        task.updateStatus BASE_PHASE, "Application '${app.name}' is available at '${urls.collect {'http://' + it}.join(',')}'"
      }
    }
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof CloudFoundryDeployDescription
  }
}
