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
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.config.KeelConfiguration
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask.Companion.UNAUTHORIZED_SCM_ACCESS_MESSAGE
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask.SpringHttpError
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.GitTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.mime.TypedInput
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
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
      } returns Response("http://keel", 200, "", emptyList(), null)
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

    val parsingError = SpringHttpError(
      status = BAD_REQUEST.value(),
      error = BAD_REQUEST.reasonPhrase,
      message = "Parsing error",
      details = mapOf(
        "message" to "Parsing error",
        "path" to listOf(
          mapOf(
            "type" to "SomeClass",
            "field" to "someField"
          )
        ),
        "pathExpression" to ".someField"
      ),
      // Jackson writes this as ms-since-epoch, so we need to strip off the nanoseconds, since we'll
      // be round-tripping it through Jackson before testing for equality.
      timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

    val accessDeniedError = SpringHttpError(
      status = FORBIDDEN.value(),
      error = FORBIDDEN.reasonPhrase,
      message = "Access denied",
      // Jackson writes this as ms-since-epoch, so we need to strip off the nanoseconds, since we'll
      // be round-tripping it through Jackson before testing for equality.
      timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )
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
            } throws RetrofitError.httpError(
              "http://igor",
              Response("http://igor", 404, "", emptyList(), null),
              null, null
            )
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
            } throws RetrofitError.httpError(
              "http://igor",
              Response("http://igor", 401, "", emptyList(), null),
              null, null
            )
          }
        }

        test("task fails with a helpful error message") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
          expectThat(result.context["error"]).isEqualTo(mapOf("message" to UNAUTHORIZED_SCM_ACCESS_MESSAGE))
        }
      }

      context("keel access denied error") {
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
            } throws RetrofitError.httpError(
              "http://keel",
              Response(
                "http://keel", 403, "", emptyList(),
                JacksonConverter(objectMapper).toBody(accessDeniedError) as TypedInput
              ),
              null, null
            )
          }
        }

        test("task fails and includes the error details returned by keel") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
          expectThat(result.context["error"]).isA<SpringHttpError>().isEqualTo(accessDeniedError)
        }
      }

      context("delivery config parsing error") {
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
            } throws RetrofitError.httpError(
              "http://keel",
              Response(
                "http://keel", 400, "", emptyList(),
                JacksonConverter(objectMapper).toBody(parsingError) as TypedInput
              ),
              null, null
            )
          }
        }

        test("task fails and includes the error details returned by keel") {
          val result = execute(manifestLocation.toMap())
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
          expectThat(result.context["error"]).isA<SpringHttpError>().isEqualTo(parsingError)
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
            } throws RetrofitError.httpError(
              "http://igor",
              Response("http://igor", 503, "", emptyList(), null),
              null, null
            )
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
