/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.smoke.gce

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.oort.config.OortConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Requires
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.net.Network.isReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

// Only runs if the gcs-kms server is listening on port 7909 on the same machine.
@Requires({ isReachable("http://localhost:7909") })
@ContextConfiguration(classes = [OrcaConfiguration, KatoConfiguration, BatchTestConfiguration, OortConfiguration,
                                 Front50Configuration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeGoogleSpec extends Specification {

  def setupSpec() {
    System.setProperty("kato.baseUrl", "http://localhost:8501")
    System.setProperty("oort.baseUrl", "http://localhost:8081")
    System.setProperty("front50.baseUrl", "http://localhost:8080")
  }

  @Autowired PipelineStarter jobStarter
  @Autowired ObjectMapper mapper
  @Autowired JobExplorer jobExplorer

  def "can create application"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipelineObservable = jobStarter.start(configJson)

    then:
    with(pipelineObservable.toBlocking().first()) {
      with(jobExplorer.getJobExecution(id.toLong())) {
        status == BatchStatus.COMPLETED
        exitStatus == ExitStatus.COMPLETED
      }
    }

    where:
    config = [
      application: "googletest",
      stages     : [
        [
          type         : "createApplication",
          account      : "my-account-name",
          application  :
            [
              name        : "googletest",
              description : "A test Google application.",
              email       : "some-email-addr@gmail.com",
              pdApiKey    : "Some pager key."
            ],
          description  : "Create new googletest application as smoke test."
        ]
      ]
    ]
  }

  def "can deploy server group"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipelineObservable = jobStarter.start(configJson)

    then:
    with(pipelineObservable.toBlocking().first()) {
      with(jobExplorer.getJobExecution(id.toLong())) {
        status == BatchStatus.COMPLETED
        exitStatus == ExitStatus.COMPLETED
      }
    }

    where:
    config = [
      application: "googletest",
      stages     : [
        [
          type         : "deploy",
          providerType : "gce",
          zone         : "us-central1-b",
          image        : "debian-7-wheezy-v20141021",
          instanceType : "f1-micro",
          capacity     : [ desired : 2 ],
          application  : "googletest",
          stack        : "test",
          credentials  : "my-account-name",
          user         : "smoke-test-user",
          description  : "Create new server group in cluster googletest as smoke test."
        ]
      ]
    ]
  }
}
