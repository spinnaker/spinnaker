package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isTrue

internal class EnvironmentVariablesResolverTests : JUnit5Minutests {
  val account = "titus"
  val baseSpec = TitusClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SimpleLocations(
      account = account,
      regions = setOf(SimpleRegionSpec("east"), SimpleRegionSpec("west"))
    ),
    container = ReferenceProvider("my-artifact"),
    _defaults = TitusServerGroupSpec(),
    overrides = emptyMap()
  )

  val specWithEnv = TitusClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SimpleLocations(
      account = account,
      regions = setOf(SimpleRegionSpec("east"), SimpleRegionSpec("west"))
    ),
    container = ReferenceProvider("my-artifact"),
    _defaults = TitusServerGroupSpec(
      env = mapOf("my-var" to "woah")
    ),
    overrides = emptyMap()
  )

  val springEnv: Environment = mockk(relaxed = true) {
    coEvery { getProperty("keel.titus.resolvers.environment.enabled", Boolean::class.java, true) } returns true
  }

  data class Fixture(val subject: EnvironmentVariablesResolver, val spec: TitusClusterSpec) {
    val resource = resource(
      kind = TITUS_CLUSTER_V1.kind,
      spec = spec
    )
    val resolved by lazy { subject(resource) }
  }

  fun tests() = rootContext<Fixture> {
    context("basic test") {
      fixture {
        Fixture(
          EnvironmentVariablesResolver(springEnv),
          baseSpec
        )
      }

      test("supports the resource kind") {
        expectThat(listOf(subject).supporting(resource))
          .containsExactly(subject)
      }

      context("spec has no defaults set") {
        test("env set") {
          val env = resolved.spec.defaults.env ?: mutableMapOf()
          expectThat(env.containsKey("SPINNAKER_ACCOUNT")).isTrue()
        }
      }
    }

    context("resource with env vars defined") {
      fixture {
        Fixture(
          EnvironmentVariablesResolver(springEnv),
          specWithEnv
        )
      }

      test("both env vars show up") {
        val env = resolved.spec.defaults.env ?: mutableMapOf()
        expectThat(env.containsKey("SPINNAKER_ACCOUNT")).isTrue()
        expectThat(env.containsKey("my-var")).isTrue()
      }
    }
  }

}
