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

package com.netflix.spinnaker.clouddriver.azure.resources.common.model

import com.azure.core.management.exception.ManagementError
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner
import com.azure.resourcemanager.resources.models.DeploymentOperation
import com.azure.resourcemanager.resources.models.StatusMessage
import com.azure.resourcemanager.resources.models.TargetResource
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.OffsetDateTime

class AzureDeploymentOperationSpec extends Specification {

  static final ObjectMapper MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  static DeploymentOperationInner inner(String json) {
    MAPPER.readValue(json, DeploymentOperationInner.class)
  }

  def "extractFailedResources returns empty for null/empty input"() {
    expect:
    AzureDeploymentOperation.extractFailedResources(null) == []
    AzureDeploymentOperation.extractFailedResources([]) == []
  }

  def "extractFailedResources skips ops with null targetResource (SDK noise filter)"() {
    given:
    def phantomInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "statusMessage": {"error": {"code": "X", "message": "phantom"}}
        }
      }''')
    def realInner = inner('''
      {
        "properties": {
          "provisioningState": "Failed",
          "statusMessage": {"error": {"code": "Y", "message": "real"}},
          "targetResource": {"resourceName": "vmss", "resourceType": "Microsoft.Compute/virtualMachineScaleSets"}
        }
      }''')
    def phantom = Mock(DeploymentOperation)
    phantom.innerModel() >> phantomInner
    def real = Mock(DeploymentOperation)
    real.innerModel() >> realInner

    when:
    def result = AzureDeploymentOperation.extractFailedResources([phantom, real])

    then:
    result.size() == 1
    result[0].resourceName == "vmss"
  }

  def "extractFailedResources skips Succeeded operations"() {
    given:
    def succeededInner = inner('''
      {
        "properties": {
          "provisioningState": "Succeeded",
          "targetResource": {"resourceName": "ok", "resourceType": "Microsoft.Network/networkInterfaces"}
        }
      }''')
    def succeeded = Mock(DeploymentOperation)
    succeeded.innerModel() >> succeededInner

    expect:
    AzureDeploymentOperation.extractFailedResources([succeeded]) == []
  }

  def "filterToRecentFailures keeps only failures within window of the most recent timestamp"() {
    given:
    def t = { OffsetDateTime ts ->
      new AzureDeploymentOperation.FailedResourceDetail("op", "r", "T", ts, "msg")
    }
    def latest = OffsetDateTime.parse("2026-05-07T20:00:00Z")
    def stale = t(latest.minusMinutes(30))
    def borderline = t(latest.minusMinutes(4))
    def fresh = t(latest)

    when:
    def kept = AzureDeploymentOperation.filterToRecentFailures([stale, borderline, fresh], Duration.ofMinutes(5))

    then:
    kept.size() == 2
    kept.contains(borderline)
    kept.contains(fresh)
    !kept.contains(stale)
  }

  def "filterToRecentFailures returns input when no timestamps are present (defensive)"() {
    given:
    def f1 = new AzureDeploymentOperation.FailedResourceDetail("op1", "r1", "T", null, "msg1")
    def f2 = new AzureDeploymentOperation.FailedResourceDetail("op2", "r2", "T", null, "msg2")

    expect:
    AzureDeploymentOperation.filterToRecentFailures([f1, f2], Duration.ofMinutes(5)) == [f1, f2]
  }

  def "filterToRecentFailures keeps null-timestamp failures defensively"() {
    given:
    def latest = OffsetDateTime.parse("2026-05-07T20:00:00Z")
    def fresh = new AzureDeploymentOperation.FailedResourceDetail("op1", "r1", "T", latest, "fresh")
    def unknown = new AzureDeploymentOperation.FailedResourceDetail("op2", "r2", "T", null, "unknown")

    when:
    def kept = AzureDeploymentOperation.filterToRecentFailures([fresh, unknown], Duration.ofMinutes(5))

    then:
    kept.size() == 2
  }

  @Unroll
  def "renderStatusMessage handles #scenario"() {
    expect:
    AzureDeploymentOperation.renderStatusMessage(input) == expected

    where:
    scenario                              | input                                                                                       | expected
    "null status"                         | null                                                                                        | "(no Azure error details)"
    "error with code and message"         | new StatusMessage().withError(new ManagementError("InvalidTemplate", "details here"))       | "InvalidTemplate: details here"
    "error with code only"                | new StatusMessage().withError(new ManagementError("OnlyCode", null))                        | "OnlyCode"
    "error with message only"             | new StatusMessage().withError(new ManagementError(null, "only-msg"))                        | "only-msg"
    "no error, status only"               | new StatusMessage().withStatus("Cancelled")                                                 | "Cancelled"
    "non-StatusMessage object"            | "raw string from interface API"                                                             | "raw string from interface API"
  }

  def "FailedResourceDetail.label combines type and name when type present"() {
    expect:
    new AzureDeploymentOperation.FailedResourceDetail(
      "op", "vmss", "Microsoft.Compute/virtualMachineScaleSets", null, "msg").label() ==
      "Microsoft.Compute/virtualMachineScaleSets/vmss"
  }

  def "FailedResourceDetail.label is just name when type is empty"() {
    expect:
    new AzureDeploymentOperation.FailedResourceDetail("op", "vmss", "", null, "msg").label() == "vmss"
  }
}
