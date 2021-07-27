package com.netflix.spinnaker.keel.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.RawDeliveryConfigResult
import com.netflix.spinnaker.keel.igor.model.Branch
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.configuredTestYamlMapper
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.mockk
import retrofit.RetrofitError
import retrofit.client.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull

class DeliveryConfigImporterTests : JUnit5Minutests {
  object Fixture {
    val yamlMapper = configuredTestYamlMapper()
      .registerDummyResource()
    val scmService: ScmService = mockk()
    val front50Cache: Front50Cache = mockk()
    val application = Application(
      name = "test",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "proj",
      repoSlug = "repo"
    )
    val deliveryConfig: DeliveryConfig = deliveryConfig().run {
      copy(rawConfig = yamlMapper.writeValueAsString(this))
    }
    val submittedDeliveryConfig: SubmittedDeliveryConfig = yamlMapper.convertValue(deliveryConfig)
    val importer = DeliveryConfigImporter(scmService, front50Cache, yamlMapper)

    private fun <T : ObjectMapper> T.registerDummyResource() = apply {
      registerSubtypes(
        NamedType(DummyResourceSpec::class.java, "test/whatever@v1")
      )
    }
  }

  fun tests() = rootContext<Fixture> {
    context("import") {
      fixture { Fixture }

      context("with a valid delivery config in source control") {
        before {
          every {
            scmService.getDeliveryConfigManifest("stash", "proj", "repo", "spinnaker.yml", any(), true)
          } returns RawDeliveryConfigResult(manifest = deliveryConfig.rawConfig!!)
        }

        test("succeeds with metadata added") {
          val result = importer.import(
            repoType = "stash",
            projectKey = "proj",
            repoSlug = "repo",
            manifestPath = "spinnaker.yml",
            ref = "refs/heads/master"
          )
          expectThat(result).isEqualTo(
            submittedDeliveryConfig.copy(
              metadata = mapOf(
                "importedFrom" to
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

        test("succeeds without metadata added") {
          val result = importer.import(
            repoType = "stash",
            projectKey = "proj",
            repoSlug = "repo",
            manifestPath = "spinnaker.yml",
            ref = "refs/heads/master",
            addMetadata = false
          )
          expectThat(result).isEqualTo(submittedDeliveryConfig)
        }

        context("from application config") {
          before {
            every {
              front50Cache.applicationByName("test")
            } returns application

            every {
              scmService.getDefaultBranch("stash", "proj", "repo")
            } returns Branch("main", "refs/heads/main", true)
          }

          test("retrieves the delivery config from the default branch without metadata") {
            val result = importer.import("test", addMetadata = false)
            expectThat(result).isEqualTo(submittedDeliveryConfig)
          }
        }
      }

      context("with HTTP error retrieving delivery config from igor") {
        val retrofitError = RetrofitError.httpError(
          "http://igor",
          Response("http://igor", 404, "not found", emptyList(), null),
          null, null
        )

        before {
          every {
            scmService.getDeliveryConfigManifest("stash", "proj", "repo", "spinnaker.yml", any(), any())
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
