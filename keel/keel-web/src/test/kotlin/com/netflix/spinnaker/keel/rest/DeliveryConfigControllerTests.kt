package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.auth.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.auth.AuthorizationSupport.TargetEntity.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.auth.PermissionLevel.READ
import com.netflix.spinnaker.keel.auth.PermissionLevel.WRITE
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.jackson.readValueInliningAliases
import com.netflix.spinnaker.keel.notifications.DismissibleNotification
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import retrofit.RetrofitError
import retrofit.client.Response
import strikt.api.expectThat
import strikt.jackson.at
import strikt.jackson.isMissing


@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class DeliveryConfigControllerTests
@Autowired constructor(
  val mvc: MockMvc,
  val jsonMapper: ObjectMapper,
  val yamlMapper: YAMLMapper,
) : JUnit5Minutests {

  @MockkBean
  lateinit var repository: KeelRepository

  @MockkBean
  lateinit var notificationRepository: DismissibleNotificationRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @MockkBean
  lateinit var importer: DeliveryConfigImporter

  @MockkBean
  lateinit var front50Cache: Front50Cache

  private val deliveryConfig = SubmittedDeliveryConfig(
    name = "keel-manifest",
    application = "keel",
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(
      DebianArtifact(
        name = "keel",
        vmOptions = VirtualMachineOptions(
          baseOs = "bionic",
          regions = setOf("us-west-2")
        )
      )
    ),
    environments = setOf(
      SubmittedEnvironment(
        name = "test",
        resources = setOf(
          SubmittedResource(
            kind = TEST_API_V1.qualify("whatever"),
            spec = DummyResourceSpec(data = "resource in test")
          )
        )
      ),
      SubmittedEnvironment(
        name = "prod",
        resources = setOf(
          SubmittedResource(
            kind = TEST_API_V1.qualify("whatever"),
            spec = DummyResourceSpec(data = "resource in prod")
          )
        ),
        constraints = setOf(DependsOnConstraint("test"))
      )
    ),
    previewEnvironments = setOf(
      PreviewEnvironmentSpec(
        branch = BranchFilter(startsWith = "feature/"),
        baseEnvironment = "test"
      )
    )
  )

  fun tests() = rootContext {
    before {
      authorizationSupport.allowAll()
    }

    after {
      clearAllMocks()
    }

    context("getting a delivery config manifest") {
      before {
        every { repository.getDeliveryConfig(deliveryConfig.safeName) } returns deliveryConfig.toDeliveryConfig()
      }

      mapOf(
        APPLICATION_YAML to yamlMapper,
        APPLICATION_JSON to jsonMapper
      ).forEach { (contentType, mapper) ->
        derivedContext<ResultActions>("getting a delivery config as $contentType") {
          fixture {
            val request = get("/delivery-configs/keel-manifest")
              .accept(contentType)

            mvc.perform(request)
          }

          test("the request is successful") {
            andExpect(status().isOk)
          }

          test("the response content type is correct") {
            andExpect(content().contentTypeCompatibleWith(contentType))
          }

          test("the response does not contain inlined resources") {
            val content = andReturn().response.contentAsString.let { mapper.readTree(it) }
            expectThat(content).at("/resources").isMissing()
          }
        }
      }
    }

    context("submitting a delivery config manifest") {
      val yamlPayload =
        """---
        |application: keel
        |serviceAccount: keel@spinnaker
        |artifacts:
        |- name: keel
        |  type: deb
        |  vmOptions:
        |    baseOs: bionic
        |    regions:
        |    - ap-south-1
        |environments:
        |- name: test
        |  locations: &locations
        |    account: "titustestvpc"
        |    regions:
        |      - name: us-west-2
        |  resources:
        |  - kind: test/whatever@v1
        |    spec:
        |      data: resource in test
        |      application: someapp
        |      locations:
        |        <<: *locations
        |- name: prod
        |  constraints:
        |  - type: depends-on
        |    environment: test
        |  - type: allowed-times
        |    windows:
        |    - hours: 6-18
        |      days: Monday-Friday
        |      tz: America/Los_Angeles
        |  - type: manual-judgement
        |  resources:
        |  - kind: test/whatever@v1
        |    spec:
        |      data: resource in prod
        |      application: someapp
        |"""
          .trimMargin()

      val jsonPayload =
        """{
        |  "application": "keel",
        |  "serviceAccount": "keel@spinnaker",
        |  "artifacts": [
        |    {
        |      "name": "keel",
        |      "type": "deb",
        |      "vmOptions": {
        |        "baseOs": "bionic",
        |        "regions": [
        |          "ap-south-1"
        |        ]
        |      }
        |    }
        |  ],
        |  "environments": [
        |    {
        |      "name": "test",
        |      "resources": [
        |        {
        |          "kind": "test/whatever@v1",
        |          "spec": {
        |            "data": "resource in test",
        |            "application": "someapp"
        |          }
        |        }
        |      ]
        |    },
        |    {
        |      "name": "prod",
        |      "constraints": [
        |        {
        |          "type": "depends-on",
        |          "environment": "test"
        |        },
        |        {
        |          "type": "allowed-times",
        |          "windows": [
        |            {
        |              "hours": "6-18",
        |              "days": "Monday-Friday",
        |              "tz": "America/Los_Angeles"
        |            }
        |          ]
        |        },
        |        {
        |          "type": "manual-judgement"
        |        }
        |      ],
        |      "resources": [
        |        {
        |          "kind": "test/whatever@v1",
        |          "spec": {
        |            "data": "resource in prod",
        |            "application": "someapp"
        |          }
        |        }
        |      ]
        |    }
        |  ]
        |}"""
          .trimMargin()

      mapOf(
        APPLICATION_YAML to yamlPayload,
        APPLICATION_JSON to jsonPayload
      ).forEach { (contentType, configContent) ->
        listOf(
          "/delivery-configs" to configContent,
          "/delivery-configs/upsertGate" to jsonMapper.writeValueAsString(
            DeliveryConfigController.GateRawConfig(
              configContent
            )
          )
        ).forEach { (endpoint, configPayload) ->
          context("persisting a delivery config as $contentType") {
            before {
              every {
                notificationRepository.dismissNotification(any<Class<DismissibleNotification>>(), any(), any(), any())
              } returns true

              coEvery {
                front50Cache.updateManagedDeliveryConfig(any<String>(), any(), any())
              } returns Application(name = deliveryConfig.application, email = "keel@keel.io")

              every {
                repository.upsertDeliveryConfig(ofType<SubmittedDeliveryConfig>())
              } answers {
                firstArg<SubmittedDeliveryConfig>().toDeliveryConfig()
              }
            }

            derivedContext<ResultActions>("existing apps") {
              fixture {
                every { repository.getDeliveryConfigForApplication(deliveryConfig.application) } returns deliveryConfig.toDeliveryConfig()

                val request = post(endpoint)
                  .accept(contentType)
                  .contentType(contentType)
                  .content(configPayload)
                  .header("X-SPINNAKER-USER", "user")

                mvc.perform(request)
              }

              test("the request is successful") {
                andExpect(status().isOk)
              }

              test("the manifest is persisted") {
                verify { repository.upsertDeliveryConfig(match<SubmittedDeliveryConfig> { it.application == "keel" }) }
              }

              test("only new apps should call front50") {
                coVerify(exactly = 0) {
                  front50Cache.updateManagedDeliveryConfig(any<String>(), any(), any())
                }
              }
            }

            derivedContext<ResultActions>("new apps") {
              fixture {
                every { repository.getDeliveryConfigForApplication(deliveryConfig.application) } throws NoDeliveryConfigForApplication(
                  "no config"
                )
                val request = post(endpoint)
                  .accept(contentType)
                  .contentType(contentType)
                  .content(configPayload)
                  .header("X-SPINNAKER-USER", "user")

                mvc.perform(request)
              }

              test("the request is successful") {
                andExpect(status().isOk)
              }

              test("initializing front50 properly") {
                coVerify(exactly = 1) {
                  front50Cache.updateManagedDeliveryConfig(any<String>(), any(), ManagedDeliveryConfig(importDeliveryConfig = true))
                }
              }

            }
          }
        }

        derivedContext<ResultActions>("the submitted manifest is missing a required field") {
          fixture {
            val mapper = when (contentType) {
              APPLICATION_YAML -> yamlMapper
              else -> jsonMapper
            }
            val invalidPayload = mapper
              .let {
                if (it is YAMLMapper) {
                  it.readValueInliningAliases<Map<String, Any?>>(configContent)
                } else {
                  it.readValue(configContent)
                }
              }
              .let { it - "application" }
              .let(mapper::writeValueAsString)

            val request = post("/delivery-configs")
              .accept(contentType)
              .contentType(contentType)
              .content(invalidPayload)
              .header("X-SPINNAKER-USER", "user")

            mvc.perform(request)
          }

          test("the request fails") {
            andExpect(status().isBadRequest)
          }
        }

        derivedContext<ResultActions>("the submitted manifest is unparseable") {
          fixture {
            val request = post("/delivery-configs")
              .accept(contentType)
              .contentType(contentType)
              .content(
                """
                T̫̺̳o̬̜ ì̬͎̲̟nv̖̗̻̣̹̕o͖̗̠̜̤k͍͚̹͖̼e̦̗̪͍̪͍ ̬ͅt̕h̠͙̮͕͓e̱̜̗͙̭ ̥͔̫͙̪͍̣͝ḥi̼̦͈̼v҉̩̟͚̞͎e͈̟̻͙̦̤-m̷̘̝̱í͚̞̦̳n̝̲̯̙̮͞d̴̺̦͕̫ ̗̭̘͎͖r̞͎̜̜͖͎̫͢ep͇r̝̯̝͖͉͎̺e̴s̥e̵̖̳͉͍̩̗n̢͓̪͕̜̰̠̦t̺̞̰i͟n҉̮̦̖̟g̮͍̱̻͍̜̳ ̳c̖̮̙̣̰̠̩h̷̗͍̖͙̭͇͈a̧͎̯̹̲̺̫ó̭̞̜̣̯͕s̶̤̮̩̘.̨̻̪̖͔
                 ̳̭̦̭̭̦̞́I̠͍̮n͇̹̪̬v̴͖̭̗̖o̸k҉̬̤͓͚̠͍i͜n̛̩̹͉̘̹g͙ ̠̥ͅt̰͖͞h̫̼̪e̟̩̝ ̭̠̲̫͔fe̤͇̝̱e͖̮̠̹̭͖͕l͖̲̘͖̠̪i̢̖͎̮̗̯͓̩n̸̰g̙̱̘̗͚̬ͅ ͍o͍͍̩̮͢f̖͓̦̥ ̘͘c̵̫̱̗͚͓̦h͝a̝͍͍̳̣͖͉o͙̟s̤̞.̙̝̭̣̳̼͟
                 ̢̻͖͓̬̞̰̦W̮̲̝̼̩̝͖i͖͖͡ͅt̘̯͘h̷̬̖̞̙̰̭̳ ̭̪̕o̥̤̺̝̼̰̯͟ṳ̞̭̤t̨͚̥̗ ̟̺̫̩̤̳̩o̟̰̩̖ͅr̞̘̫̩̼d̡͍̬͎̪̺͚͔e͓͖̝̙r̰͖̲̲̻̠.̺̝̺̟͈
                 ̣̭T̪̩̼h̥̫̪͔̀e̫̯͜ ̨N̟e҉͔̤zp̮̭͈̟é͉͈ṛ̹̜̺̭͕d̺̪̜͇͓i̞á͕̹̣̻n͉͘ ̗͔̭͡h̲͖̣̺̺i͔̣̖̤͎̯v̠̯̘͖̭̱̯e̡̥͕-m͖̭̣̬̦͈i͖n̞̩͕̟̼̺͜d̘͉ ̯o̷͇̹͕̦f̰̱ ̝͓͉̱̪̪c͈̲̜̺h̘͚a̞͔̭̰̯̗̝o̙͍s͍͇̱͓.̵͕̰͙͈ͅ ̯̞͈̞̱̖Z̯̮̺̤̥̪̕a͏̺̗̼̬̗ḻg͢o̥̱̼.̺̜͇͡ͅ ̴͓͖̭̩͎̗
                 ̧̪͈̱̹̳͖͙H̵̰̤̰͕̖e̛ ͚͉̗̼̞w̶̩̥͉̮h̩̺̪̩͘ͅọ͎͉̟ ̜̩͔̦̘ͅW̪̫̩̣̲͔̳a͏͔̳͖i͖͜t͓̤̠͓͙s̘̰̩̥̙̝ͅ ̲̠̬̥Be̡̙̫̦h̰̩i̛̫͙͔̭̤̗̲n̳͞d̸ ͎̻͘T̛͇̝̲̹̠̗ͅh̫̦̝ͅe̩̫͟ ͓͖̼W͕̳͎͚̙̥ą̙l̘͚̺͔͞ͅl̳͍̙̤̤̮̳.̢
                 ̟̺̜̙͉Z̤̲̙̙͎̥̝A͎̣͔̙͘L̥̻̗̳̻̳̳͢G͉̖̯͓̞̩̦O̹̹̺!̙͈͎̞̬
                """.trimIndent()
              )
              .header("X-SPINNAKER-USER", "user")

            mvc.perform(request)
          }

          test("the request fails") {
            andExpect(status().isBadRequest)
          }
        }
      }
    }

    context("importing a delivery config from source control") {
      before {
        every {
          repository.upsertDeliveryConfig(ofType<SubmittedDeliveryConfig>())
        } returns deliveryConfig.toDeliveryConfig()

        every { repository.getDeliveryConfigForApplication(deliveryConfig.application) } returns deliveryConfig.toDeliveryConfig()
      }

      context("when manifest retrieved successfully") {
        before {
          every {
            importer.import("stash", "proj", "repo", "spinnaker.yml", "refs/heads/master")
          } returns deliveryConfig
        }

        test("the request is successful and the manifest persisted") {
          val request =
            post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
              .accept(APPLICATION_YAML)
              .contentType(APPLICATION_YAML)

          val response = mvc.perform(request)
          response.andExpect(status().isOk)

          verify(exactly = 1) {
            repository.upsertDeliveryConfig(deliveryConfig)
          }
        }
      }

      context("when manifest retrieval fails") {
        val retrofitError = RetrofitError.httpError(
          "http://igor",
          Response("http://igor", 404, "not found", emptyList(), null),
          null, null
        )

        before {
          every {
            importer.import("stash", "proj", "repo", "spinnaker.yml", "refs/heads/master")
          } throws retrofitError
        }

        test("the error from the downstream service is returned") {
          val request =
            post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
              .accept(APPLICATION_YAML)
              .contentType(APPLICATION_YAML)

          val response = mvc.perform(request)
          response.andExpect(status().isNotFound)
        }
      }
    }

    context("deleting a delivery config") {
      context("that exists") {
        before {
          every {
            repository.deleteDeliveryConfigByName(any())
          } just Runs
        }

        test("the request is successful and the manifest deleted") {
          val request = delete("/delivery-configs/myconfig")
          val response = mvc.perform(request)
          response.andExpect(status().isOk)

          verify(exactly = 1) {
            repository.deleteDeliveryConfigByName("myconfig")
          }
        }
      }

      context("that does not exist") {
        before {
          every {
            repository.deleteDeliveryConfigByName("myconfig")
          } throws NoSuchDeliveryConfigName("myconfig")
        }

        test("the request fails with a not found error") {
          val request = delete("/delivery-configs/myconfig")
          val response = mvc.perform(request)
          response.andExpect(status().isNotFound)
        }
      }
    }

    context("API permission checks") {
      context("GET /delivery-configs") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, DELIVERY_CONFIG)
            authorizationSupport.allowCloudAccountAccess(READ, DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = get("/delivery-configs/${deliveryConfig.name}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no READ access to cloud account") {
          before {
            authorizationSupport.denyCloudAccountAccess(READ, DELIVERY_CONFIG)
            authorizationSupport.allowApplicationAccess(READ, DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = get("/delivery-configs/${deliveryConfig.name}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("POST /delivery-configs") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = post("/delivery-configs").addData(jsonMapper, deliveryConfig)
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.denyServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = post("/delivery-configs").addData(jsonMapper, deliveryConfig)
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }

      context("POST /delivery-configs/import") {
        before {
          every {
            importer.import("stash", "proj", "repo", "spinnaker.yml", "refs/heads/master")
          } returns deliveryConfig
        }
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request =
              post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.denyServiceAccountAccess()
          }
          test("request is forbidden") {
            val request =
              post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
