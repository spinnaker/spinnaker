/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.succeeded

class ConvertExampleFilesTest : JUnit5Minutests {
  private val mapper = configuredYamlMapper()

  fun tests() = rootContext<Unit> {

    context("cluster") {
      mapper.registerSubtypes(NamedType(ClusterSpec::class.java, "cluster"))
      val file = this.javaClass.getResource("/examples/cluster-example.yml").readText()

      test("yaml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<ClusterSpec>>(file)
        }.succeeded()
      }
    }

    context("security group") {
      mapper.registerSubtypes(NamedType(SecurityGroupSpec::class.java, "security-group"))
      val file = this.javaClass.getResource("/examples/security-group-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<SecurityGroupSpec>>(file)
        }.succeeded()
      }
    }

    context("image") {
      mapper.registerSubtypes(NamedType(ImageSpec::class.java, "image"))
      val file = this.javaClass.getResource("/examples/image-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<ImageSpec>>(file)
        }.succeeded()
      }
    }

    context("clb") {
      mapper.registerSubtypes(NamedType(ClassicLoadBalancer::class.java, "classic-load-balancer"))
      val file = this.javaClass.getResource("/examples/clb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<ClassicLoadBalancer>>(file)
        }.succeeded()
      }
    }

    context("alb") {
      mapper.registerSubtypes(NamedType(ApplicationLoadBalancer::class.java, "application-load-balancer"))
      val file = this.javaClass.getResource("/examples/alb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<ApplicationLoadBalancer>>(file)
        }.succeeded()
      }
    }
  }
}
