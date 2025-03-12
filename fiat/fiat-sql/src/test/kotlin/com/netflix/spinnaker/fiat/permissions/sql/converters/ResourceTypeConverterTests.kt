/*
 * Copyright 2021 Expedia, Inc.
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

package com.netflix.spinnaker.fiat.permissions.sql.converters

import com.netflix.spinnaker.fiat.model.resources.ResourceType
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNull
import java.lang.IllegalArgumentException

class ResourceTypeConverterTests : JUnit5Minutests {

    private val subject = ResourceTypeConverter()

    fun tests() = rootContext {

        context("ResourceTypeConverter") {
            setOf(ResourceType.ACCOUNT, ResourceType.APPLICATION, ResourceType.BUILD_SERVICE, ResourceType.ROLE, ResourceType.SERVICE_ACCOUNT).forEach { rt ->
                test("converts $rt to its name") {
                    expectThat(subject.to(rt)).isEqualTo(rt.name)
                }

                test("converts $rt from its name") {
                    expectThat(subject.from(rt.name)).isEqualTo(rt)
                }
            }

            test("passes null through") {
                expectThat(subject.from(null)).isNull()
                expectThat(subject.to(null)).isNull()
            }

            test("throws exception on empty string") {
                expectCatching { subject.from("") }
                    .isFailure()
                    .isA<IllegalArgumentException>()

                expectCatching { subject.to(ResourceType("")) }
                    .isFailure()
                    .isA<IllegalArgumentException>()
            }

            test("converts extension resource types") {
                val extensionResourceType = ResourceType("extension_resource")

                expectThat(subject.to(extensionResourceType)).isEqualTo(extensionResourceType.name.toLowerCase())
                expectThat(subject.from(extensionResourceType.name.toLowerCase())).isEqualTo(extensionResourceType)
            }
        }
    }

}