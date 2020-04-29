/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.kork.plugins.update.release.source

import com.netflix.spinnaker.kork.plugins.update.ServerGroupLocationResolver
import com.netflix.spinnaker.kork.plugins.update.ServerGroupNameResolver
import com.netflix.spinnaker.kork.plugins.update.internal.Front50Service
import com.netflix.spinnaker.kork.plugins.update.internal.PinnedVersions
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.pf4j.update.PluginInfo
import retrofit2.Call
import retrofit2.Response
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class Front50PluginInfoReleaseSourceTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("failure to resolve server group name is graceful") {
      every { serverGroupNameResolver.get() } returns null

      subject.processReleases(releases)

      expectThat(releases.map { it.props.version }).isEqualTo(listOf("1.0.0", "1.0.0"))
    }

    test("failure to resolve server group location is graceful") {
      every { serverGroupNameResolver.get() } returns "orca-v000"
      every { serverGroupLocationResolver.get() } returns null

      subject.processReleases(releases)

      expectThat(releases.map { it.props.version }).isEqualTo(listOf("1.0.0", "1.0.0"))
    }

    test("front50 request failing is graceful") {
      every { serverGroupNameResolver.get() } returns "orca-v000"
      every { serverGroupLocationResolver.get() } returns "us-west-2"

      val call: Call<PinnedVersions> = mockk(relaxed = true)
      every { front50Service.pinVersions(eq("orca-v000"), eq("orca"), eq("us-west-2"), any()) } returns call
      every { call.execute() } returns Response.error(500, ResponseBody.create(MediaType.get("application/json"), "{}"))

      subject.processReleases(releases)

      expectThat(releases.map { it.props.version }).isEqualTo(listOf("1.0.0", "1.0.0"))
    }

    test("returns versions defined by front50") {
      every { serverGroupNameResolver.get() } returns "orca-v000"
      every { serverGroupLocationResolver.get() } returns "us-west-2"

      val call: Call<PinnedVersions> = mockk(relaxed = true)
      every { front50Service.pinVersions(eq("orca-v000"), eq("orca"), eq("us-west-2"), any()) } returns call
      every { call.execute() } returns Response.success(mapOf(
        "foo" to PluginInfo.PluginRelease().apply { version = "1.0.0" },
        "bar" to PluginInfo.PluginRelease().apply { version = "1.0.1" }
      ))

      subject.processReleases(releases)

      expectThat(releases).and {
        get { size }.isEqualTo(2)
        get { first() }.and {
          get { pluginId }.isEqualTo("foo")
          get { props.version }.isEqualTo("1.0.0")
        }
        get { last() }.and {
          get { pluginId }.isEqualTo("bar")
          get { props.version }.isEqualTo("1.0.1")
        }
      }
    }
  }

  private inner class Fixture {
    val front50Service: Front50Service = mockk(relaxed = true)
    val serverGroupNameResolver: ServerGroupNameResolver = mockk(relaxed = true)
    val serverGroupLocationResolver: ServerGroupLocationResolver = mockk(relaxed = true)
    val subject = Front50PluginInfoReleaseSource(
      front50Service,
      serverGroupNameResolver,
      serverGroupLocationResolver,
      "orca"
    )

    val releases = setOf(
      PluginInfoRelease("foo", PluginInfo.PluginRelease().apply { version = "1.0.0" }),
      PluginInfoRelease("bar", PluginInfo.PluginRelease().apply { version = "1.0.0" })
    )
  }
}
