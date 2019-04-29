package com.netflix.spinnaker.keel.ami

import com.netflix.spinnaker.keel.bakery.api.BaseLabel.CANDIDATE
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.PREVIOUS
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.UNSTABLE
import com.netflix.spinnaker.keel.mahe.DynamicPropertyService
import com.netflix.spinnaker.keel.mahe.api.DynamicProperty
import com.netflix.spinnaker.keel.mahe.api.PropertyResponse
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class BaseImageCacheTests : JUnit5Minutests {
  internal class Fixture {
    val maheService = mockk<DynamicPropertyService>()
    val subject = BaseImageCache(maheService, configuredObjectMapper())
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("refreshing the cache") {
      before {
        every { maheService.getProperties("bakery") } returns CompletableDeferred(PropertyResponse(
          propertiesList = setOf(
            DynamicProperty(
              propertyId = "bakery.api.base_label_map|bakery|test||||",
              key = "bakery.api.base_label_map",
              value = """
                |{
                |  "trusty": {
                |    "release": "trustybase-x86_64-999999999999-ebs",
                |    "unstable": "trustybase-unstable-x86_64-999999999999-ebs",
                |    "candidate": "trustybase-x86_64-999999999999-ebs",
                |    "pre-candidate": "trustybase-x86_64-999999999999-ebs",
                |    "previous": "trustybase-x86_64-999999999999-ebs"
                |  },
                |  "bionic-classic": {
                |    "release": "bionic-classicbase-x86_64-201904041959-ebs",
                |    "unstable": "bionic-classicbase-unstable-x86_64-201904252133-ebs",
                |    "candidate": "bionic-classicbase-x86_64-201904232145-ebs",
                |    "pre-candidate": "bionic-classicbase-x86_64-201904232145-ebs",
                |    "previous": "bionic-classicbase-x86_64-201902202219-ebs"
                |  },
                |  "bionic": {
                |    "foundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162338-bionic-hvm-sriov-ebs",
                |    "candidate": "bionicbase-x86_64-201904232145-ebs",
                |    "pre-candidate": "bionicbase-x86_64-201904232145-ebs",
                |    "unstable": "bionicbase-unstable-x86_64-201904252133-ebs",
                |    "previousfoundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162338-bionic-hvm-sriov-ebs",
                |    "release": "bionicbase-x86_64-201904041959-ebs",
                |    "previous": "bionicbase-x86_64-201902202219-ebs",
                |    "unstablefoundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162338-bionic-hvm-sriov-ebs"
                |  },
                |  "xenial": {
                |    "foundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162339-xenial-pv-ebs",
                |    "candidate": "xenialbase-x86_64-201904232145-ebs",
                |    "upstream-foundation": "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-20160516.1",
                |    "pre-candidate": "xenialbase-x86_64-201904232145-ebs",
                |    "unstable": "xenialbase-unstable-x86_64-201904252133-ebs",
                |    "previousfoundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162339-xenial-pv-ebs",
                |    "release": "xenialbase-x86_64-201904041959-ebs",
                |    "previous": "xenialbase-x86_64-201902202219-ebs",
                |    "unstablefoundation": "nflx-foundation-1.37.0-h363.7985f1b-x86_64-20190419162339-xenial-pv-ebs"
                |  }
                |}""".trimMargin()
            )
          )
        ))

        runBlocking {
          subject.refresh()
        }
      }

      sequenceOf(
        Triple("bionic", RELEASE, "bionicbase-x86_64-201904041959-ebs"),
        Triple("bionic", CANDIDATE, "bionicbase-x86_64-201904232145-ebs"),
        Triple("bionic", UNSTABLE, "bionicbase-unstable-x86_64-201904252133-ebs"),
        Triple("bionic", PREVIOUS, "bionicbase-x86_64-201902202219-ebs"),
        Triple("xenial", RELEASE, "xenialbase-x86_64-201904041959-ebs"),
        Triple("xenial", CANDIDATE, "xenialbase-x86_64-201904232145-ebs"),
        Triple("xenial", UNSTABLE, "xenialbase-unstable-x86_64-201904252133-ebs"),
        Triple("xenial", PREVIOUS, "xenialbase-x86_64-201902202219-ebs")
      ).forEach { (os, label, image) ->
        test("returns correct base image details for $os $label") {
          expectThat(subject.getBaseImage(os, label))
            .isEqualTo(image)
        }
      }
    }
  }
}
