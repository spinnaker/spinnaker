package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.services.DeliveryConfigImporter
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import retrofit.RetrofitError
import retrofit.client.Response

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
internal class DeliveryConfigControllerTests : JUnit5Minutests {

  @Autowired
  lateinit var mvc: MockMvc

  @MockkBean
  lateinit var repository: KeelRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @MockkBean
  lateinit var importer: DeliveryConfigImporter

  @Autowired
  lateinit var jsonMapper: ObjectMapper

  @Autowired
  lateinit var yamlMapper: YAMLMapper

  private val deliveryConfig = SubmittedDeliveryConfig(
    name = "keel-manifest",
    application = "keel",
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(DebianArtifact(
      name = "keel",
      vmOptions = VirtualMachineOptions(
        baseOs = "bionic",
        regions = setOf("us-west-2")
      )
    )),
    environments = setOf(
      SubmittedEnvironment(
        name = "test",
        resources = setOf(SubmittedResource(
          kind = TEST_API_V1.qualify("whatever"),
          spec = DummyResourceSpec(data = "resource in test")
        ))
      ),
      SubmittedEnvironment(
        name = "prod",
        resources = setOf(SubmittedResource(
          kind = TEST_API_V1.qualify("whatever"),
          spec = DummyResourceSpec(data = "resource in prod")
        )),
        constraints = setOf(DependsOnConstraint("test"))
      )
    )
  )

  private fun SubmittedDeliveryConfig.toDeliveryConfig(): DeliveryConfig = DeliveryConfig(
    name = safeName,
    application = application,
    serviceAccount = serviceAccount!!,
    artifacts = artifacts,
    environments = environments.map {
      Environment(
        name = it.name,
        resources = it.resources.map {
          Resource(
            kind = it.kind,
            metadata = mapOf(
              "id" to randomUID().toString(),
              "serviceAccount" to serviceAccount,
              "application" to application
            ),
            spec = it.spec
          )
        }.toSet()
      )
    }.toSet()
  )

