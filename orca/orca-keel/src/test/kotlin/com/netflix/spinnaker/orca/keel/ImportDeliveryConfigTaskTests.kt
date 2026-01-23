/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.keel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.config.KeelConfiguration
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.keel.model.DeliveryConfig
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask.Companion.UNAUTHORIZED_SCM_ACCESS_MESSAGE
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.GitTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull

internal class ImportDeliveryConfigTaskTests : JUnit5Minutests {
  data class ManifestLocation(
    val repoType: String,
    val projectKey: String,
    val repositorySlug: String,
    val directory: String,
    val manifest: String,
    val ref: String
  )

  data class Fixture(
    val trigger: Trigger,
    val manifestLocation: ManifestLocation = ManifestLocation(
      repoType = "stash",
      projectKey = "SPKR",
      repositorySlug = "keeldemo",
      directory = ".",
      manifest = "spinnaker.yml",
      ref = "refs/heads/master"
    )
  ) {
    companion object {
      val objectMapper: ObjectMapper = KeelConfiguration().keelObjectMapper()
    }

    val manifest = mapOf(
      "name" to "keeldemo-manifest",
      "application" to "keeldemo",
      "artifacts" to emptySet<Map<String, Any?>>(),
      "environments" to emptySet<Map<String, Any?>>()
    )

    val scmService: ScmService = mockk(relaxUnitFun = true) {
      every {
        getDeliveryConfigManifest(any(), any(), any(), any(), any(), any())
      } returns manifest
    }

    val keelService: KeelService = mockk(relaxUnitFun = true) {
      every {
        publishDeliveryConfig(any())
      } answers {
        Calls.response(ResponseBody.create("application/json".toMediaTypeOrNull(), "[]"))
      }
    }

    val subject = ImportDeliveryConfigTask(keelService, scmService, objectMapper)

    fun execute(context: Map<String, Any?>) =
      subject.execute(
        StageExecutionImpl(
          PipelineExecutionImpl(ExecutionType.PIPELINE, "keeldemo").also { it.trigger = trigger },
          ExecutionType.PIPELINE.toString(),
          context
        )
      )
  }

  private fun makeSpinnakerHttpException(status: Int, message: String? = "Response.error()"): SpinnakerHttpException {
    val body = ResponseBody.create("application/json".toMediaTypeOrNull(), "[]")
    val url = "https://igor"
    val rawResponse = okhttp3.Response.Builder()
      .body(body)
      .code(status)
      .message(message.toString())
      .protocol(Protocol.HTTP_1_1)
      .request((okhttp3.Request.Builder())
        .url(url)
        .build())
      .build()
    val response = Response.error<Any>(body, rawResponse)
    val retrofit = Retrofit.Builder()
      .baseUrl(url)
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
    return SpinnakerHttpException(response, retrofit)
  }

  private fun ManifestLocation.toMap() =
    Fixture.objectMapper.convertValue<Map<String, Any?>>(this).toMutableMap()

  private val objectMapper = Fixture.objectMapper

  fun tests() = rootContext<Fixture> {
    context("basic behavior") {
      fixture {
        Fixture(
          DefaultTrigger("manual")
        )
      }
      test("successfully retrieves manifest from SCM and publishes to keel") {
        val result = execute(manifestLocation.toMap())
        expectThat(result.status).isEqualTo(SUCCEEDED)
        verify(exactly = 1) {
          scmService.getDeliveryConfigManifest(
            manifestLocation.repoType,
            manifestLocation.projectKey,
            manifestLocation.repositorySlug,
            manifestLocation.directory,
            manifestLocation.manifest,
            manifestLocation.ref
          )
        }
        verify(exactly = 1) {
          keelService.publishDeliveryConfig(manifest)
        }
      }
    }

    context("with manual trigger") {
      fixture {
        Fixture(
          DefaultTrigger("manual")
        )
      }

      context("with required stage context missing") {
        test("throws an exception") {
          expectThrows<InvalidRequestException> {
            execute(manifestLocation.toMap().also { it.remove("repoType") })
          }
        }
      }

      context("with optional stage context missing") {
        test("uses defaults to fill in the blanks") {
          val result = execute(
            manifestLocation.toMap().also {
              it.remove("directory")
              it.remove("manifest")
              it.remove("ref")
            }
          )
          expectThat(result.status).isEqualTo(SUCCEEDED)
          verify(exactly = 1) {
            scmService.getDeliveryConfigManifest(
              manifestLocation.repoType,
              manifestLocation.projectKey,
              manifestLocation.repositorySlug,
              null,
              "spinnaker.yml",
              "refs/heads/master"
            )
          }
        }
      }
    }

    context("with git trigger") {
      fixture {
        Fixture(
          GitTrigger(
            source = "stash",
            project = "other",
            slug = "other",
            branch = "master",
            hash = "bea43e7033e19327183416f23fe2ee1b64c25f4a",
            action = "n/a"
          )
        )
      }

      context("with fully-populated stage context") {
        test("disregards trigger and uses context information to retrieve manifest from SCM") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(SUCCEEDED)
          verify(exactly = 1) {
            scmService.getDeliveryConfigManifest(
              manifestLocation.repoType,
              manifestLocation.projectKey,
              manifestLocation.repositorySlug,
              manifestLocation.directory,
              manifestLocation.manifest,
              manifestLocation.ref
            )
          }
        }
      }

      context("with some missing information in stage context") {
        test("uses trigger information to fill in the blanks") {
          val result = execute(
            manifestLocation.toMap().also {
              it.remove("projectKey")
              it.remove("repositorySlug")
              it.remove("ref")
            }
          )
          expectThat(result.status).isEqualTo(SUCCEEDED)
          verify(exactly = 1) {
            scmService.getDeliveryConfigManifest(
              manifestLocation.repoType,
              (trigger as GitTrigger).project,
              trigger.slug,
              manifestLocation.directory,
              manifestLocation.manifest,
              trigger.hash
            )
          }
        }
      }

      context("with no information in stage context") {
        test("uses trigger information and defaults to fill in the blanks") {
          val result = execute(mutableMapOf())
          expectThat(result.status).isEqualTo(SUCCEEDED)
          verify(exactly = 1) {
            scmService.getDeliveryConfigManifest(
              (trigger as GitTrigger).source,
              trigger.project,
              trigger.slug,
              null,
              "spinnaker.yml",
              trigger.hash
            )
          }
        }
      }
    }

