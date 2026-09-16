/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.client.http.HttpTransport
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.services.compute.Compute
import com.google.auth.oauth2.AccessToken
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import spock.lang.Specification

import java.time.LocalDate

class GoogleCredentialsSpec extends Specification {

  def "DEFAULT compute client targets stable v1 service and batch paths"() {
    when:
    Compute compute = new FakeGoogleCredentials("test-project").getCompute("spinnaker-test")
    def request = compute.projects().get("test-project").buildHttpRequest()

    then:
    ComputeVersion.DEFAULT.servicePath == "compute/v1/projects/"
    ComputeVersion.DEFAULT.batchPath == "batch/compute/v1"
    compute.getServicePath() == "compute/v1/projects/"
    request.getUrl().toString().contains("/compute/v1/projects/")
    !request.getUrl().toString().contains("/compute/beta/")
    compute.batch().getBatchUrl().toString().contains("/batch/compute/v1")
  }

  def "ALPHA compute client retains alpha service and batch paths"() {
    given:
    def credentials = new GoogleCredentials("test-project", ComputeVersion.ALPHA) {
      @Override
      HttpTransport buildHttpTransport() {
        return new MockHttpTransport.Builder().build()
      }

      @Override
      com.google.auth.oauth2.GoogleCredentials getCredentials() {
        LocalDate tomorrow = LocalDate.now().plusDays(1)
        return com.google.auth.oauth2.GoogleCredentials.create(
            new AccessToken("some-token", java.sql.Date.valueOf(tomorrow)))
      }
    }

    when:
    Compute compute = credentials.getCompute("spinnaker-test")
    def request = compute.projects().get("test-project").buildHttpRequest()

    then:
    ComputeVersion.ALPHA.servicePath == "compute/alpha/projects/"
    ComputeVersion.ALPHA.batchPath == "batch/compute/alpha"
    compute.getServicePath() == "compute/alpha/projects/"
    request.getUrl().toString().contains("/compute/alpha/projects/")
    compute.batch().getBatchUrl().toString().contains("/batch/compute/alpha")
  }
}
