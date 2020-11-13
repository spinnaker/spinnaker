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

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.caffeine.TEST_CACHE_FACTORY
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.creationDate
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.containsKeys
import strikt.assertions.getValue
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.map
import java.time.Instant
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class ImageServiceTests : JUnit5Minutests {
  class Fixture {
    val cloudDriver = mockk<CloudDriverService>()
    val subject = ImageService(cloudDriver, TEST_CACHE_FACTORY)
    val artifact = DebianArtifact("my-package", vmOptions = VirtualMachineOptions(baseOs = "ignored", regions = emptySet()))
    
    val image1 = NamedImage(
      imageName = "my-package-0.0.1_rc.97-h98",
      attributes = mapOf(
        "virtualizationType" to "hvm",
        "creationDate" to "2018-10-25T13:08:58.000Z"
      ),
      tagsByImageId = mapOf(
        "ami-001" to mapOf(
          "build_host" to "https://jenkins/",
          "appversion" to "my-package-0.0.1~rc.97-h98.1c684a9/JENKINS-job/98",
          "creator" to "emburns@netflix.com",
          "base_ami_version" to "nflx-base-5.292.0-h988",
          "creation_time" to "2018-10-25 13:08:59 UTC"
        )
      ),
      accounts = setOf("test"),
      amis = mapOf(
        "us-west-1" to listOf("ami-001"),
        "ap-south-1" to listOf("ami-001")
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
        "us-west-1" to listOf("ami-002"),
        "ap-south-1" to listOf("ami-002")
      )
    )

    // image that is only in one region
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

    // mismatched app name (desired app name is a substring of this one)
    val image4 = NamedImage(
      imageName = "my-package-foo-0.0.1_rc.99-h100",
      attributes = mapOf(
        "virtualizationType" to "hvm",
        "creationDate" to "2019-08-28T13:09:54.000Z"
      ),
      tagsByImageId = mapOf(
        "ami-003" to mapOf(
          "build_host" to "https://jenkins/",
          "appversion" to "my-package-foo-0.0.1~rc.99-h100.8192e02/JENKINS-job/100",
          "creator" to "emburns@netflix.com",
          "base_ami_version" to "nflx-base-5.292.0-h988",
          "creation_time" to "2018-10-31 13:09:55 UTC"
        )
      ),
      accounts = setOf("test"),
      amis = mapOf(
        "us-west-1" to listOf("ami-004"),
        "ap-south-1" to listOf("ami-004")
      )
    )

    // image3 but in a different region
    val image5 = NamedImage(
      imageName = "my-package-0.0.1_rc.99-h100",
      attributes = mapOf(
        "virtualizationType" to "hvm",
        "creationDate" to "2018-11-01T13:09:54.000Z"
      ),
      tagsByImageId = mapOf(
        "ami-003" to mapOf(
          "build_host" to "https://jenkins/",
          "appversion" to "my-package-0.0.1~rc.99-h100.8192e02/JENKINS-job/100",
          "creator" to "emburns@netflix.com",
          "base_ami_version" to "nflx-base-5.292.0-h988",
          "creation_time" to "2018-10-31 13:24:55 UTC"
        )
      ),
      accounts = setOf("test"),
      amis = mapOf(
        "ap-south-1" to listOf("ami-005")
      )
    )

    // image1 but with a newer creation_time and base_ami_version than image3
    val image6 = NamedImage(
      imageName = "my-package-0.0.1_rc.97-h98",
      attributes = mapOf(
        "virtualizationType" to "hvm",
        "creationDate" to "2019-11-25T13:08:59.000Z"
      ),
      tagsByImageId = mapOf(
        "ami-006" to mapOf(
          "build_host" to "https://jenkins/",
          "appversion" to "my-package-0.0.1~rc.97-h98.1c684a9/JENKINS-job/98",
          "creator" to "emburns@netflix.com",
          "base_ami_version" to "nflx-base-5.293.0-h989",
          "creation_time" to "2019-11-25 13:08:59 UTC"
        )
      ),
      accounts = setOf("test"),
      amis = mapOf(
        "us-west-1" to listOf("ami-006"),
        "ap-south-1" to listOf("ami-006")
      )
    )

    // Excludes image4 which is for a different package
    val newestImage = listOf(image1, image2, image3, image5, image6)
      .sortedWith(NamedImageComparator)
      .first()
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("namedImages are in chronological order") {
      test("newestImage correctly calculated") {
        expectThat(newestImage.imageName).isEqualTo("my-package-0.0.1_rc.99-h100")
      }

      test("finds correct image") {
        val sortedImages = listOf(image2, image3, image1, image5, image6).sortedWith(NamedImageComparator)
        expectThat(sortedImages.first()) {
          get { imageName }.isEqualTo("my-package-0.0.1_rc.99-h100")
        }
      }

      test("images are sorted by appversion, then creation date") {
        val sortedImages = listOf(image2, image3, image1, image5, image6).sortedWith(NamedImageComparator)
        val first = sortedImages[0]
        val second = sortedImages[1]
        expectThat(first.imageName)
          .isEqualTo(second.imageName)
        expectThat(first.creationDate)
          .isEqualTo(Instant.parse("2018-11-01T13:09:54.000Z"))
        expectThat(second.creationDate)
          .isEqualTo(Instant.parse("2018-10-31T13:09:54.000Z"))
      }
    }

    context("given a list of images") {
      before {
        every {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package",
            account = "test"
          )
        } returns listOf(image2, image4, image3, image1, image5, image6)
      }

      test("latest image returns actual image") {
        runBlocking {
          val image = subject.getLatestImage(
            artifact = artifact,
            account = "test"
          )
          expectThat(image)
            .isNotNull()
            .get { appVersion }
            .isEqualTo(newestImage.appVersion)
        }
      }
    }

    context("given a list of images") {
      before {
        every {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package",
            account = "test"
          )
        } returns listOf(image2, image4, image3, image1, image5)
      }

      test("get latest named image returns actual latest image") {
        runBlocking {
          val image = subject.getLatestImage(
            artifact = artifact,
            account = "test"
          )
          expectThat(image)
            .isNotNull()
            .get { appVersion }
            .isEqualTo(newestImage.appVersion)
        }
      }
    }

    context("no image found for latest artifact") {
      before {
        every {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package",
            account = "test",
            region = null
          )
        } returns emptyList()
      }

      test("no image provided") {
        runBlocking {
          val image = subject.getLatestImage(
            artifact = artifact,
            account = "test"
          )
          expectThat(image)
            .isNull()
        }
      }
    }

    context("image regions are split across multiple actual AMIs") {
      before {
        every {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package",
            account = "test",
            region = null
          )
        } returns listOf(image3, image5)
      }

      test("result includes both regions") {
        val image = runBlocking {
          subject.getLatestImage(
            artifact = artifact,
            account = "test"
          )
        }

        expectThat(image)
          .isNotNull()
          .get {regions}
          .contains(image3.amis.keys)
          .contains(image5.amis.keys)
      }
    }

    context("given images in varying stages of completeness") {
      val appVersion = AppVersion.parseName("my-package-0.0.1~rc.97-h98.1c684a9")
      val regions = setOf("us-west-1", "ap-south-1")
      before {
        every {
          cloudDriver.namedImages(any(), any(), any())
        } returns listOf(image1, image6)
      }

      test("searching for a specific appversion finds latest complete image") {
        val images = runBlocking {
          subject.getLatestNamedImages(
            appVersion = appVersion,
            account = "test",
            regions = regions
          )
        }
        expectThat(images)
          .hasSize(2)
          .containsKeys(*regions.toTypedArray())
          .with(Map<*, NamedImage>::values) {
            all {
              get { imageName }.isEqualTo(image1.imageName)
            }
          }
      }

      test("the newest image is selected when searching in a single region") {
        val image = runBlocking {
          subject.getLatestNamedImage(appVersion, "test", regions.first())
        }

        expectThat(image)
          .isNotNull()
          .get { imageName }
          .isEqualTo(image1.imageName)
      }

      test("any other regions supported by the same image are subsequently served from cache") {
        val r1Image = runBlocking {
          subject.getLatestNamedImage(appVersion, "test", regions.first())
        }
        val r2Image = runBlocking {
          subject.getLatestNamedImage(appVersion, "test", regions.last())
        }

        expectThat(r1Image).isEqualTo(r2Image)

        verify(exactly = 1) {
          cloudDriver.namedImages(any(), any(), any())
        }
      }
    }

    context("images that were baked separately in the desired regions") {
      val appVersion = AppVersion.parseName("my-package-0.0.1~rc.99-h100.8192e02")
      val regions = setOf("us-west-1", "ap-south-1")
      before {
        every {
          cloudDriver.namedImages(any(), any(), any())
        } returns listOf(image3, image5)
      }

      test("searching for a specific appversion finds all images for required regions") {
        val images = runBlocking {
          subject.getLatestNamedImages(
            appVersion = appVersion,
            account = "test",
            regions = regions
          )
        }

        expectThat(images)
          .hasSize(2)
          .containsKeys(*regions.toTypedArray())
          .and {
            getValue("us-west-1").get { imageName }.isEqualTo(image3.imageName)
          }
          .and {
            getValue("ap-south-1").get { imageName }.isEqualTo(image5.imageName)
          }
      }
    }

    context("images that are not in all desired regions") {
      val appVersion = AppVersion.parseName("my-package-0.0.1~rc.99-h100.8192e02")
      val regions = setOf("us-west-1", "ap-south-1")
      before {
        every {
          cloudDriver.namedImages(any(), any(), any())
        } returns listOf(image3)
      }

      test("images that were found are returned") {
        val images = runBlocking {
          subject.getLatestNamedImages(
            appVersion = appVersion,
            account = "test",
            regions = regions
          )
        }

        expectThat(images)
          .hasSize(1)
          .get(Map<*, NamedImage>::values)
          .map { it.imageName }
          .containsExactly(image3.imageName)
      }
    }
  }
}
