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
import com.netflix.spinnaker.kato.cf.TestCredential
import com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.InstanceInfo
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.InstancesInfo
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
/**
 * Test cases for {@link CloudFoundryDeployHandler}
 *
 *
 */
class CloudFoundryDeployHandlerUnitSpec extends Specification {

  @Subject
  CloudFoundryDeployHandler handler

  @Shared
  ResourceLoader resourceLoader = new DefaultResourceLoader()

  CloudFoundryClient clientToTwoRunningInstances
  CloudFoundryClient clientToPartiallyUpApplication

  CloudFoundryClientFactory factoryForTwoRunningInstances
  CloudFoundryClientFactory factoryForPartiallyUpApplication

  /**
   * There are stubs to test for either a {@link InstanceInfo.RUNNING} cluster or one that is FLAPPING.
   * When an instance is DOWN or CRASHED, the client waits for the app to retry multiple times and is NOT
   * catchable.
   *
   * @return
   */
  def setup() {
    def runningInstance = Stub(InstanceInfo) {
      getState() >> InstanceState.RUNNING
    }

    def flappingInstance = Stub(InstanceInfo) {
      getState() >> InstanceState.FLAPPING
    }

    def clusterWithBothInstancesRunning = Stub(InstancesInfo) {
      getInstances() >> [runningInstance, runningInstance]
    }

    def clusterWithOnlyOneInstanceRunning = Stub(InstancesInfo) {
      getInstances() >> [runningInstance, flappingInstance]
    }

    def logMessage1 = "first bits"
    def logMessage2 = "last bits"

    clientToTwoRunningInstances = Stub(CloudFoundryClient) {
      // Read out some stubbed log data
      getStagingLogs(_, 0) >> logMessage1
      getStagingLogs(_, logMessage1.length()) >> logMessage2
      getStagingLogs(_, logMessage1.length() + logMessage2.length()) >> null
      // Get mock instances
      getApplicationInstances(_) >> clusterWithBothInstancesRunning
    }

    clientToPartiallyUpApplication = Stub(CloudFoundryClient) {
      // Read out some stubbed log data
      getStagingLogs(_, 0) >> logMessage1
      getStagingLogs(_, logMessage1.length()) >> logMessage2
      getStagingLogs(_, logMessage1.length() + logMessage2.length()) >> null
      // Get mock instances
      getApplicationInstances(_) >> clusterWithOnlyOneInstanceRunning
    }

    Task task = Stub(Task) {
      getResultObjects() >> []
    }
    TaskRepository.threadLocalTask.set(task)
  }

  // TODO Rewrite when end-to-end deployment verified

  @Ignore('Rewrite when end-to-end deployment verified')
  void "handler handles basic cf deploy description type"() {
    given:
    handler = new CloudFoundryDeployHandler(new TestCloudFoundryClientFactory(stubClient: clientToTwoRunningInstances));
    def description = new CloudFoundryDeployDescription()
    description.credentials = TestCredential.named('test')

    expect:
    handler.handles description
  }

  static boolean isOffline() {
    Socket s
    try {
      s = new Socket('repo.spring.io', 80)
      return false
    } catch (e) {
      return true
    } finally {
      if (s != null) {
        try {
          s.close()
        } catch (ignored) {

        }
      }
    }
  }

  //@IgnoreIf({CloudFoundryDeployHandlerUnitSpec.isOffline()})
  @Ignore('Rewrite when end-to-end deployment verified')
  void "handler handles cf deploy description with remote artifact"() {
    setup:
    handler = new CloudFoundryDeployHandler(new TestCloudFoundryClientFactory(stubClient: clientToTwoRunningInstances));
    def description = new CloudFoundryDeployDescription(application: "cool-app",
        artifact: "http://repo.spring.io/libs-release/org/cloudfoundry/cloudfoundry-client-lib/1.1.1/cloudfoundry-client-lib-1.1.1-javadoc.jar")
    description.credentials = TestCredential.named('test')

    when:
    def results = handler.handle(description, [])

    then:
    results.messages == ["first bits", "last bits", "2 of 2 instances running (2 running)"]
    results.serverGroupNames == []
  }

  @Ignore('Rewrite when end-to-end deployment verified')
  void "handler should report 2 of 2 instances as being successfully up"() {
    setup:
    handler = new CloudFoundryDeployHandler(new TestCloudFoundryClientFactory(stubClient: clientToTwoRunningInstances));
    def description = new CloudFoundryDeployDescription(application: "cool-app",
        artifact: resourceLoader.getResource("classpath:cool-app.jar").file.absolutePath)
    description.credentials = TestCredential.named('test')

    when:
    def results = handler.handle(description, [])

    then:
    results.messages == ["first bits", "last bits", "2 of 2 instances running (2 running)"]
    results.serverGroupNames == []
  }

  @Ignore('Rewrite when end-to-end deployment verified')
  void "handler should throw exception when unable to launch app"() {
    setup:
    handler = new CloudFoundryDeployHandler(new TestCloudFoundryClientFactory(stubClient: clientToPartiallyUpApplication));
    def description = new CloudFoundryDeployDescription(application: "cool-app",
        artifact: resourceLoader.getResource("classpath:cool-app.jar").file.absolutePath)
    description.credentials = TestCredential.named('test')

    when:
    handler.handle(description, [])

    then:
    thrown(RuntimeException)
  }

}
