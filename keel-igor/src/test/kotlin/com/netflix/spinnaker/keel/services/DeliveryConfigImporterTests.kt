package com.netflix.spinnaker.keel.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.igor.ScmService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.mockk
import retrofit.RetrofitError
import retrofit.client.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

class DeliveryConfigImporterTests : JUnit5Minutests {
  object Fixture {
    val jsonMapper: ObjectMapper = configuredObjectMapper()
      .also {
        it.registerSubtypes(
          NamedType(DummyResourceSpec::class.java, "test/whatever@v1")
        )
      }
    val scmService: ScmService = mockk()
    val deliveryConfig: DeliveryConfig = deliveryConfig()
    val submittedDeliveryConfig: SubmittedDeliveryConfig = jsonMapper.convertValue(deliveryConfig)
    val importer = DeliveryConfigImporter(jsonMapper, scmService)
  }

  fun tests() = rootContext<Fixture> {
    context("import") {
      fixture { Fixture }

      context("with a valid delivery config in source control") {
        before {
          every {
            scmService.getDeliveryConfigManifest("stash", "proj", "repo", "spinnaker.yml", any())
          } returns jsonMapper.convertValue(submittedDeliveryConfig)
        }

        test("succeeds and includes import metadata") {
          val result = importer.import(
            repoType = "stash",
            projectKey = "proj",
            repoSlug = "repo",
            manifestPath = "spinnaker.yml",
            ref = "refs/heads/master"
          )
          expectThat(result).isEqualTo(
            submittedDeliveryConfig.copy(
              metadata = mapOf("importedFrom" to
                mapOf(
                  "repoType" to "stash",
                  "projectKey" to "proj",
                  "repoSlug" to "repo",
                  "manifestPath" to "spinnaker.yml",
                  "ref" to "refs/heads/master"
                )
              )
            )
          )
        }
      }

      context("with HTTP error retrieving delivery config from igor") {
        val retrofitError = RetrofitError.httpError("http://igor",
          Response("http://igor", 404, "not found", emptyList(), null),
          null, null)

        before {
          every {
            scmService.getDeliveryConfigManifest("stash", "proj", "repo", "spinnaker.yml", any())
          } throws retrofitError
        }

        test("bubbles up HTTP error") {
          expectCatching {
            importer.import(
              repoType = "stash",
              projectKey = "proj",
              repoSlug = "repo",
              manifestPath = "spinnaker.yml",
              ref = "refs/heads/master"
            )
          }
            .isFailure()
            .isEqualTo(retrofitError)
        }
      }
    }
  }
}
