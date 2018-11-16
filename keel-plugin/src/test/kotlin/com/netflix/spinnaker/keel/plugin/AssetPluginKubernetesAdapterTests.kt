package com.netflix.spinnaker.keel.plugin

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.oneeyedmen.minutest.junit.junitTests
import com.squareup.okhttp.Response
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1DeleteOptions
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.map
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

/**
 * NOTE: requires minikube to be running.
 *
 *     brew cask install minikube
 *     brew install docker-machine-driver-hyperkit (other driver options work but this seems to be the lightest)
 *     minikube start --vm-driver hyperkit
 *
 *     # do stuff
 *
 *     minikube stop
 *
 * Once you've run minikube once it will remember the config including the
 * `vm-driver` setting (in fact it looks like specifying it again gets ignored)
 * so you can run it again with just `minikube start`
 */
internal object AssetPluginKubernetesAdapterTests {

  private data class Fixture(
    val plugin: MockAssetPlugin,
    val crd: V1beta1CustomResourceDefinition,
    val crdApi: ApiextensionsV1beta1Api = ApiextensionsV1beta1Api(),
    val customObjectsApi: CustomObjectsApi = CustomObjectsApi()
  ) {
    val watcher: AssetPluginKubernetesAdapter<SecurityGroup> = AssetPluginKubernetesAdapter(
      customObjectsApi,
      crd,
      plugin,
      object : TypeToken<Watch.Response<Asset<SecurityGroup>>>() {}.type
    )
  }

  @BeforeAll
  @JvmStatic
  fun initK8s() {
    assumeMinikubeAvailable()

    val client = Config.defaultClient()
    with(client.httpClient) {
      setReadTimeout(5, SECONDS)
    }
    Configuration.setDefaultApiClient(client)
  }

  /**
   * Tests to see if minikube is running. If not, skips this test class.
   */
  private fun assumeMinikubeAvailable() {
    val minikubeDir = "/usr/local/bin"
    val exitStatus = try {
      ProcessBuilder("$minikubeDir/minikube", "status")
        .apply {
          environment()["PATH"] = minikubeDir
        }
        .start()
        .waitFor()
    } catch (e: Exception) {
      assumeNoException(e)
    }
    assumeTrue("Exit status from `minikube status` was $exitStatus", exitStatus == 0)
  }

  @TestFactory
  fun `kubernetes integration`() = junitTests<Fixture> {
    fixture {
      Fixture(
        plugin = MockAssetPlugin(),
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
      crdApi.createCustomResourceDefinition(crd, "true")
      crdApi.waitForCRDCreated(crd.metadata.name)
      watcher.start()
    }

    after {
      watcher.stop()
      try {
        crdApi.deleteCustomResourceDefinition(
          crd.metadata.name,
          V1DeleteOptions(),
          "true",
          0,
          null,
          "Background"
        )
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
        val response = parse<ResourceList<SecurityGroup>>(call.execute())
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
        expectThat(plugin.lastCreated.get())
          .get { metadata.name }
          .isEqualTo("my-security-group".let(::AssetName))
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

      context("the instance is updated") {
        before {
          val uid = customObjectsApi.getClusterCustomObjectCall(
            crd.spec.group,
            crd.spec.version,
            crd.spec.names.plural,
            "my-security-group",
            null,
            null
          )
            .execute()
            .let {
              parse<Asset<SecurityGroup>>(it)
                .also(::println)
                .metadata.uid
            }
          val securityGroup = mapOf(
            "apiVersion" to "ec2.${SPINNAKER_API_V1.group}/v1",
            "kind" to crd.spec.names.kind,
            "metadata" to mapOf(
              "name" to "my-security-group",
              "uid" to uid
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

          val response = customObjectsApi.patchClusterCustomObjectWithHttpInfo(
            crd.spec.group,
            crd.spec.version,
            crd.spec.names.plural,
            "my-security-group",
            securityGroup
          )
          println(response.statusCode)
          println(response.data)
        }

        test("the plugin gets invoked") {
          expectThat(plugin.lastUpdated.get())
            .get { spec }
            .isA<SecurityGroup>()
            .get { description }
            .isEqualTo("a security group with an updated description")
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

private fun ApiextensionsV1beta1Api.waitForCRDCreated(name: String) {
  runBlocking {
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
      yield()
    }
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
    latch.await()
    return ref.get()
  }

  fun set(value: V) {
    ref.set(value)
    latch.countDown()
  }
}

private class MockAssetPlugin : AssetPlugin {

  val lastCreated = BlockingReference<Asset<*>>()
  val lastUpdated = BlockingReference<Asset<*>>()
  val lastDeleted = BlockingReference<Asset<*>>()

  override val supportedKinds: Iterable<String>
    get() = TODO("not implemented")

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