    context("with detailed git info in payload field") {
      val trigger = objectMapper.readValue(javaClass.getResource("/trigger.json"), Trigger::class.java)
      fixture {
        Fixture(
          trigger
        )
      }

      context("parsing git metadata") {
        test("parses correctly") {
          execute(mutableMapOf())
          val submittedConfig = slot<DeliveryConfig>()
          verify(exactly = 1) {
            keelService.publishDeliveryConfig(capture(submittedConfig))
          }
          val m: Any = submittedConfig.captured.getOrDefault("metadata", emptyMap<String, Any?>())!!
          val metadata: Map<String, Any?> = objectMapper.convertValue(m)
          expectThat(metadata["gitMetadata"]).isNotEqualTo(null)
        }
      }
    }

    context("additional error handling behavior") {
      fixture {
        Fixture(
          DefaultTrigger("manual")
        )
      }

      context("manifest is not found") {
        modifyFixture {
          with(scmService) {
            every {
              getDeliveryConfigManifest(
                manifestLocation.repoType,
                manifestLocation.projectKey,
                manifestLocation.repositorySlug,
                manifestLocation.directory,
                manifestLocation.manifest,
                manifestLocation.ref
              )
            } throws makeSpinnakerHttpException(404)
          }
        }

        test("task fails if manifest not found") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
        }
      }

      context("unauthorized access to manifest") {
        modifyFixture {
          with(scmService) {
            every {
              getDeliveryConfigManifest(
                manifestLocation.repoType,
                manifestLocation.projectKey,
                manifestLocation.repositorySlug,
                manifestLocation.directory,
                manifestLocation.manifest,
                manifestLocation.ref
              )
            } throws makeSpinnakerHttpException(401)
          }
        }

        test("task fails with a helpful error message") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
          expectThat(result.context["error"]).isEqualTo(mapOf("message" to UNAUTHORIZED_SCM_ACCESS_MESSAGE))
        }
      }

      context("retryable failure to call downstream services") {
        modifyFixture {
          with(scmService) {
            every {
              getDeliveryConfigManifest(
                manifestLocation.repoType,
                manifestLocation.projectKey,
                manifestLocation.repositorySlug,
                manifestLocation.directory,
                manifestLocation.manifest,
                manifestLocation.ref
              )
            } throws makeSpinnakerHttpException(503)
          }
        }

        test("task retries if max retries not reached") {
          var result: TaskResult
          for (attempt in 1 until ImportDeliveryConfigTask.MAX_RETRIES) {
            result = execute(manifestLocation.toMap().also { it["attempt"] = attempt })
            expectThat(result.status).isEqualTo(ExecutionStatus.RUNNING)
            expectThat(result.context["attempt"]).isEqualTo(attempt + 1)
          }
        }

        test("task fails if max retries reached") {
          val result = execute(manifestLocation.toMap().also { it["attempt"] = ImportDeliveryConfigTask.MAX_RETRIES })
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
        }

        test("task result context includes the error from the last attempt") {
          var result: TaskResult? = null
          for (attempt in 1..ImportDeliveryConfigTask.MAX_RETRIES) {
            result = execute(manifestLocation.toMap().also { it["attempt"] = attempt })
          }
          expectThat(result!!.context["errorFromLastAttempt"]).isNotNull()
          expectThat(result!!.context["error"] as String).contains(result!!.context["errorFromLastAttempt"] as String)
        }
      }
    }

    context("malformed input") {
      fixture {
        Fixture(
          trigger = DefaultTrigger("manual"),
          manifestLocation = ManifestLocation(
            repoType = "stash",
            projectKey = "SPKR",
            repositorySlug = "keeldemo",
            directory = ".",
            manifest = "",
            ref = "refs/heads/master"
          )
        )
      }

      test("successfully retrieves manifest from SCM and publishes to keel") {
        val result = execute(manifestLocation.toMap())

        expectThat(result.status) isEqualTo SUCCEEDED

        verify(exactly = 1) {
          scmService.getDeliveryConfigManifest(any(), any(), any(), any(), "spinnaker.yml", any())
        }
      }
    }
  }
}
