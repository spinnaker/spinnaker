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

import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.utils.RestTemplateFactory
import com.netflix.spinnaker.clouddriver.cf.utils.S3ServiceFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.CloudDomain
import org.cloudfoundry.client.lib.domain.InstanceInfo
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.InstancesInfo
import org.jets3t.service.S3Service
import org.jets3t.service.S3ServiceException
import org.jets3t.service.ServiceException
import org.jets3t.service.model.S3Object
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject
/**
 * Test cases for {@link CloudFoundryDeployHandler}
 */
class CloudFoundryDeployHandlerSpec extends Specification {

  @Subject
  CloudFoundryDeployHandler handler

  CloudFoundryClient client

  Task task

  RestTemplate restTemplate

  RestTemplateFactory restTemplateFactory

  S3Service s3

  S3ServiceFactory s3ServiceFactory

  def setup() {
    client = Mock(CloudFoundryClient)
    task = new DefaultTask('test')
    TaskRepository.threadLocalTask.set(task)
    restTemplate = Mock(RestTemplate)
    restTemplateFactory = Stub(RestTemplateFactory) {
      createRestTemplate() >> { restTemplate }
    }
    s3 = Mock(S3Service)
    s3ServiceFactory = Stub(S3ServiceFactory) {
      createS3Service(_) >> { s3 }
    }
  }

  void "only handles CF deployment description type"() {
    given:
    handler = new CloudFoundryDeployHandler(clientFactory: new TestCloudFoundryClientFactory(stubClient: client));
    def description = new CloudFoundryDeployDescription(credentials: TestCredential.named('test'))

    expect:
    handler.handles description
  }

  void "handles basic deployment from http"() {
    given:
    def body = 'simulated file content'.getBytes()
    def headers = new HttpHeaders()
    headers.set(HttpHeaders.CONTENT_LENGTH, "${body.length}")
    def simulatedDownloadResponse = ResponseEntity.ok().headers(headers).body(body)

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        targetSize    : 2,
        envs          : [[name: 'CUSTOM_ENV', value: 'test value']],
        buildpackUrl  : 'https://my-custom-buildpack/',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    def results = handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedDownloadResponse }
    0 * restTemplate._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            CUSTOM_ENV              : 'test value',
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.updateApplicationInstances(serverGroupName, 2)
    1 * client.startApplication(serverGroupName)
    0 * client._

