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
 *
 */

package com.netflix.spinnaker.front50.api.test

import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.StorageService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import strikt.api.expectThat
import strikt.assertions.isA

class Front50FixtureTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("a front50 integration test environment") {
      front50Fixture {
        Fixture()
      }

      test("service starts with SQL storage service") {
        expectThat(storageService).isA<SqlStorageService>()
      }
    }
  }

  private inner class Fixture : Front50Fixture() {

    @Autowired
    lateinit var storageService: StorageService
  }
}
