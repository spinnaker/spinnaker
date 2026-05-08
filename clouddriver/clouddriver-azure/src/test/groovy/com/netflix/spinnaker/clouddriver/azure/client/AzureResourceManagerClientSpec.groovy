/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.azure.client

import com.azure.core.http.HttpResponse
import com.azure.core.management.exception.ManagementError
import com.azure.core.management.exception.ManagementException
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner
import com.azure.resourcemanager.resources.models.DeploymentOperation
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import spock.lang.Specification

class AzureResourceManagerClientSpec extends Specification {

  static final ObjectMapper MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  static DeploymentOperationInner inner(String json) {
    MAPPER.readValue(json, DeploymentOperationInner.class)
  }

  def "enrichWithDeploymentOperationErrors returns the original message when there are no operations"() {
    expect:
    AzureResourceManagerClient.enrichWithDeploymentOperationErrors("Long running operation failed.", []) ==
      "Long running operation failed."
  }

  def "enrichWithDeploymentOperationErrors returns the original message when no operations failed"() {
    given:
    def succeededInner = inner('''
      {
        "properties": {
          "provisioningState": "Succeeded",
          "targetResource": {"resourceName": "my-nic", "resourceType": "Microsoft.Network/networkInterfaces"}
        }
      }''')
    def succeeded = Mock(DeploymentOperation)
    succeeded.innerModel() >> succeededInner

    expect:
    AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [succeeded]) == "Long running operation failed."
  }

  def "enrichWithDeploymentOperationErrors appends statusMessage from a single FAILED operation"() {
    given:
    def failedInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:00Z",
          "statusMessage": {
            "error": {
              "code": "InvalidTemplate",
              "message": "Resource Microsoft.Compute/virtualMachineScaleSets does not support availability zones at location 'westus'."
            }
          },
          "targetResource": {"resourceName": "my-vmss", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def failed = Mock(DeploymentOperation)
    failed.innerModel() >> failedInner

    when:
    def enriched = AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [failed])

    then:
    enriched.startsWith("Long running operation failed.")
    enriched.contains("my-vmss")
    enriched.contains("InvalidTemplate")
    enriched.contains("does not support availability zones at location 'westus'")
  }

  def "enrichWithDeploymentOperationErrors joins multiple FAILED operations and skips successful ones"() {
    given:
    def failedAInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:00Z",
          "statusMessage": {"error": {"code": "InvalidTemplate", "message": "first error"}},
          "targetResource": {"resourceName": "vmss-a", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def failedBInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:01Z",
          "statusMessage": {"error": {"code": "InvalidResourceReference", "message": "second error"}},
          "targetResource": {"resourceName": "lb-b", "resourceType": "Microsoft.Network/loadBalancers"}
        }
      }''')
    def succeededInner = inner('''
      {
        "properties": {
          "provisioningState": "Succeeded",
          "targetResource": {"resourceName": "nic-c", "resourceType": "Microsoft.Network/networkInterfaces"}
        }
      }''')
    def failedA = Mock(DeploymentOperation)
    failedA.innerModel() >> failedAInner
    def failedB = Mock(DeploymentOperation)
    failedB.innerModel() >> failedBInner
    def succeeded = Mock(DeploymentOperation)
    succeeded.innerModel() >> succeededInner

    when:
    def enriched = AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [failedA, succeeded, failedB])

    then:
    enriched.contains("vmss-a")
    enriched.contains("first error")
    enriched.contains("lb-b")
    enriched.contains("second error")
    !enriched.contains("nic-c")
    enriched.contains(" | ")
  }

  def "enrichWithDeploymentOperationErrors falls back gracefully when statusMessage is absent"() {
    given:
    def failedInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:00Z",
          "targetResource": {"resourceName": "orphan", "resourceType": "Microsoft.Resources/deployments"}
        }
      }''')
    def failed = Mock(DeploymentOperation)
    failed.innerModel() >> failedInner

    when:
    def enriched = AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [failed])

    then:
    enriched.startsWith("Long running operation failed.")
    enriched.contains("orphan")
  }

  def "enrichWithDeploymentOperationErrors skips operations missing a target resource (SDK noise)"() {
    given:
    def phantomInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:00Z",
          "statusMessage": {"error": {"code": "DeploymentFailed", "message": "phantom op with no target"}}
        }
      }''')
    def realInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T20:00:00Z",
          "statusMessage": {"error": {"code": "InvalidTemplate", "message": "real failure"}},
          "targetResource": {"resourceName": "my-vmss", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def phantom = Mock(DeploymentOperation)
    phantom.innerModel() >> phantomInner
    def real = Mock(DeploymentOperation)
    real.innerModel() >> realInner

    when:
    def enriched = AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [phantom, real])

    then:
    !enriched.contains("phantom op")
    !enriched.contains("<unknown>")
    enriched.contains("my-vmss")
    enriched.contains("real failure")
  }

  def "enrichWithDeploymentOperationErrors filters out stale failures from prior retries by timestamp"() {
    given:
    // Two failures: one from 30 minutes ago (stale prior attempt), one fresh (~now in window)
    def staleInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T19:00:00Z",
          "statusMessage": {"error": {"code": "OldError", "message": "stale failure from prior retry"}},
          "targetResource": {"resourceName": "stale-resource", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def freshInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "timestamp": "2026-05-07T19:30:00Z",
          "statusMessage": {"error": {"code": "FreshError", "message": "actual current failure"}},
          "targetResource": {"resourceName": "fresh-resource", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def stale = Mock(DeploymentOperation)
    stale.innerModel() >> staleInner
    def fresh = Mock(DeploymentOperation)
    fresh.innerModel() >> freshInner

    when:
    def enriched = AzureResourceManagerClient.enrichWithDeploymentOperationErrors(
      "Long running operation failed.", [stale, fresh])

    then:
    !enriched.contains("stale failure from prior retry")
    !enriched.contains("stale-resource")
    enriched.contains("fresh-resource")
    enriched.contains("actual current failure")
  }

  def "wrapAsRichException preserves ManagementException type"() {
    given:
    def response = Mock(HttpResponse) { getStatusCode() >> 400 }
    def value = new ManagementError("Original", "original message")
    def original = new ManagementException("Long running operation failed.", response, value)

    when:
    def wrapped = AzureResourceManagerClient.wrapAsRichException("enriched message", original)

    then:
    wrapped instanceof ManagementException
    wrapped.message == "enriched message"
    ((ManagementException) wrapped).getResponse() == response
    ((ManagementException) wrapped).getValue() == value
    wrapped.cause == original
  }

  def "wrapAsRichException wraps non-Management exceptions in RuntimeException"() {
    given:
    def original = new IllegalStateException("boom")

    when:
    def wrapped = AzureResourceManagerClient.wrapAsRichException("enriched", original)

    then:
    wrapped.class == RuntimeException
    wrapped.message == "enriched"
    wrapped.cause == original
  }
}
