package com.netflix.spinnaker.keel.plugin

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.oneeyedmen.minutest.junit.junitTests
import com.squareup.okhttp.Response
import io.kubernetes.client.ApiClient
import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CoreApi
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1DeleteOptions
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.util.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.map
import java.io.Reader
import java.io.StringReader
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * NOTE: requires local k8s to be running (either Docker for Mac or Minikube).
 */
internal object KubernetesIntegrationTest {

  private val client: ApiClient = Config.defaultClient().also {
    with(it.httpClient) {
      setConnectTimeout(1, SECONDS)
      setReadTimeout(1, SECONDS)
    }
    Configuration.setDefaultApiClient(it)
  }

  private val extensionsApi = ApiextensionsV1beta1Api(client)
  private val customObjectsApi = CustomObjectsApi(client)
  val crdName = "security-groups.ec2.${SPINNAKER_API_V1.group}"
  private val crdLocator = object : CustomResourceDefinitionLocator {
    override fun locate(): Reader =
      """---
      |apiVersion: apiextensions.k8s.io/v1beta1
      |kind: CustomResourceDefinition
      |metadata:
      |  name: $crdName
      |spec:
      |  group: ec2.${SPINNAKER_API_V1.group}
      |  version: ${SPINNAKER_API_V1.version}
      |  names:
      |    kind: security-group
      |    plural: security-groups
      |  scope: Cluster
    """.trimMargin()
        .let(::StringReader)
  }


  private class MockAssetPlugin : AssetPlugin {
    val lastCreated = BlockingReference<Asset<*>>()
    val lastUpdated = BlockingReference<Asset<*>>()
    val lastDeleted = BlockingReference<Asset<*>>()

    override val supportedKinds: Map<String, KClass<out Any>> =
      mapOf(crdName to SecurityGroup::class)

    override fun current(request: Asset<*>): CurrentResponse {
      TODO("not implemented")
    }

    override fun create(request: Asset<*>): ConvergeResponse {
      lastCreated.set(request)
      return ConvergeAccepted
    }

    override fun update(request: Asset<*>): ConvergeResponse {
      lastUpdated.set(request)
      return ConvergeAccepted
    }

    override fun delete(request: Asset<*>): ConvergeResponse {
      lastDeleted.set(request)
      return ConvergeAccepted
    }
  }

  private class Fixture<T : Any>(
    val crd: V1beta1CustomResourceDefinition
  ) {
    val plugin = MockAssetPlugin()
    val adapter: AssetPluginKubernetesAdapter = AssetPluginKubernetesAdapter(
      extensionsApi,
      customObjectsApi,
      plugin
    )
  }

  /**
   * I wanted to use JUnit's assumptions here but it causes the test to fail rather than skip on Travis CI.
   */
  fun assumeK8sAvailable(): Boolean {
    return try {
      val response = CoreApi(client).apiVersionsWithHttpInfo
      if (response.statusCode != HTTP_OK) {
        println("Local Kubernetes responded with HTTP ${response.statusCode}")
        println(response.data)
        false
      } else {
        true
      }
    } catch (e: Exception) {
      println("Skipping tests as local Kubernetes is not available")
      false
    }
  }

