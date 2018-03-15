/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kayenta

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.netflix.spinnaker.orca.kayenta.config.KayentaConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import retrofit.Endpoint
import retrofit.Endpoints.newFixedEndpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter.LogLevel
import retrofit.client.OkClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID.randomUUID

object KayentaServiceTest : Spek({

  val wireMockServer = WireMockServer(options().dynamicPort())
  lateinit var subject: KayentaService
  beforeGroup {
    wireMockServer.start()
    configureFor(wireMockServer.port())
    subject = KayentaConfiguration()
      .kayentaService(
        OkClient(),
        wireMockServer.endpoint,
        LogLevel.FULL,
        RequestInterceptor.NONE
      )
  }
  afterGroup(wireMockServer::stop)

  describe("the Kayenta service") {

    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val canaryConfigId = randomUUID().toString()
    val mapper = jacksonObjectMapper()

    describe("creating a new canary") {

      val startTime = clock.instant()
      val endTime = startTime.plus(24, HOURS)

      beforeGroup {
        stubFor(
          post(urlPathEqualTo("/canary/$canaryConfigId"))
            .willReturn(
              aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{}")
            )
        )
      }

      on("posting the request to Kayenta") {
        subject.create(
          canaryConfigId = canaryConfigId,
          application = "covfefe",
          parentPipelineExecutionId = randomUUID().toString(),
          metricsAccountName = null,
          configurationAccountName = null,
          storageAccountName = null,
          canaryExecutionRequest = CanaryExecutionRequest(
            scopes = mapOf("canary" to CanaryScopes(
              CanaryScope(
                scope = "covfefe-control",
                region = "us-west-2",
                start = startTime,
                end = endTime
              ),
              CanaryScope(
                scope = "covfefe-experiment",
                region = "us-west-2",
                start = startTime,
                end = endTime
              )
            )),
            thresholds = Thresholds(pass = 50, marginal = 75)
          )
        )
      }

      it("renders timestamps as ISO strings") {
        findAll(postRequestedFor(urlPathEqualTo("/canary/$canaryConfigId"))).let {
          assertThat(it).hasSize(1)
          mapper.readTree(it.first().bodyAsString).apply {
            assertThat(at("/scopes/canary/controlScope/start").textValue())
              .isEqualTo(startTime.toString())
            assertThat(at("/scopes/canary/controlScope/end").textValue())
              .isEqualTo(endTime.toString())
            assertThat(at("/scopes/canary/experimentScope/start").textValue())
              .isEqualTo(startTime.toString())
            assertThat(at("/scopes/canary/experimentScope/end").textValue())
              .isEqualTo(endTime.toString())
          }
        }
      }
    }

    describe("fetching canary results") {

      given("a successful canary result") {
        val canaryId = "666fa25b-b0c6-421b-b84f-f93826932994"
        val storageAccountName = "my-google-account"

        val responseJson = """
{
  "application": "myapp",
  "parentPipelineExecutionId": "9cf4ec2e-29fb-4968-ae60-9182b575b30a",
  "pipelineId": "$canaryId",
  "stageStatus": {
    "fetchControl2": "succeeded",
    "fetchControl1": "succeeded",
    "fetchControl0": "succeeded",
    "mixMetrics": "succeeded",
    "fetchExperiment0": "succeeded",
    "fetchExperiment1": "succeeded",
    "fetchExperiment2": "succeeded",
    "judge": "succeeded",
    "setupContext": "succeeded",
    "fetchExperiment3": "succeeded",
    "fetchControl3": "succeeded"
  },
  "complete": true,
  "status": "succeeded",
  "result": {
    "application": "myapp",
    "parentPipelineExecutionId": "9cf4ec2e-29fb-4968-ae60-9182b575b30a",
    "judgeResult": {
      "judgeName": "dredd-v1.0",
      "results": [
        {
          "name": "cpu1",
          "id": "2fdb4816-923e-4b05-a496-12ff7d1ca119",
          "classification": "Low",
          "groups": [
            "system"
          ],
          "experimentMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          },
          "controlMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          }
        },
        {
          "name": "cpu2",
          "id": "0a4323e9-9a67-40ac-9cf5-397dcca11f69",
          "classification": "Pass",
          "groups": [
            "system"
          ],
          "experimentMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          },
          "controlMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          }
        },
        {
          "name": "cpu3",
          "id": "e31d07b6-c805-410a-afe0-a6e59b2f5162",
          "classification": "High",
          "groups": [
            "application"
          ],
          "experimentMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          },
          "controlMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          }
        },
        {
          "name": "cpu4",
          "id": "03d87914-235d-41b6-9081-567551a4a0bf",
          "classification": "High",
          "groups": [
            "application"
          ],
          "experimentMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          },
          "controlMetadata": {
            "stats": {
              "count": 60,
              "mean": 1.0044959745072346,
              "min": 0.9985115845726492,
              "max": 1.1198433858808128,
              "median": 0.9999258832637375
            }
          }
        }
      ],
      "groupScores": [
        {
          "name": "application",
          "score": 0,
          "classification": "",
          "classificationReason": ""
        },
        {
          "name": "system",
          "score": 50,
          "classification": "",
          "classificationReason": ""
        }
      ],
      "score": {
        "score": 33,
        "classification": "Fail",
        "classificationReason": ""
      }
    },
    "config": {
      "createdTimestamp": 1513798922188,
      "updatedTimestamp": 1517067740081,
      "createdTimestampIso": "2017-12-20T19:42:02.188Z",
      "updatedTimestampIso": "2018-01-27T15:42:20.081Z",
      "name": "MySampleStackdriverCanaryConfig",
      "description": "Example Automated Canary Analysis (ACA) Configuration using Stackdriver",
      "configVersion": "1.0",
      "applications": [
        "myapp"
      ],
      "judge": {
        "name": "dredd-v1.0",
        "judgeConfigurations": {}
      },
      "metrics": [
        {
          "name": "cpu1",
          "query": {
            "type": "stackdriver",
            "metricType": "compute.googleapis.com/instance/cpu/utilization",
            "serviceType": "stackdriver"
          },
          "groups": [
            "system"
          ],
          "analysisConfigurations": {},
          "scopeName": "default"
        },
        {
          "name": "cpu2",
          "query": {
            "type": "stackdriver",
            "metricType": "compute.googleapis.com/instance/cpu/utilization",
            "serviceType": "stackdriver"
          },
          "groups": [
            "system"
          ],
          "analysisConfigurations": {},
          "scopeName": "default"
        },
        {
          "name": "cpu3",
          "query": {
            "type": "stackdriver",
            "metricType": "compute.googleapis.com/instance/cpu/utilization",
            "serviceType": "stackdriver"
          },
          "groups": [
            "application"
          ],
          "analysisConfigurations": {},
          "scopeName": "default"
        },
        {
          "name": "cpu4",
          "query": {
            "type": "stackdriver",
            "metricType": "compute.googleapis.com/instance/cpu/utilization",
            "serviceType": "stackdriver"
          },
          "groups": [
            "application"
          ],
          "analysisConfigurations": {},
          "scopeName": "default"
        }
      ],
      "classifier": {
        "groupWeights": {
          "system": 66,
          "application": 34
        },
        "scoreThresholds": {
          "pass": 95,
          "marginal": 75
        }
      }
    },
    "canaryExecutionRequest": {
      "scopes": {
        "default": {
          "controlScope": {
            "scope": "myapp-v059",
            "region": "us-central1",
            "start": "2018-02-15T20:05:00Z",
            "end": "2018-02-15T21:05:00Z",
            "step": 60,
            "extendedScopeParams": {
              "resourceType": "gce_instance"
            }
          },
          "experimentScope": {
            "scope": "myapp-v059",
            "region": "us-central1",
            "start": "2018-02-15T20:05:00Z",
            "end": "2018-02-15T21:05:00Z",
            "step": 60,
            "extendedScopeParams": {
              "resourceType": "gce_instance"
            }
          }
        }
      },
      "thresholds": {
        "pass": 60,
        "marginal": 40
      }
    },
    "metricSetPairListId": "581dd9b7-5a0d-42c2-aecc-c6e6600d2591",
    "canaryDuration": "PT1H",
    "pipelineId": "666fa25b-b0c6-421b-b84f-f93826932994"
  },
  "buildTimeMillis": 1521059331684,
  "buildTimeIso": "2018-03-14T20:28:51.684Z",
  "startTimeMillis": 1521059331909,
  "startTimeIso": "2018-03-14T20:28:51.909Z",
  "endTimeMillis": 1521059341101,
  "endTimeIso": "2018-03-14T20:29:01.101Z",
  "storageAccountName": "my-google-account"
}
"""

        beforeGroup {
          stubFor(
            get(urlPathEqualTo("/canary/$canaryId"))
              .withQueryParam("storageAccountName", equalTo(storageAccountName))
              .willReturn(
                aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withBody(responseJson)
              )
          )
        }

        it("parses the JSON response correctly") {
          subject.getCanaryResults(storageAccountName, canaryId)
            .let { response ->
              assertThat(response.complete).isTrue()
              assertThat(response.buildTimeIso).isEqualTo(Instant.ofEpochMilli(1521059331684))
              assertThat(response.endTimeIso).isEqualTo(Instant.ofEpochMilli(1521059341101))
              assertThat(response.startTimeIso).isEqualTo(Instant.ofEpochMilli(1521059331909))
              assertThat(response.result!!.application).isEqualTo("myapp")
              assertThat(response.result!!.canaryDuration).isEqualTo(Duration.ofHours(1))
              assertThat(response.result!!.judgeResult.score.score).isEqualTo(33)
              assertThat(response.exception).isNull()
            }
        }
      }

      given("an exception") {

        val canaryId = "02a95d21-290c-49f9-8be1-fb0b7779a73a"
        val storageAccountName = "my-google-account"

        val responseJson = """
{
  "application": "myapp",
  "parentPipelineExecutionId": "88086da2-3e5a-4a9e-ada7-089ab70a9578",
  "pipelineId": "02a95d21-290c-49f9-8be1-fb0b7779a73a",
  "stageStatus": {
    "fetchControl0": "terminal",
    "mixMetrics": "not_started",
    "fetchExperiment0": "terminal",
    "judge": "not_started",
    "setupContext": "succeeded"
  },
  "complete": true,
  "status": "terminal",
  "exception": {
    "timestamp": 1521127342802,
    "exceptionType": "IllegalArgumentException",
    "operation": "prometheusFetch",
    "details": {
      "stackTrace": "java.lang.IllegalArgumentException: Either a resource type or a custom filter is required.\n\tat com.netflix.kayenta.prometheus.metrics.PrometheusMetricsService.addScopeFilter(PrometheusMetricsService.java:112)\n\tat com.netflix.kayenta.prometheus.metrics.PrometheusMetricsService.queryMetrics(PrometheusMetricsService.java:222)\n",
      "error": "Unexpected Task Failure",
      "errors": [
        "Either a resource type or a custom filter is required."
      ]
    },
    "shouldRetry": false
  },
  "buildTimeMillis": 1521127342562,
  "buildTimeIso": "2018-03-15T15:22:22.562Z",
  "startTimeMillis": 1521127342569,
  "startTimeIso": "2018-03-15T15:22:22.569Z",
  "endTimeMillis": 1521127342887,
  "endTimeIso": "2018-03-15T15:22:22.887Z",
  "storageAccountName": "my-google-account"
}
"""

        beforeGroup {
          stubFor(
            get(urlPathEqualTo("/canary/$canaryId"))
              .withQueryParam("storageAccountName", equalTo(storageAccountName))
              .willReturn(
                aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withBody(responseJson)
              )
          )
        }

        it("parses the JSON response correctly") {
          subject.getCanaryResults(storageAccountName, canaryId)
            .let { response ->
              assertThat(response.complete).isTrue()
              assertThat(response.result).isNull()
              assertThat(response.exception)
                .containsEntry("exceptionType", "IllegalArgumentException")
            }
        }
      }
    }
  }
})

val WireMockServer.endpoint: Endpoint
  get() = newFixedEndpoint(url("/"))