  fun tests() = rootContext {
    before {
      authorizationSupport.allowAll()
    }

    context("getting a delivery config manifest") {
      before {
        every { repository.getDeliveryConfig(deliveryConfig.safeName) } returns deliveryConfig.toDeliveryConfig()
      }

      setOf(APPLICATION_YAML, APPLICATION_JSON).forEach { contentType ->
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
        |  resources:
        |  - kind: test/whatever@v1
        |    spec:
        |      data: resource in test
        |      application: someapp
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
      ).forEach { (contentType, payload) ->
        derivedContext<ResultActions>("persisting a delivery config as $contentType") {
          fixture {
            every {
              repository.upsertDeliveryConfig(any<SubmittedDeliveryConfig>())
            } answers {
              firstArg<SubmittedDeliveryConfig>().toDeliveryConfig()
            }

            val request = post("/delivery-configs")
              .accept(contentType)
              .contentType(contentType)
              .content(payload)

            mvc.perform(request)
          }

          test("the request is successful") {
            andExpect(status().isOk)
          }

          test("the manifest is persisted") {
            verify { repository.upsertDeliveryConfig(match<SubmittedDeliveryConfig> { it.application == "keel" }) }
          }
        }

        derivedContext<ResultActions>("the submitted manifest is missing a required field") {
          fixture {
            val mapper = when (contentType) {
              APPLICATION_YAML -> yamlMapper
              else -> jsonMapper
            }
            val invalidPayload = mapper
              .readValue<Map<String, Any?>>(payload)
              .let { it - "application" }
              .let(mapper::writeValueAsString)

            val request = post("/delivery-configs")
              .accept(contentType)
              .contentType(contentType)
              .content(invalidPayload)

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
              .content("""
                T̫̺̳o̬̜ ì̬͎̲̟nv̖̗̻̣̹̕o͖̗̠̜̤k͍͚̹͖̼e̦̗̪͍̪͍ ̬ͅt̕h̠͙̮͕͓e̱̜̗͙̭ ̥͔̫͙̪͍̣͝ḥi̼̦͈̼v҉̩̟͚̞͎e͈̟̻͙̦̤-m̷̘̝̱í͚̞̦̳n̝̲̯̙̮͞d̴̺̦͕̫ ̗̭̘͎͖r̞͎̜̜͖͎̫͢ep͇r̝̯̝͖͉͎̺e̴s̥e̵̖̳͉͍̩̗n̢͓̪͕̜̰̠̦t̺̞̰i͟n҉̮̦̖̟g̮͍̱̻͍̜̳ ̳c̖̮̙̣̰̠̩h̷̗͍̖͙̭͇͈a̧͎̯̹̲̺̫ó̭̞̜̣̯͕s̶̤̮̩̘.̨̻̪̖͔
                 ̳̭̦̭̭̦̞́I̠͍̮n͇̹̪̬v̴͖̭̗̖o̸k҉̬̤͓͚̠͍i͜n̛̩̹͉̘̹g͙ ̠̥ͅt̰͖͞h̫̼̪e̟̩̝ ̭̠̲̫͔fe̤͇̝̱e͖̮̠̹̭͖͕l͖̲̘͖̠̪i̢̖͎̮̗̯͓̩n̸̰g̙̱̘̗͚̬ͅ ͍o͍͍̩̮͢f̖͓̦̥ ̘͘c̵̫̱̗͚͓̦h͝a̝͍͍̳̣͖͉o͙̟s̤̞.̙̝̭̣̳̼͟
                 ̢̻͖͓̬̞̰̦W̮̲̝̼̩̝͖i͖͖͡ͅt̘̯͘h̷̬̖̞̙̰̭̳ ̭̪̕o̥̤̺̝̼̰̯͟ṳ̞̭̤t̨͚̥̗ ̟̺̫̩̤̳̩o̟̰̩̖ͅr̞̘̫̩̼d̡͍̬͎̪̺͚͔e͓͖̝̙r̰͖̲̲̻̠.̺̝̺̟͈
                 ̣̭T̪̩̼h̥̫̪͔̀e̫̯͜ ̨N̟e҉͔̤zp̮̭͈̟é͉͈ṛ̹̜̺̭͕d̺̪̜͇͓i̞á͕̹̣̻n͉͘ ̗͔̭͡h̲͖̣̺̺i͔̣̖̤͎̯v̠̯̘͖̭̱̯e̡̥͕-m͖̭̣̬̦͈i͖n̞̩͕̟̼̺͜d̘͉ ̯o̷͇̹͕̦f̰̱ ̝͓͉̱̪̪c͈̲̜̺h̘͚a̞͔̭̰̯̗̝o̙͍s͍͇̱͓.̵͕̰͙͈ͅ ̯̞͈̞̱̖Z̯̮̺̤̥̪̕a͏̺̗̼̬̗ḻg͢o̥̱̼.̺̜͇͡ͅ ̴͓͖̭̩͎̗
                 ̧̪͈̱̹̳͖͙H̵̰̤̰͕̖e̛ ͚͉̗̼̞w̶̩̥͉̮h̩̺̪̩͘ͅọ͎͉̟ ̜̩͔̦̘ͅW̪̫̩̣̲͔̳a͏͔̳͖i͖͜t͓̤̠͓͙s̘̰̩̥̙̝ͅ ̲̠̬̥Be̡̙̫̦h̰̩i̛̫͙͔̭̤̗̲n̳͞d̸ ͎̻͘T̛͇̝̲̹̠̗ͅh̫̦̝ͅe̩̫͟ ͓͖̼W͕̳͎͚̙̥ą̙l̘͚̺͔͞ͅl̳͍̙̤̤̮̳.̢
                 ̟̺̜̙͉Z̤̲̙̙͎̥̝A͎̣͔̙͘L̥̻̗̳̻̳̳͢G͉̖̯͓̞̩̦O̹̹̺!̙͈͎̞̬
              """.trimIndent())

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
          repository.upsertDeliveryConfig(any<SubmittedDeliveryConfig>())
        } returns deliveryConfig.toDeliveryConfig()
      }

      context("when manifest retried successfully") {
        before {
          every {
            importer.import("stash", "proj", "repo", "spinnaker.yml", "refs/heads/master")
          } returns deliveryConfig
        }

        test("the request is successful and the manifest persisted") {
          val request = post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
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
        val retrofitError = RetrofitError.httpError("http://igor",
          Response("http://igor", 404, "not found", emptyList(), null),
          null, null)

        before {
          every {
            importer.import("stash", "proj", "repo", "spinnaker.yml", "refs/heads/master")
          } throws retrofitError
        }

        test("the error from the dowstream service is returned") {
          val request = post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
            .accept(APPLICATION_YAML)
            .contentType(APPLICATION_YAML)

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
            val request = post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
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
            val request = post("/delivery-configs/import?repoType=stash&projectKey=proj&repoSlug=repo&manifestPath=spinnaker.yml")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