  @TestFactory
  fun `kubernetes integration`() = junitTests<CustomResourceDefinitionRegistrar> {
    if (assumeK8sAvailable()) {
      fixture {
        CustomResourceDefinitionRegistrar(extensionsApi, listOf(crdLocator))
      }

      before {
        registerCustomResourceDefinition()
      }

      after {
        try {
          extensionsApi.deleteCustomResourceDefinition(
            crdName,
            V1DeleteOptions(),
            "true",
            0,
            null,
            "Background"
          )
        } catch (e: JsonSyntaxException) {
          // FFS k8s, learn to parse your own responses
        } catch (e: ApiException) {
          if (e.code != HTTP_NOT_FOUND) {
            throw e
          }
        }
        extensionsApi.waitForCRDDeleted(crdName)
      }

      test("can see the CRD after registering it") {
        val response = extensionsApi.listCustomResourceDefinition(
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
          .contains(crdName)
      }

      derivedContext<Fixture<SecurityGroup>>("no objects of the type have been defined") {
        deriveFixture {
          Fixture(extensionsApi.readCustomResourceDefinition(crdName, "true", null, null))
        }

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
          val response = parse<ResourceList<SecurityGroup>>(call.execute())
          expectThat(response.items).isEmpty()
        }
      }

      derivedContext<Fixture<SecurityGroup>>("an object has been registered") {
        deriveFixture {
          Fixture(extensionsApi.readCustomResourceDefinition(crdName, "true", null, null))
        }

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

          adapter.start()

          customObjectsApi.createClusterCustomObject(
            crd.spec.group,
            crd.spec.version,
            crd.spec.names.plural,
            securityGroup,
            "true"
          )
          customObjectsApi.waitForObjectCreated(
            crd.spec.group,
            crd.spec.version,
            crd.spec.names.plural,
            "my-security-group"
          )
        }

        after {
          adapter.stop()

          try {
            customObjectsApi.deleteClusterCustomObject(
              crd.spec.group,
              crd.spec.version,
              crd.spec.names.plural,
              "my-security-group",
              V1DeleteOptions(),
              0,
              null,
              "Background"
            )
          } catch (e: ApiException) {
            if (e.code != HTTP_NOT_FOUND) {
              throw e
            }
          }
        }

        test("the plugin gets invoked") {
          expectThat(plugin.lastCreated.get())
            .get { metadata.name }
            .isEqualTo(AssetName("my-security-group"))
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
          val response = parse<ResourceList<SecurityGroup>>(call.execute())
          expectThat(response.items)
            .hasSize(1)
            .first()
            .and {
              get { apiVersion }.isEqualTo(ApiVersion("ec2.${SPINNAKER_API_V1.group}", "v1"))
              get { spec.name }.isEqualTo("fnord")
            }
        }

        context("the object is updated") {
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
                description = "a security group with an updated description"
              )
            )

            // ensure the create has completed
            plugin.lastCreated.get()

            customObjectsApi.patchClusterCustomObject(
              crd.spec.group,
              crd.spec.version,
              crd.spec.names.plural,
              "my-security-group",
              securityGroup
            )
          }

          test("the plugin gets invoked") {
            expectThat(plugin.lastUpdated.get())
              .get { spec }
              .isA<SecurityGroup>()
              .get { description }
              .isEqualTo("a security group with an updated description")
          }
        }

        context("the object is deleted") {
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
                description = "a security group with an updated description"
              )
            )

            // ensure the create has completed
            plugin.lastCreated.get()

            customObjectsApi.deleteClusterCustomObject(
              crd.spec.group,
              crd.spec.version,
              crd.spec.names.plural,
              "my-security-group",
              V1DeleteOptions(),
              0,
              null,
              "Background"
            )
          }

          test("the plugin gets invoked") {
            expectThat(plugin.lastDeleted.get())
              .get { metadata.name }
              .isEqualTo(AssetName("my-security-group"))
          }
        }
      }
    }
  }
}

data class ResourceList<T : Any>(
  val apiVersion: String,
  val items: List<Asset<T>>,
  val kind: String,
  val metadata: Map<String, Any?>
)

/**
 * Simplified version of security group for the purposes of this test.
 */
data class SecurityGroup(
  val application: String,
  val name: String,
  val accountName: String,
  val region: String,
  val vpcName: String?,
  val description: String?
)

// TODO: there's a correct way to do this by watching for a create event.
private fun CustomObjectsApi.waitForObjectCreated(group: String, version: String, plural: String, name: String) {
  var found = false
  while (!found) {
    found = getClusterCustomObject(
      group,
      version,
      plural,
      name
    ) != null
  }
}

private fun ApiextensionsV1beta1Api.waitForCRDDeleted(name: String) {
  runBlocking {
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
      yield()
    }
  }
}

private inline fun <reified T : Any> parse(response: Response): T =
  Configuration.getDefaultApiClient().json.gson.fromJson<T>(
    response.body().string(),
    object : TypeToken<T>() {}.type
  )

class BlockingReference<V> {
  private val ref = AtomicReference<V>()
  private val latch = CountDownLatch(1)

  fun get(): V {
    if (!latch.await(1, SECONDS)) {
      throw TimeoutException("Timed out waiting for value")
    }
    return ref.get()
  }

  fun set(value: V) {
    ref.set(value)
    latch.countDown()
  }
}