    results.serverGroupNames == ["spinnaker:${serverGroupName}"]
    results.serverGroupNameByRegion == [spinnaker: serverGroupName]
    results.messages == [
        'INIT : Creating task test',
        'DEPLOY : Initializing handler...',
        'DEPLOY : Found next sequence 000.',
        "DEPLOY : Creating application ${serverGroupName}",
        'DEPLOY : Memory set to 1024',
        'DEPLOY : Disk limit set to 1024',
        "DEPLOY : Custom buildpack ${description.buildpackUrl}",
        "DEPLOY : Successfully downloaded ${simulatedDownloadResponse.body.length} bytes",
        "DEPLOY : Uploading ${simulatedDownloadResponse.body.length} bytes to ${serverGroupName}",
        'DEPLOY : Setting environment variables...',
        'DEPLOY : Setting the number of instances to 2',
        "DEPLOY : Starting ${serverGroupName}"
    ]
  }

  void "handles username/password for http"() {
    given:
    def body = 'simulated file content'.getBytes()
    def headers = new HttpHeaders()
    headers.set(HttpHeaders.CONTENT_LENGTH, "${body.length}")
    def simulatedDownloadResponse = ResponseEntity.ok().headers(headers).body(body)

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        username      : 'my-username',
        password      : 'my-password',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedDownloadResponse }
    0 * restTemplate._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles basic deployment from s3"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    def results = handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)

    results.serverGroupNames == ["spinnaker:${serverGroupName}"]
    results.serverGroupNameByRegion == [spinnaker: serverGroupName]
    results.messages == [
        'INIT : Creating task test',
        'DEPLOY : Initializing handler...',
        'DEPLOY : Found next sequence 000.',
        "DEPLOY : Creating application ${serverGroupName}",
        'DEPLOY : Memory set to 1024',
        'DEPLOY : Disk limit set to 1024',
        "DEPLOY : Downloading cool-app-0.1.0.jar from repo.example.com/releases...",
        "DEPLOY : Successfully downloaded ${simulatedDownloadResponse.contentLength} bytes",
        "DEPLOY : Uploading ${simulatedDownloadResponse.contentLength} bytes to ${serverGroupName}",
        'DEPLOY : Setting environment variables...',
        "DEPLOY : Starting ${serverGroupName}"
    ]
  }

  void "handles username/password for s3"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        username      : 'my-username',
        password      : 'my-password',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
  }

  void "handles failure of non-supported download protocol"() {
    given:
    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'ftp://ftp.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Repository '${description.repository}' is not a recognized protocol.'"
  }

  void "handles finding the next sequence number"() {
    given:
    def body = 'simulated file content'.getBytes()
    def headers = new HttpHeaders()
    headers.set(HttpHeaders.CONTENT_LENGTH, "${body.length}")
    def simulatedDownloadResponse = ResponseEntity.ok().headers(headers).body(body)

    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v003'

    when:
    handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedDownloadResponse }
    0 * restTemplate._

    1 * client.getApplications() >> {
      [new CloudApplication(null, description.application + '-v000'), new CloudApplication(null, description.application + '-v002')]
    }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v003.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles rolling the sequence number"() {
    given:
    def body = 'simulated file content'.getBytes()
    def headers = new HttpHeaders()
    headers.set(HttpHeaders.CONTENT_LENGTH, "${body.length}")
    def simulatedDownloadResponse = ResponseEntity.ok().headers(headers).body(body)

    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedDownloadResponse }
    0 * restTemplate._

    1 * client.getApplications() >> { [new CloudApplication(null, description.application + '-v999')] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles incomplete download from http"() {
    given:
    def body1 = 'simulated file co'.getBytes()
    def body2 = 'simulated file content'.getBytes()

    def headers1 = new HttpHeaders()
    headers1.set(HttpHeaders.CONTENT_LENGTH, "${body2.length}")
    def simulatedIncompleteDownload = ResponseEntity.ok().headers(headers1).body(body1)

    def headers2 = new HttpHeaders()
    headers2.set(HttpHeaders.CONTENT_LENGTH, "${body2.length}")
    def simulatedCompletedDownload = ResponseEntity.ok().headers(headers2).body(body2)

    handler = new CloudFoundryDeployHandler(
        clientFactory: new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory: restTemplateFactory,
        operationPoller: Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    def results = handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedIncompleteDownload }
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedCompletedDownload }
    0 * restTemplate._

    1 * client.getApplications() >> { null }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, "example.com", null) }

    results.messages == [
        'INIT : Creating task test',
        'DEPLOY : Initializing handler...',
        'DEPLOY : Found next sequence 000.',
        "DEPLOY : Creating application ${serverGroupName}",
        'DEPLOY : Memory set to 1024',
        'DEPLOY : Disk limit set to 1024',
        "DEPLOY : Downloaded ${simulatedIncompleteDownload.body.length} bytes, but ${headers1.get(HttpHeaders.CONTENT_LENGTH).get(0)} expected! Retry...",
        "DEPLOY : Successfully downloaded ${simulatedCompletedDownload.body.length} bytes",
        "DEPLOY : Uploading ${simulatedCompletedDownload.body.length} bytes to ${serverGroupName}",
        'DEPLOY : Setting environment variables...',
        "DEPLOY : Starting ${serverGroupName}"
    ]
  }

  void "handles failed download from s3"() {
    given:
    handler = new CloudFoundryDeployHandler(
        clientFactory         : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory   : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller       : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> {
      throw new S3ServiceException(new ServiceException('Simulated S3 failure'))
    }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Simulated S3 failure'"
  }

  void "handles populating Jenkins env variables"() {
    given:
    def body = 'simulated file content'.getBytes()
    def headers = new HttpHeaders()
    headers.set(HttpHeaders.CONTENT_LENGTH, "${body.length}")
    def simulatedDownloadResponse = ResponseEntity.ok().headers(headers).body(body)

    handler = new CloudFoundryDeployHandler(
        clientFactory: new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory: restTemplateFactory,
        operationPoller: Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 'http://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        trigger       : [
          job: 'test-job',
          buildNumber: '123',
          buildInfo: [
            url: 'https://ci.example.com/test-job/123',
            scm: [[
              sha1: '12345',
              branch: 'pull-request'
            ]]
          ]
        ],
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * restTemplate.exchange('http://repo.example.com/releases/cool-app-0.1.0.jar', _, _, _) >> { simulatedDownloadResponse }
    0 * restTemplate._

    1 * client.getApplications() >> { null }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, "example.com", null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_JENKINS_HOST: description.trigger.buildInfo.url,
            SPINNAKER_BUILD_JENKINS_NAME: description.trigger.job,
            SPINNAKER_BUILD_JENKINS_BUILD: description.trigger.buildNumber,
            SPINNAKER_BUILD_COMMITHASH: description.trigger.buildInfo.scm[0].sha1,
            SPINNAKER_BUILD_COMMITBRANCH: description.trigger.buildInfo.scm[0].branch,
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles failure to push app to CF"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _) >> { throw new IOException("Simulated CF failure") }
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Error uploading application => Simulated CF failure.'"
  }

  void "handles failure to list existing CF applications"() {
    given:
    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )

    when:
    handler.handle(description, [])

    then:
    0 * s3._

    1 * client.getApplications() >> { throw new RuntimeException('Simulated CF failure to read apps') }
    0 * client._

    RuntimeException e = thrown()
    e.message == "Simulated CF failure to read apps"
  }

  void "handles application not found in CF"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { throw new CloudFoundryException(HttpStatus.NOT_FOUND, "Not Found", "Application not found") }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles application lookup failure due to cloud exception"() {
    given:
    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { throw new CloudFoundryException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated CF failure", "Simulated CF failure") }
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Error while checking for existing application '${serverGroupName}'. Error message: '500 Simulated CF failure'. Description: 'Simulated CF failure''"
  }

  void "handles application lookup failure due to 503 RestTemplate exception"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE) }
    1 * client.getDefaultDomain() >> { null }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}", "${description.application}-v000"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles application lookup failure due to unpredicted RestTemplate exception"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR) }
    1 * client.getDefaultDomain() >> { null }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}", "${description.application}-v000"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles missing default domain"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { null }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}", "${description.application}-v000"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    0 * client._
  }

  void "handles failure to create application"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null) >> {
      throw new CloudFoundryException(HttpStatus.INTERNAL_SERVER_ERROR, 'Simulated failure', 'Unable to create application')
    }
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Error while creating application '${serverGroupName}'. Error message: '500 Simulated failure'. Description: 'Unable to create application''"
  }


  void "handles failure to set env variables"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ]) >> { throw new RuntimeException('Simulated CF failure') }
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Simulated CF failure'"
  }

  void "handles failure to set number of instances"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : Mock(OperationPoller)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        targetSize    : 2,
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'example.com', null) }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}.example.com", "${description.application}-v000.example.com"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.updateApplicationInstances(serverGroupName, 2)  >> { throw new CloudFoundryException(HttpStatus.INTERNAL_SERVER_ERROR, 'Simulated failure', 'Unable to set number of instances') }
    1 * client.deleteApplication(serverGroupName)
    0 * client._

    RuntimeException e = thrown()
    e.message == "Error while building '${serverGroupName}'. Error message: 'Error while setting number of instances for application '${serverGroupName}'. Error message: '500 Simulated failure'. Description: 'Unable to set number of instances''"
  }

  void "polls on state of deployment"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : new OperationPoller(1, 3)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'
    def instancesInfo = Mock(InstancesInfo)

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.STARTING.toString()])] }
    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.RUNNING.toString()])] }
    0 * instancesInfo._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { null }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}", "${description.application}-v000"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    2 * client.getApplicationInstances(serverGroupName) >> { instancesInfo }
    0 * client._
  }

  void "polling failed deployment times out"() {
    given:
    def simulatedDownloadResponse = new S3Object('repo.example.com/releases', 'cool-app-0.1.0.jar')

    handler = new CloudFoundryDeployHandler(
        clientFactory       : new TestCloudFoundryClientFactory(stubClient: client),
        restTemplateFactory : restTemplateFactory,
        s3ServiceFactory: s3ServiceFactory,
        operationPoller     : new OperationPoller(1, 3)
    )
    def description = new CloudFoundryDeployDescription(
        application   : 'cool-app',
        repository    : 's3://repo.example.com/releases',
        artifact      : 'cool-app-0.1.0.jar',
        loadBalancers : 'cool-test-app',
        credentials   : TestCredential.named('test')
    )
    def serverGroupName = description.application + '-v000'
    def instancesInfo = Mock(InstancesInfo)

    when:
    handler.handle(description, [])

    then:
    1 * s3.getObject('repo.example.com/releases', 'cool-app-0.1.0.jar') >> { simulatedDownloadResponse }
    0 * s3._

    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.STARTING.toString()])] }
    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.CRASHED.toString()])] }
    0 * instancesInfo._

    1 * client.getApplications() >> { [] }
    1 * client.getApplication(serverGroupName) >> { null }
    1 * client.getDefaultDomain() >> { null }
    1 * client.createApplication(serverGroupName, _, 1024, 1024, ["${description.loadBalancers}", "${description.application}-v000"], null)
    1 * client.uploadApplication(serverGroupName, _, _)
    1 * client.updateApplicationEnv(serverGroupName,
        [
            SPINNAKER_BUILD_PACKAGE : description.artifact,
            SPINNAKER_LOAD_BALANCERS: description.loadBalancers,
            SPINNAKER_REPOSITORY    : description.repository,
            SPINNAKER_ARTIFACT      : description.artifact,
            SPINNAKER_ACCOUNT       : 'test'
        ])
    1 * client.startApplication(serverGroupName)
    2 * client.getApplicationInstances(serverGroupName) >> { instancesInfo }
    0 * client._

    OperationTimedOutException e = thrown()
    e.message == "Operation on ${serverGroupName} timed out."
  }

}
