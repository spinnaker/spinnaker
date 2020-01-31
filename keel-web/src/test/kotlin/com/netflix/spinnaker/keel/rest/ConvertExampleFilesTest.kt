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
import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.titus.SPINNAKER_TITUS_API_V1
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.succeeded

class ConvertExampleFilesTest : JUnit5Minutests {
  private val mapper = configuredYamlMapper()

  fun tests() = rootContext<Unit> {

    context("ec2 cluster") {
      mapper.registerSubtypes(NamedType(ClusterSpec::class.java, "$SPINNAKER_EC2_API_V1/cluster"))
      val file = this.javaClass.getResource("/examples/cluster-example.yml").readText()

      test("yaml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<ClusterSpec>()
      }
    }

    context("ec2 cluster with scaling policies") {
      mapper.registerSubtypes(NamedType(ClusterSpec::class.java, "$SPINNAKER_EC2_API_V1/cluster"))
      val file = this.javaClass.getResource("/examples/ec2-cluster-with-autoscaling-example.yml").readText()

      test("yaml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<ClusterSpec>()
      }
    }

    context("security group") {
      mapper.registerSubtypes(NamedType(SecurityGroupSpec::class.java, "$SPINNAKER_EC2_API_V1/security-group"))
      val file = this.javaClass.getResource("/examples/security-group-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<SecurityGroupSpec>()
      }
    }

    context("image") {
      mapper.registerSubtypes(NamedType(ImageSpec::class.java, "${"bakery.$SPINNAKER_API_V1"}/image"))
      val file = this.javaClass.getResource("/examples/image-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<ImageSpec>()
      }
    }

    context("clb") {
      mapper.registerSubtypes(NamedType(ClassicLoadBalancerSpec::class.java, "$SPINNAKER_EC2_API_V1/classic-load-balancer"))
      val file = this.javaClass.getResource("/examples/clb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<ClassicLoadBalancerSpec>()
      }
    }

    context("alb") {
      mapper.registerSubtypes(NamedType(ApplicationLoadBalancerSpec::class.java, "$SPINNAKER_EC2_API_V1/application-load-balancer"))
      val file = this.javaClass.getResource("/examples/alb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<ApplicationLoadBalancerSpec>()
      }
    }

    context("titus cluster") {
      mapper.registerSubtypes(NamedType(TitusClusterSpec::class.java, "$SPINNAKER_TITUS_API_V1/cluster"))
      val file = this.javaClass.getResource("/examples/titus-cluster-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<TitusClusterSpec>>(file)
        }.succeeded()
      }
    }

    context("simple titus cluster") {
      mapper.registerSubtypes(NamedType(TitusClusterSpec::class.java, "cluster"))
      val file = this.javaClass.getResource("/examples/simple-titus-cluster-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<TitusClusterSpec>()
      }
    }

    context("titus cluster with artifact") {
      mapper.registerSubtypes(NamedType(TitusClusterSpec::class.java, "cluster"))
      val file = this.javaClass.getResource("/examples/titus-cluster-with-artifact-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedResource<*>>(file)
        }
          .succeeded()
          .get { spec }
          .isA<TitusClusterSpec>()
      }
    }
  }
}
