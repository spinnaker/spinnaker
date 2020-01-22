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

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.creationDate
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest.dynamicTest
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class ImageServiceTests : JUnit5Minutests {
  class Fixture {
    val cloudDriver = mockk<CloudDriverService>()
    val subject = ImageService(cloudDriver)

    val mapper = configuredObjectMapper()

    /*
    example payload for a package that has been baked 3 times
    the oldest was baked correctly
    the next is missing tags in one region
    the next is missing a region
    the most recent is the wrong package but has all tags and regions
     */
    val imageJson = this.javaClass.getResource("/named-images.json").readText()
    val realImages = mapper.readValue<List<NamedImage>>(imageJson)

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
          "appversion" to "my-package-0.0.1~rc.97-h98/JENKINS-job/98",
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
        coEvery {
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
            artifactName = "my-package",
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
        coEvery {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package",
            account = "test"
          )
        } returns listOf(image2, image4, image3, image1, image5)
      }
      test("get latest named image returns actual latest image") {
        runBlocking {
          val image = subject.getLatestNamedImage(
            packageName = "my-package",
            account = "test"
          )
          expectThat(image)
            .isNotNull()
            .get { imageName }
            .isEqualTo(newestImage.imageName)
        }
      }
    }

    context("get latest named image can be filtered by region") {
      mapOf("us-west-1" to "ami-003", "ap-south-1" to "ami-005").map { (region, imageId) ->
        dynamicTest("get latest image can be filtered by region ($region)") {
          before {
            coEvery {
              cloudDriver.namedImages(
                user = DEFAULT_SERVICE_ACCOUNT,
                imageName = "my-package",
                account = "test",
                region = region
              )
            } answers {
              listOf(image2, image4, image3, image1, image5).filter { it.amis.keys.contains(region) }
            }
          }

          test("it works") {
            runBlocking {
              val image = subject.getLatestNamedImage(
                packageName = "my-package",
                account = "test",
                region = region
              )
              expectThat(image)
                .isNotNull()
                .get { imageId }
                .isEqualTo(imageId)
            }
          }
        }
      }
    }

    context("no image found for latest artifact") {
      before {
        coEvery {
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
          val image = subject.getLatestNamedImage(
            packageName = "my-package",
            account = "test"
          )
          expectThat(image)
            .isNull()
        }
      }
    }

    context("searching for a very specific image version") {
      before {
        coEvery {
          cloudDriver.namedImages(any(), any(), any())
        } returns listOf(image2)
      }
      test("returns correct version") {
        runBlocking {
          val image = subject.getLatestNamedImage(
            appVersion = AppVersion.parseName("my-package-0.0.1~rc.98-h99.4cb755c"),
            account = "test"
          )
          expectThat(image)
            .isNotNull()
            .isEqualTo(image2)
        }

        coVerify {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = "my-package-0.0.1_rc.98-h99.4cb755c",
            account = "test"
          )
        }
      }
    }

    context("given jenkins info") {
      before {
        coEvery {
          cloudDriver.namedImages(DEFAULT_SERVICE_ACCOUNT, "my-package", "test")
        } returns listOf(image2, image4, image3, image1)
      }
      test("finds named image") {
        runBlocking {
          val image = subject.getNamedImageFromJenkinsInfo(
            packageName = "my-package",
            account = "test",
            buildHost = "https://jenkins/",
            buildName = "JENKINS-job",
            buildNumber = "98"
          )
          expectThat(image)
            .isNotNull()
            .get { imageName }
            .isEqualTo("my-package-0.0.1_rc.97-h98")
        }
      }
    }

    context("given images in varying stages of completeness") {
      val packageName = "keel"
      val regions = listOf("us-west-2", "us-east-1")
      before {
        coEvery {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = packageName,
            account = "test"
          )
        } returns realImages
      }
      test("searching by package name finds latest complete image") {
        runBlocking {
          val image = subject.getLatestNamedImageWithAllRegions(packageName, "test", regions)
          expectThat(image)
            .isNotNull()
            .get { imageName }
            .isEqualTo("keel-0.312.0-h240.44eaaa3-x86_64-20191025212812-xenial-hvm-sriov-ebs")
        }
      }
    }

    context("given images in varying stages of completeness") {
      val appVersion = "keel-0.312.0-h240.44eaaa3"
      val regions = listOf("us-west-2", "us-east-1")
      before {
        coEvery {
          cloudDriver.namedImages(
            user = DEFAULT_SERVICE_ACCOUNT,
            imageName = appVersion,
            account = "test"
          )
        } returns realImages
      }
      test("searching for a specific appversion finds latest complete image") {
        runBlocking {
          val image = subject.getLatestNamedImageWithAllRegionsForAppVersion(AppVersion.parseName(appVersion), "test", regions)
          expectThat(image)
            .isNotNull()
            .get { imageName }
            .isEqualTo("keel-0.312.0-h240.44eaaa3-x86_64-20191025212812-xenial-hvm-sriov-ebs")
        }
      }
    }
  }
}
