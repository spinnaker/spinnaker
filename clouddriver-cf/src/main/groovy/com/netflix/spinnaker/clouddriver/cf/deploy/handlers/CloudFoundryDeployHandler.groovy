/*
 * Copyright 2015-2016 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.handlers
import com.netflix.frigga.NameBuilder
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.utils.RestTemplateFactory
import com.netflix.spinnaker.clouddriver.cf.utils.S3ServiceFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.apache.commons.codec.binary.Base64
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.InstancesInfo
import org.cloudfoundry.client.lib.domain.Staging
import org.jets3t.service.S3Service
import org.jets3t.service.S3ServiceException
import org.jets3t.service.model.S3Object
import org.jets3t.service.security.AWSCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.util.StreamUtils
import org.springframework.web.client.HttpServerErrorException
/**
 * A deployment handler for Cloud Foundry.
 */
class CloudFoundryDeployHandler implements DeployHandler<CloudFoundryDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  @Qualifier('cloudFoundryOperationPoller')
  OperationPoller operationPoller

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CloudFoundryClientFactory clientFactory

  RestTemplateFactory restTemplateFactory

  S3ServiceFactory s3ServiceFactory

  @Override
  DeploymentResult handle(CloudFoundryDeployDescription description, List priorOutputs) {
    CloudFoundryClient client = clientFactory.createCloudFoundryClient(description.credentials, true)

    task.updateStatus BASE_PHASE, "Initializing handler..."

    def nameBuilder = new NameBuilder() {
      @Override
      public String combineAppStackDetail(String appName, String stack, String detail) {
        return super.combineAppStackDetail(appName, stack, detail)
      }
    }

    def clusterName = nameBuilder.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)
    def nextSequence = getNextSequence(clusterName, client)
    task.updateStatus BASE_PHASE, "Found next sequence ${nextSequence}."

    description.serverGroupName = "${clusterName}-v${nextSequence}".toString()

    try {
      task.updateStatus BASE_PHASE, "Creating application ${description.serverGroupName}"

      createApplication(description, client)

      uploadApplication(description, client)

      addEnvironmentVariables(description, client)

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

    } catch (Exception e) {
      // In the event of an error along the way, need to clean out the server group from Cloud Foundry
      try {
        task.updateStatus BASE_PHASE, "Deleting ${description.serverGroupName}"
        client.deleteApplication(description.serverGroupName)
      } finally {
        task.updateStatus BASE_PHASE, "${description.serverGroupName} cleaned up."
      }

      throw new RuntimeException("Error while building '${description.serverGroupName}'. Error message: '${e.message}'", e)
    }

    operationPoller.waitForOperation(
        {client.getApplicationInstances(description.serverGroupName)},
        { InstancesInfo instancesInfo -> instancesInfo?.instances?.any {it.state == InstanceState.RUNNING}},
        null, task, description.serverGroupName, BASE_PHASE)

    def deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames << "${description.credentials.org}:${description.serverGroupName}".toString()
    deploymentResult.serverGroupNameByRegion[description.credentials.org] = description.serverGroupName
    deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

    deploymentResult
  }

  def createApplication(CloudFoundryDeployDescription description, CloudFoundryClient client) {
    CloudApplication application = null
    try {
      application = client.getApplication(description.serverGroupName)
    } catch (HttpServerErrorException e) {
      if (e.statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
        task.updateStatus BASE_PHASE, "${description.serverGroupName} is unavailable."
      }
    } catch (CloudFoundryException e) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        def message = "Error while checking for existing application '${description.serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
        throw new RuntimeException(message, e)
      }
    }

    try {
      Staging staging = (description?.buildpackUrl) ? new Staging(null, description.buildpackUrl) : new Staging()
      if (application == null) {
        def defaultDomain = client.defaultDomain
        def domain = (defaultDomain != null) ? '.' + defaultDomain.name : ''
        def loadBalancers = description.loadBalancers?.split(',').collect { it + domain }
        loadBalancers += description.serverGroupName + domain

        task.updateStatus BASE_PHASE, "Memory set to ${description.memory}"
        task.updateStatus BASE_PHASE, "Disk limit set to ${description.disk}"
        if (description?.buildpackUrl) {
          task.updateStatus BASE_PHASE, "Custom buildpack ${description.buildpackUrl}"
        }
        client.createApplication(description.serverGroupName, staging, description.disk, description.memory, loadBalancers,
            description?.services)
        // TODO Add support for updating application disk quotas
      }
    } catch (CloudFoundryException e) {
      def message = "Error while creating application '${description.serverGroupName}'. Error message: '${e.message}'. Description: '${e.description}'"
      throw new RuntimeException(message, e)
    }
  }

  def uploadApplication(CloudFoundryDeployDescription description, CloudFoundryClient client) {
    try {
      def results

      if (description.repository.startsWith('http')) {
        results = downloadJarFileFromWeb(description)
      } else if (description.repository.startsWith('s3')) {
        results = downloadJarFileFromS3(description)
      }  else {
        throw new RuntimeException("Repository '${description.repository}' is not a recognized protocol.")
      }

      task.updateStatus BASE_PHASE, "Uploading ${results.contentLength} bytes to ${description.serverGroupName}"

      client.uploadApplication(description.serverGroupName, results.file.name, results.file.newInputStream())

    } catch (IOException e) {
      throw new IllegalStateException("Error uploading application => ${e.message}.", e)
    }
  }

  def addEnvironmentVariables(CloudFoundryDeployDescription description, CloudFoundryClient client) {
    task.updateStatus BASE_PHASE, "Setting environment variables..."

    def env = [:]

    if (description?.envs && !description.envs.isEmpty()) {
      env += description.envs.collectEntries { [it.name, it.value] }
    }

    if (isJenkinsTrigger(description)) {
      env[CloudFoundryConstants.JENKINS_HOST] = description.trigger?.buildInfo?.url ?: ''
      env[CloudFoundryConstants.JENKINS_NAME] = description.trigger?.job ?: ''
      env[CloudFoundryConstants.JENKINS_BUILD] = description.trigger?.buildNumber ?: ''
      env[CloudFoundryConstants.COMMIT_HASH] = description.trigger?.buildInfo?.scm[0]?.sha1 ?: ''
      env[CloudFoundryConstants.COMMIT_BRANCH] = description.trigger?.buildInfo?.scm[0]?.branch ?: ''
    }

    env[CloudFoundryConstants.PACKAGE] = description.artifact
    env[CloudFoundryConstants.LOAD_BALANCERS] = description.loadBalancers

    env[CloudFoundryConstants.REPOSITORY] = description.repository
    env[CloudFoundryConstants.ARTIFACT] = description.artifact
    env[CloudFoundryConstants.ACCOUNT] = description.credentialAccount

    client.updateApplicationEnv(description.serverGroupName, env)
  }

  /**
   * Discern if this is a Jenkins trigger
   *
   * @param description
   * @return
   */
  private boolean isJenkinsTrigger(CloudFoundryDeployDescription description) {
    description?.trigger?.job && description?.trigger?.buildNumber
  }

  def downloadJarFileFromWeb(CloudFoundryDeployDescription description) {
    HttpHeaders requestHeaders = new HttpHeaders()

    if (description.username && description.password) {
      requestHeaders.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.encodeBase64String("${description.username}:${description.password}".getBytes()))
    }

    def requestEntity = new HttpEntity<>(requestHeaders)

    def restTemplate = this.restTemplateFactory.createRestTemplate()

    long contentLength = -1
    ResponseEntity<byte[]> responseBytes

    while (contentLength == -1 || contentLength != responseBytes?.headers?.getContentLength()) {
      if (contentLength > -1) {
        task.updateStatus BASE_PHASE, "Downloaded ${contentLength} bytes, but ${responseBytes.headers.getContentLength()} expected! Retry..."
      }
      def basePath = description.repository + (description.repository.endsWith('/') ? '' : '/')
      responseBytes = restTemplate.exchange("${basePath}${description.artifact}".toString(), HttpMethod.GET, requestEntity, byte[])
      contentLength = responseBytes != null ? responseBytes.getBody().length : 0;
    }

    task.updateStatus BASE_PHASE, "Successfully downloaded ${contentLength} bytes"

    File file = File.createTempFile(description.serverGroupName, null)
    FileOutputStream fout = new FileOutputStream(file)
    fout.write(responseBytes.body)
    fout.close()

    [
        contentLength: contentLength,
        file: file
    ]
  }

  def downloadJarFileFromS3(CloudFoundryDeployDescription description) {
    AWSCredentials awsCredentials = null

    if (description.username && description.password) {
      awsCredentials = new AWSCredentials(description.username, description.password, description.credentials.name)
    }

    S3Service s3 = s3ServiceFactory.createS3Service(awsCredentials)

    def baseBucket = description.repository + (description.repository.endsWith('/') ? '' : '/') - 's3://'
    baseBucket = baseBucket.getAt(0..baseBucket.length()-2)

    task.updateStatus BASE_PHASE, "Downloading ${description.artifact} from ${baseBucket}..."

    S3Object object
    try {
      object = s3.getObject(baseBucket, description.artifact)
    } catch (S3ServiceException e) {
      task.updateStatus BASE_PHASE, "Failed to download ${description.artifact} from ${baseBucket} => ${e.message}"
      throw new RuntimeException(e.message)
    }

    task.updateStatus BASE_PHASE, "Successfully downloaded ${object.contentLength} bytes"

    File file = File.createTempFile(description.serverGroupName, null)
    FileOutputStream fout = new FileOutputStream(file)
    StreamUtils.copy(object.dataInputStream, fout)
    fout.close()

    [
        contentLength: object.contentLength,
        file: file
    ]

  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof CloudFoundryDeployDescription
  }

  /**
   * Scan through all the apps in this space, and find the maximum one with a matching cluster name
   *
   * @param clusterName
   * @param project
   * @param region
   * @param client
   * @return
   */
  private def getNextSequence(String clusterName, CloudFoundryClient client) {
    def maxSeqNumber = -1

    client.applications.each { app ->
      def names = Names.parseName(app.name)

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    }

    String.format("%03d", (++maxSeqNumber) % 1000)
  }

}
