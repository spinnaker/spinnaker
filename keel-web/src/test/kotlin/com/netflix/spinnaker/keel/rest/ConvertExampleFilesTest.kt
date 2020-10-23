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
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLASSIC_LOAD_BALANCER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.test.configuredTestYamlMapper
import com.netflix.spinnaker.keel.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.titus.jackson.registerKeelTitusApiModule
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isSuccess

class ConvertExampleFilesTest : JUnit5Minutests {
  private val mapper = configuredTestYamlMapper()
    .registerKeelEc2ApiModule()
    .registerKeelTitusApiModule()

  fun tests() = rootContext<Unit> {
    before {
      @Suppress("DEPRECATION")
      mapper.registerSubtypes(EC2_CLUSTER_V1.toNamedType())
      mapper.registerSubtypes(EC2_CLUSTER_V1_1.toNamedType())
      mapper.registerSubtypes(EC2_SECURITY_GROUP_V1.toNamedType())
      mapper.registerSubtypes(EC2_CLASSIC_LOAD_BALANCER_V1.toNamedType())
      mapper.registerSubtypes(EC2_APPLICATION_LOAD_BALANCER_V1_1.toNamedType())
      mapper.registerSubtypes(TITUS_CLUSTER_V1.toNamedType())
    }

    context("ec2 cluster") {
      val file = this.javaClass.getResource("/examples/cluster-example.yml").readText()

      test("yaml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<ClusterV1Spec>()
      }
    }

    context("ec2 cluster with scaling policies") {
      val file = this.javaClass.getResource("/examples/ec2-cluster-with-autoscaling-example.yml").readText()

      test("yaml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<ClusterSpec>()
      }
    }

    context("security group") {
      val file = this.javaClass.getResource("/examples/security-group-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<SecurityGroupSpec>()
      }
    }

    context("clb") {
      val file = this.javaClass.getResource("/examples/clb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<ClassicLoadBalancerSpec>()
      }
    }

    context("alb") {
      val file = this.javaClass.getResource("/examples/alb-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<ApplicationLoadBalancerSpec>()
      }
    }

    context("titus cluster") {
      val file = this.javaClass.getResource("/examples/titus-cluster-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<TitusClusterSpec>()
      }
    }

    context("simple titus cluster") {
      val file = this.javaClass.getResource("/examples/simple-titus-cluster-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<TitusClusterSpec>()
      }
    }

    context("titus cluster with artifact") {
      val file = this.javaClass.getResource("/examples/titus-cluster-with-artifact-example.yml").readText()

      test("yml can be parsed") {
        expectCatching {
          mapper.readValue<SubmittedDeliveryConfig>(file)
        }
          .isSuccess()
          .get { environments.first().resources.first().spec }
          .isA<TitusClusterSpec>()
      }
    }
  }

  private fun SupportedKind<*>.toNamedType(): NamedType = NamedType(specClass, kind.toString())
}
