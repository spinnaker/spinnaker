package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
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
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
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
  lateinit var resourcePersister: ResourcePersister

  fun tests() = rootContext {
    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      artifactRepository.dropAll()
    }

    context("getting a delivery config manifest") {
      before {
        resourcePersister.upsert(
          SubmittedDeliveryConfig(
            name = "keel-manifest",
            application = "keel",
            serviceAccount = "keel@spinnaker",
            artifacts = setOf(DebianArtifact(name = "keel")),
            environments = setOf(
              SubmittedEnvironment(
                name = "test",
                resources = setOf(SubmittedResource(
                  apiVersion = "test.$SPINNAKER_API_V1",
                  kind = "whatever",
                  spec = DummyResourceSpec(data = "resource in test")
                ))
              ),
              SubmittedEnvironment(
                name = "prod",
                resources = setOf(SubmittedResource(
                  apiVersion = "test.$SPINNAKER_API_V1",
                  kind = "whatever",
                  spec = DummyResourceSpec(data = "resource in prod")
                )),
                constraints = setOf(DependsOnConstraint("test"))
              )
            )
          )
        )
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
        |environments:
        |- name: test
        |  resources:
        |  - apiVersion: test.spinnaker.netflix.com/v1
        |    kind: whatever
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
        |  - apiVersion: test.spinnaker.netflix.com/v1
        |    kind: whatever
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
        |      "type": "deb"
        |    }
        |  ],
        |  "environments": [
        |    {
        |      "name": "test",
        |      "resources": [
        |        {
        |          "apiVersion": "test.spinnaker.netflix.com/v1",
        |          "kind": "whatever",
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
        |          "apiVersion": "test.spinnaker.netflix.com/v1",
        |          "kind": "whatever",
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

          context("no environments with stateful constraints have been evaluated") {
            test("no constraint state") {
              getProdConstraintResponse(contentType)
                // Response assertions that aren't dependent on contentType or deserialization
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("status"))))

              val states = getProdConstraintStates(contentType)
              expectThat(states).isEmpty()
            }
          }

          derivedContext<ResultActions>("interacting with manual judgement") {
            val constraintStateYaml =
              """---
                |type: manual-judgement
                |artifactVersion: keel-1.0.2
                |status: OVERRIDE_PASS
              """.trimMargin()

            val constraintStateJson =
              """{
                |  "type": "manual-judgement",
                |  "artifactVersion": "keel-1.0.2",
                |  "status": "OVERRIDE_PASS"
                |}
              """.trimMargin()

            fixture {
              val judgement = ConstraintState("keel-manifest", "prod", "keel-1.0.2", "manual-judgement", PENDING)
              deliveryConfigRepository.storeConstraintState(judgement)

              getProdConstraintResponse(contentType)
            }

            test("pending manual judgement") {
              andExpect(status().isOk)
              andExpect(content().string(containsString("manual-judgement")))
              andExpect(content().string(containsString("PENDING")))
            }

            derivedContext<ResultActions>("approving manual judgement") {
              fixture {
                val request = post(
                  "/delivery-configs/keel-manifest/environment/prod/constraint")
                  .accept(contentType)
                  .contentType(contentType)
                  .content(when (contentType) {
                    APPLICATION_YAML -> constraintStateYaml
                    else -> constraintStateJson
                  })
                  .header("X-SPINNAKER-USER", "keel")

                mvc.perform(request)
              }

              test("judgement is persisted") {
                andExpect(status().isOk)

                val judgement = deliveryConfigRepository
                  .getConstraintState("keel-manifest", "prod", "keel-1.0.2", "manual-judgement")

                expectThat(judgement).isNotNull()
                expectThat(judgement!!.status).isEqualTo(OVERRIDE_PASS)
                expectThat(judgement.judgedBy).isEqualTo("keel")
              }

              test("persisted judgement is in rest response") {
                val states = getProdConstraintStates(contentType)
                expectThat(states).hasSize(1)
                with(states.first()) {
                  expectThat(status).isEqualTo(OVERRIDE_PASS)
                  expectThat(judgedBy).isEqualTo("keel")
                }
              }
            }
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
  }

  private fun getProdConstraintResponse(contentType: MediaType) =
    mvc.perform(
      get("/delivery-configs/keel-manifest/environment/prod/constraints")
        .accept(contentType)
        .contentType(contentType)
    )

  private fun getProdConstraintStates(contentType: MediaType): List<ConstraintState> =
    getProdConstraintResponse(contentType)
      .andReturn()
      .response
      .contentAsString
      .let {
        if (contentType == APPLICATION_YAML) {
          configuredYamlMapper().readValue(it)
        } else {
          configuredObjectMapper().readValue(it)
        }
      }
}
