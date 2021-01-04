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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

@SpringBootTest(webEnvironment = NONE)
class DeliveryConfigYamlParsingTests @Autowired constructor(

  private val mapper: YAMLMapper

): JUnit5Minutests {

  fun tests() = rootContext {
    test("ec2 cluster") {
      parseSuccessfully("cluster-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<ClusterV1Spec>()
    }

    test("ec2 cluster with scaling policies") {
      parseSuccessfully("ec2-cluster-with-autoscaling-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<ClusterSpec>()
    }

    test("security group") {
      parseSuccessfully("security-group-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<SecurityGroupSpec>()
    }

    test("clb") {
      parseSuccessfully("clb-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<ClassicLoadBalancerSpec>()
    }

    test("alb") {
      parseSuccessfully("alb-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<ApplicationLoadBalancerSpec>()
    }

    test("titus cluster") {
      parseSuccessfully("titus-cluster-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<TitusClusterSpec>()
    }

    test("simple titus cluster") {
      parseSuccessfully("simple-titus-cluster-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<TitusClusterSpec>()
    }

    test("titus cluster with artifact") {
      parseSuccessfully("titus-cluster-with-artifact-example.yml")
        .get { environments.first().resources.first().spec }
        .isA<TitusClusterSpec>()
    }

    test("titus cluster with test container") {
      parseSuccessfully("titus-cluster-with-test-container.yml")
        .get { environments.first().verifyWith.first() }
        .isEqualTo(
          TestContainerVerification(
            repository = "acme/widget",
            tag = "stable",
            location = Location(account = "test", region = "us-east-1")
          )
        )
    }
  }

  /**
   * Given a [fileName] with a name of a delivery config file in the resources/examples directory,
   * attempt to deserialize into a [SubmittedDeliveryConfig] object and assert that the deserialization
   * was successful
   */
  fun parseSuccessfully(fileName: String) =
    expectCatching {
      val text = this.javaClass.getResource("/examples/$fileName").readText()

      mapper.readValue<SubmittedDeliveryConfig>(text)
    }.isSuccess()
}
