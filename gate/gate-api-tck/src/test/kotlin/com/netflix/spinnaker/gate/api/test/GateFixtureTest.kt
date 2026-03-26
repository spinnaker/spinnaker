/*
 * Copyright 2020 Armory, Inc.
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
 */

package com.netflix.spinnaker.gate.api.test

import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource

class GateFixtureTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("a gate integration test environment") {
      gateFixture {
        Fixture()
      }

      test("service starts") { /* no-op */ }
    }
  }

  // Disable plugin caches that log during startup even though no plugins are configured:
  //   INFO [TaskScheduler-2] RemotePluginInfoReleaseCache : Cached 0 remote plugin configurations.
  //   INFO [TaskScheduler-1] DeckPluginCache : Cached 0 deck plugins
  @TestPropertySource(properties = [
    "spinnaker.extensibility.remote-plugins.enabled=false",
    "spinnaker.extensibility.deck-proxy.enabled=false"
  ])
  private inner class Fixture : GateFixture() {
    // Mock beans that attempt to connect to downstream services on startup.
    // Without these mocks the test logs connection errors like:
    //   ERROR [TaskScheduler-4] DownstreamServicesHealthIndicator : Exception received during
    //     health check of service: clouddriver, java.net.ConnectException: Failed to connect
    //     to localhost/[0:0:0:0:0:0:0:1]:7002 (url: http://localhost:7002/health)
    @MockBean
    lateinit var downstreamServicesHealthIndicator: DownstreamServicesHealthIndicator

    //   ERROR [pool-2-thread-2] ApplicationService : Falling back to application cache
    //     SpinnakerNetworkException: java.net.ConnectException: Failed to connect to
    //     localhost/[0:0:0:0:0:0:0:1]:7002
    @MockBean
    lateinit var applicationService: ApplicationService

    //   ERROR [TaskScheduler-2] DefaultProviderLookupService : Unable to refresh account
    //     details cache
    //     SpinnakerNetworkException: java.net.ConnectException: Failed to connect to
    //     localhost/[0:0:0:0:0:0:0:1]:7002
    @MockBean
    lateinit var defaultProviderLookupService: DefaultProviderLookupService
  }
}
