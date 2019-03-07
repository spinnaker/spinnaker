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
package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

object ImageServiceTest {
  val cloudDriver = mockk<CloudDriverService>()
  val subject = ImageService(cloudDriver)

  val image1 = NamedImage(
    imageName = "my-package-0.0.1_rc.97-h98",
    attributes = mapOf(
      "virtualizationType" to "hvm",
      "creationDate" to "2018-10-25T13:08:58.000Z"
    ),
    tagsByImageId = mapOf(
      "ami-001" to mapOf(
        "build_host" to "https://jenkins/",
        "appversion" to "my-package-0.0.1~rc.97-h98/JENKINS-job/98",
        "creator" to "emburns@netflix.com",
        "base_ami_version" to "nflx-base-5.292.0-h988",
        "creation_time" to "2018-10-25 13:08:59 UTC"
      )
    ),
    accounts = setOf("test"),
    amis = mapOf(
      "us-west-1" to listOf("ami-001")
    )
  )

  val image2 = NamedImage(
    imageName = "my-package-0.0.1_rc.98-h99",
    attributes = mapOf(
      "virtualizationType" to "hvm",
      "creationDate" to "2018-10-28T13:09:13.000Z"
    ),
    tagsByImageId = mapOf(
      "ami-002" to mapOf(
        "build_host" to "https://jenkins/",
        "appversion" to "my-package-0.0.1~rc.98-h99.4cb755c/JENKINS-job/99",
        "creator" to "emburns@netflix.com",
        "base_ami_version" to "nflx-base-5.292.0-h988",
        "creation_time" to "2018-10-28 13:09:14 UTC"
      )
    ),
    accounts = setOf("test"),
    amis = mapOf(
      "us-west-1" to listOf("ami-002")
    )
  )

  val image3 = NamedImage(
    imageName = "my-package-0.0.1_rc.99-h100",
    attributes = mapOf(
      "virtualizationType" to "hvm",
      "creationDate" to "2018-10-31T13:09:54.000Z"
    ),
    tagsByImageId = mapOf(
      "ami-003" to mapOf(
        "build_host" to "https://jenkins/",
        "appversion" to "my-package-0.0.1~rc.99-h100.8192e02/JENKINS-job/100",
        "creator" to "emburns@netflix.com",
        "base_ami_version" to "nflx-base-5.292.0-h988",
        "creation_time" to "2018-10-31 13:09:55 UTC"
      )
    ),
    accounts = setOf("test"),
    amis = mapOf(
      "us-west-1" to listOf("ami-003")
    )
  )

  @Test
  fun `namedImages are in cronological order`() {
    val sortedImages = listOf(image2, image3, image1).sortedWith(NamedImageComparator)
    expectThat(sortedImages.last()) {
      get { imageName }.isEqualTo("my-package-0.0.1_rc.99-h100")
    }
  }

  @Test
  fun `get latest image returns actual latest image`() {
    coEvery {
      cloudDriver.namedImages("my-package-0.0.1_rc.98-h99", "test")
    } returns listOf(image2, image3, image1)

    runBlocking {
      val image = subject.getLatestImage("my-package", "my-package-0.0.1_rc.98-h99", "test")
      expectThat(image).isNotNull()
      expectThat(image?.appVersion).equals("my-package-0.0.1~rc.98-h99.4cb755c/JENKINS-job/99")
    }
  }

  @Test
  fun `get latest named image returns actual latest image`() {
    coEvery {
      cloudDriver.namedImages("my-package", "test")
    } returns listOf(image2, image3, image1)

    runBlocking {
      val image = subject.getLatestNamedImage("my-package", "test")
      expectThat(image).isNotNull()
      expectThat(image?.imageName).equals("my-package-0.0.1_rc.99-h100")
    }
  }

  @Test
  fun `no image provided if image not found for latest from artifact`() {
    coEvery {
      cloudDriver.namedImages("my-package", "test")
    } returns emptyList()

    runBlocking {
      val image = subject.getLatestNamedImage("my-package", "test")
      expectThat(image).isNull()
    }
  }

  @Test
  fun `get named image from jenkins info works`() {
    coEvery {
      cloudDriver.namedImages("my-package", "test")
    } returns listOf(image2, image3, image1)

    runBlocking {
      val image = subject.getNamedImageFromJenkinsInfo(
        packageName = "my-package",
        account = "test",
        buildHost = "https://jenkins/",
        buildName = "JENKINS-job",
        buildNumber = "98"
      )
      expectThat(image).isNotNull()
      expectThat(image?.imageName).equals("my-package-0.0.1_rc.97-h98")
    }
  }
}
