package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.succeeded

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class DeliveryConfigControllerTests : JUnit5Minutests {

  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var deliveryConfigRepository: InMemoryDeliveryConfigRepository

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @Autowired
  lateinit var repository: KeelRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @Autowired
  lateinit var jsonMapper: ObjectMapper

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

  fun tests() = rootContext {
    before {
      authorizationSupport.allowAll()
    }

    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      artifactRepository.dropAll()
    }

    context("getting a delivery config manifest") {
      before {
        repository.upsertDeliveryConfig(deliveryConfig)
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
        |name: keel-manifest
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
        |      - hours: 6-18
        |        days: mon-fri
        |  resources:
        |  - kind: test/whatever@v1
        |    spec:
        |      data: resource in prod
        |      application: someapp
        |"""
          .trimMargin()

      val jsonPayload =
        """{
        |  "name": "keel-manifest",
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
            expectCatching { deliveryConfigRepository.get("keel-manifest") }
              .succeeded()
          }

          test("each individual resource is persisted") {
            expectThat(resourceRepository.size()).isEqualTo(2)
          }

          test("prod constraint is persisted") {
            expectThat(
              deliveryConfigRepository
                .get("keel-manifest")
                .environments
                .find { it.name == "prod" }
                ?.constraints
            ).isNotNull()
          }
        }

        derivedContext<ResultActions>("the submitted manifest is missing a required field") {
          fixture {
            val mapper = when (contentType) {
              APPLICATION_YAML -> configuredYamlMapper()
              else -> configuredObjectMapper()
            }
            val invalidPayload = mapper
              .readValue<Map<String, Any?>>(payload)
              .let { it - "serviceAccount" }
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
            authorizationSupport.denyApplicationAccess(WRITE, DELIVERY_CONFIG)
            authorizationSupport.allowServiceAccountAccess(DELIVERY_CONFIG)
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
            authorizationSupport.allowApplicationAccess(WRITE, DELIVERY_CONFIG)
            authorizationSupport.denyServiceAccountAccess(DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = post("/delivery-configs").addData(jsonMapper, deliveryConfig)
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
