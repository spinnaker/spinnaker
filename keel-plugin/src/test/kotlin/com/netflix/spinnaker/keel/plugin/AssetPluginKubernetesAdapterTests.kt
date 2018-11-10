package com.netflix.spinnaker.keel.plugin

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.nhaarman.mockito_kotlin.mock
import com.oneeyedmen.minutest.junit.junitTests
import com.squareup.okhttp.Response
import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1DeleteOptions
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec
import io.kubernetes.client.util.Config
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.map
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.util.concurrent.TimeUnit.SECONDS

internal object AssetPluginKubernetesAdapterTests {

  private data class Fixture(
    val plugin: AssetPlugin,
    val crd: V1beta1CustomResourceDefinition,
    val crdApi: ApiextensionsV1beta1Api = ApiextensionsV1beta1Api(),
    val customObjectsApi: CustomObjectsApi = CustomObjectsApi()
  ) {
    inline fun <reified T : Any> parseSomeShit(response: Response): T {
      val json = response.body().string()
      println(json)
      return Configuration.getDefaultApiClient().json.gson.fromJson<T>(
        json,
        object : TypeToken<T>() {}.type
      )
    }
  }

  @BeforeAll
  @JvmStatic
  fun initK8s() {
    val client = Config.defaultClient()
    client.httpClient.setReadTimeout(60, SECONDS)
    Configuration.setDefaultApiClient(client)
  }

  @TestFactory
  fun `kubernetes integration`() = junitTests<Fixture> {
    fixture {
      Fixture(
        plugin = mock(),
        crd = V1beta1CustomResourceDefinition().apply {
          apiVersion = "apiextensions.k8s.io/v1beta1"
          kind = "CustomResourceDefinition"
          metadata = V1ObjectMeta().apply {
            name = "security-groups.ec2.${SPINNAKER_API_V1.group}"
          }
          spec = V1beta1CustomResourceDefinitionSpec().apply {
            group = "ec2.${SPINNAKER_API_V1.group}"
            version = SPINNAKER_API_V1.version
            names = V1beta1CustomResourceDefinitionNames().apply {
              kind = "security-group"
              plural = "security-groups"
            }
            scope = "Cluster"
          }
        }
      )
    }

    before {
      try {
        crdApi.createCustomResourceDefinition(crd, "true")
        println("Created ${crd.metadata.name}")
      } catch (e: ApiException) {
        if (e.code == HTTP_CONFLICT) {
          println("${crd.metadata.name} already exists")
        } else {
          println(e.responseBody)
          throw e
        }
      }
      crdApi.waitForCRDCreated(crd.metadata.name)
    }

    after {
      try {
        println("Deleting ${crd.metadata.name}")
        crdApi.deleteCustomResourceDefinition(
          crd.metadata.name,
          V1DeleteOptions(),
          "true",
          0,
          null,
          "Background"
        )
      } catch (e: ApiException) {
        println("Error deleting ${crd.metadata.name}: ${e.code}")
      } catch (e: JsonSyntaxException) {
        // FFS k8s, learn to parse your own responses
      }
      crdApi.waitForCRDDeleted(crd.metadata.name)
    }

    test("can see the CRD") {
      val response = crdApi.listCustomResourceDefinition(
        "true",
        "true",
        null,
        null,
        null,
        0,
        null,
        5,
        false
      )

      expectThat(response.items)
        .map { it.metadata.name }
        .contains(crd.metadata.name)
    }

    context("no objects of the type have been defined") {
      test("there should be zero objects") {
        val call = customObjectsApi.listClusterCustomObjectCall(
          crd.spec.group,
          crd.spec.version,
          crd.spec.names.plural,
          "true",
          null,
          null,
          false,
          null,
          null
        )
        val response = parseSomeShit<ListCustomObjectResponse<SecurityGroup>>(call.execute())
        println(response)
        expectThat(response.items).isEmpty()
      }
    }

    context("an instance of the CRD has been registered") {
      before {
        val securityGroup = mapOf(
          "apiVersion" to "ec2.${SPINNAKER_API_V1.group}/v1",
          "kind" to crd.spec.names.kind,
          "metadata" to mapOf(
            "name" to "my-security-group"
          ),
          "spec" to SecurityGroup(
            application = "fnord",
            name = "fnord",
            accountName = "test",
            region = "us-west-2",
            vpcName = "vpc0",
            description = "a security group"
          )
        )

        try {
          customObjectsApi.createClusterCustomObject(
            crd.spec.group,
            crd.spec.version,
            crd.spec.names.plural,
            securityGroup,
            "true"
          )
        } catch (e: ApiException) {
          println("Error creating custom object $e")
          throw e
        }
      }

      after {

      }

      test("there should be one object") {
        val call = customObjectsApi.listClusterCustomObjectCall(
          crd.spec.group,
          crd.spec.version,
          crd.spec.names.plural,
          "true",
          null,
          null,
          false,
          null,
          null
        )
        val response = parseSomeShit<ListCustomObjectResponse<SecurityGroup>>(call.execute())
        println(response)
        expectThat(response.items)
          .hasSize(1)
          .first()
          .and {
            get { apiVersion }.isEqualTo("ec2.${SPINNAKER_API_V1.group}/v1")
            get { spec.name }.isEqualTo("fnord")
          }
      }
    }
  }
}

data class ListCustomObjectResponse<T>(
  val apiVersion: String,
  val items: List<Resource<T>>,
  val kind: String,
  val metadata: Map<String, Any?>
)

data class Resource<T>(
  val apiVersion: String,
  val kind: String, // TODO: create a type
  val metadata: AssetMetadata,
  val spec: T
)

data class SecurityGroup(
  val application: String,
  val name: String,
  val accountName: String,
  val region: String,
  val vpcName: String?,
  val description: String?
)

private fun ApiextensionsV1beta1Api.waitForCRDCreated(name: String) {
  var found = false
  while (!found) {
    found = listCustomResourceDefinition(
      "true",
      "true",
      null,
      null,
      null,
      0,
      null,
      5,
      false
    )
      .items
      .map { it.metadata.name }
      .contains(name)
    if (!found) {
      Thread.sleep(100)
    }
  }
}

private fun ApiextensionsV1beta1Api.waitForCRDDeleted(name: String) {
  var found = true
  while (found) {
    found = listCustomResourceDefinition(
      "true",
      "true",
      null,
      null,
      null,
      0,
      null,
      5,
      false
    )
      .items
      .map { it.metadata.name }
      .contains(name)
    if (!found) {
      Thread.sleep(100)
    }
  }
}
