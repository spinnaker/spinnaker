/*
 * Copyright 2026 Moderne, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.azure

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

class AzureImageFinderSpec extends Specification {

  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)

  @Subject
  def azureImageFinder = new AzureImageFinder(objectMapper: objectMapper, oortService: oortService)

  // Wire shape that AzureVMImageLookupController#buildGalleryAzureNamedImage emits
  // for Shared Image Gallery results: stable imageName (= imageDefinitionName),
  // version segregated, URI carries the full gallery resource path.
  private static Map galleryImageWireShape(
      String region, String version, Map<String, String> tags,
      String imageDefinitionName = "moderne-arm64-noble") {
    [
        imageName: imageDefinitionName,
        version  : version,
        region   : region,
        uri      : "/subscriptions/sub/resourceGroups/rg/providers/" +
                   "Microsoft.Compute/galleries/moderne/images/" +
                   "${imageDefinitionName}/versions/${version}".toString(),
        tags     : tags,
    ]
  }

  // Wire shape for a managed image: timestamped imageName plus the literal
  // "na" sentinel that AzureVMImageLookupController#buildAzureNamedImage
  // stamps on the `version` field for managed entries (it also stamps "na"
  // on publisher/offer/sku but the finder only inspects version).
  private static Map managedImageWireShape(
      String region, String name, Map<String, String> tags) {
    [
        imageName: name,
        version  : "na",
        region   : region,
        uri      : "/subscriptions/sub/resourceGroups/rg/providers/" +
                   "Microsoft.Compute/images/${name}".toString(),
        tags     : tags,
    ]
  }

  def "compareVersions orders semver numerically, not lexicographically"() {
    expect:
    Integer.signum(AzureImageFinder.compareVersions(a, b)) == expected

    where:
    a            | b            | expected
    "1.10.0"     | "1.9.0"      | 1   // numeric: 10 > 9
    "1.9.0"      | "1.10.0"     | -1
    "2026.5.10"  | "2026.5.9"   | 1
    "1.0.0"      | "1.0.0"      | 0
    "1.0"        | "1.0.0"      | 0   // missing components treated as 0
    "1.0.1"      | "1.0"        | 1
    null         | "1.0.0"      | -1
    "1.0.0"      | null         | 1
    null         | null         | 0
    ""           | "1.0.0"      | -1
    "1.0.0-rc1"  | "1.0.0-rc2"  | -1  // non-numeric falls back to lex
  }

  def "AzureManagedImage compareTo prefers gallery over managed when both candidate in the same region"() {
    given: "a gallery image (has version) and a managed image (version='na' per the controller's wire shape)"
    def gallery = new AzureImageFinder.AzureManagedImage(
        imageName: "moderne-arm64-noble",  // 'a' > '1' on the next char
        version: "2026.5.1",  // intentionally OLDER than the managed bake
        region: "canadacentral")
    def managed = new AzureImageFinder.AzureManagedImage(
        imageName: "moderne-1746961200000-noble-arm64",
        version: "na",
        region: "canadacentral")

    expect: "gallery sorts first regardless of name lex or relative age"
    gallery.compareTo(managed) < 0
    managed.compareTo(gallery) > 0
  }

  def "AzureManagedImage compareTo prefers gallery even when managed lex-beats gallery on imageName"() {
    // Source preference must not rely on the imageName lex tiebreak. The
    // controller stamps version="na" on managed rows, so a naive "is version
    // non-empty?" discriminator misclassifies managed as gallery, falling
    // through to a reverse-alpha imageName compare. We pick names here where
    // that lex compare would pick the WRONG image -- the test then proves
    // source preference is doing the work, not luck.
    given: "names where managed sorts AFTER gallery alphabetically (lex would pick managed under reverse-alpha)"
    def gallery = new AzureImageFinder.AzureManagedImage(
        imageName: "bake-noble-arm64",  // 'b' < 'z' so gallery lex < managed lex
        version: "2026.5.1",
        region: "canadacentral")
    def managed = new AzureImageFinder.AzureManagedImage(
        imageName: "zone-1746961200000-noble-arm64",
        version: "na",  // production sentinel from AzureVMImageLookupController:496
        region: "canadacentral")

    expect: "gallery still wins on source preference"
    gallery.compareTo(managed) < 0
    managed.compareTo(gallery) > 0
  }

  def "AzureManagedImage compareTo tiebreaks on version when imageDefinitionName ties"() {
    given: "two gallery image versions with the same imageDefinitionName"
    def older = new AzureImageFinder.AzureManagedImage(
        imageName: "moderne-arm64-noble",
        version: "2026.5.9",
        region: "westus")
    def newer = new AzureImageFinder.AzureManagedImage(
        imageName: "moderne-arm64-noble",
        version: "2026.5.10",
        region: "westus")

    expect: "the newer version sorts first (compareTo < 0)"
    newer.compareTo(older) < 0
    older.compareTo(newer) > 0
  }

  def "dedup-per-region loop picks the highest-version gallery image"() {
    given: "three gallery image versions of the same definition, in cache-arrival order"
    def images = [
        new AzureImageFinder.AzureManagedImage(
            imageName: "moderne-arm64-noble", version: "2026.5.8", region: "westus"),
        new AzureImageFinder.AzureManagedImage(
            imageName: "moderne-arm64-noble", version: "2026.5.10", region: "westus"),
        new AzureImageFinder.AzureManagedImage(
            imageName: "moderne-arm64-noble", version: "2026.5.9", region: "westus"),
    ]

    when: "applying the same selection logic as AzureImageFinder#byTags"
    def sorted = images.toSorted()
    def latest = [:]
    sorted.each { image ->
      def existing = latest[image.region]
      if (existing == null || image.compareTo(existing) < 0) {
        latest[image.region] = image
      }
    }

    then: "the newest version wins regardless of cache arrival order"
    latest["westus"].version == "2026.5.10"
  }

  def "byTags picks the highest gallery version when cache returns versions out of order"() {
    given: "a deploy stage targeting westus"
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["westus"],
    ])
    def baseTags = [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"]

    when:
    def imageDetails = azureImageFinder.byTags(
        stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"],
        [])

    then: "finder asks for managed; gallery defaults to true on LookupOptions so both caches are searched"
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, [
        "tag:moderne_base"   : "true",
        "tag:moderne_base_os": "ubuntu-arm64-24.04",
        "managedImages"      : "true",
    ]) >> Calls.response([
        galleryImageWireShape("westus", "2026.5.8", baseTags),
        galleryImageWireShape("westus", "2026.5.10", baseTags),
        galleryImageWireShape("westus", "2026.5.9", baseTags),
    ])
    0 * _

    and: "byTags returns exactly the newest version's URI"
    imageDetails.size() == 1
    def selected = imageDetails.first()
    selected.region == "westus"
    selected.imageName == "moderne-arm64-noble"
    selected.imageId.endsWith("/versions/2026.5.10")
    selected.get("version") == "2026.5.10"
  }

  def "byTags filters out gallery images from regions the deploy doesn't target"() {
    given: "deploy targets westus; an extra gallery version lives in eastus"
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["westus"],
    ])
    def baseTags = [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"]

    when:
    def imageDetails = azureImageFinder.byTags(
        stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"],
        [])

    then: "clouddriver returns gallery images in both regions; only the targeted region survives"
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, _) >> Calls.response([
        galleryImageWireShape("eastus", "2026.5.10", baseTags),
        galleryImageWireShape("westus", "2026.5.10", baseTags),
    ])
    0 * _

    and:
    imageDetails.size() == 1
    imageDetails.first().region == "westus"
    imageDetails.first().imageId.endsWith("/versions/2026.5.10")
  }

  def "byTags picks the newest version per region when multiple regions are targeted"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["westus", "eastus"],
    ])
    def baseTags = [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"]

    when:
    def imageDetails = azureImageFinder.byTags(
        stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"],
        [])

    then:
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, _) >> Calls.response([
        galleryImageWireShape("westus", "2026.5.8", baseTags),
        galleryImageWireShape("westus", "2026.5.10", baseTags),
        galleryImageWireShape("eastus", "2026.5.7", baseTags),
        galleryImageWireShape("eastus", "2026.5.9", baseTags),
    ])
    0 * _

    and:
    imageDetails.size() == 2
    imageDetails.find { it.region == "westus" }.imageId.endsWith("/versions/2026.5.10")
    imageDetails.find { it.region == "eastus" }.imageId.endsWith("/versions/2026.5.9")
  }

  def "byTags returns null when clouddriver has no matching images"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["westus"],
    ])

    when:
    def imageDetails = azureImageFinder.byTags(stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"], [])

    then:
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, _) >> Calls.response([])
    0 * _

    and:
    imageDetails == null
  }

  def "byTags throws when regions are not specified"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: [],
    ])

    when:
    azureImageFinder.byTags(stage, "moderne", [moderne_base: "true"], [])

    then:
    thrown(IllegalArgumentException)
    0 * oortService._
  }

  def "byTags picks gallery over managed in the same region (the documented gap is closed)"() {
    // Mixed managed + gallery in canadacentral: bake produces the managed
    // image and the replication step publishes a gallery version pointing at
    // the same content. Before the comparator's source preference, gallery
    // would beat managed on lex (or lose, depending on names). Now gallery
    // wins unconditionally when both are candidates -- gallery is the
    // canonical deploy-time form.
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["canadacentral"],
    ])
    def baseTags = [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"]

    when:
    def imageDetails = azureImageFinder.byTags(stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"], [])

    then: "clouddriver returns both: a fresh managed image and an older gallery version"
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, _) >> Calls.response([
        managedImageWireShape("canadacentral",
            "moderne-1746961200000-noble-arm64", baseTags),
        galleryImageWireShape("canadacentral", "2026.5.1", baseTags),
    ])
    0 * _

    and: "gallery wins on source preference, not on lex or age"
    imageDetails.size() == 1
    imageDetails.first().imageName == "moderne-arm64-noble"
    imageDetails.first().get("version") == "2026.5.1"
    imageDetails.first().imageId.contains("/galleries/")
  }

  def "byTags omits version key for managed-only region (controller's 'na' sentinel is sanitized)"() {
    // Bake regions return only a managed image (the gallery replication hasn't
    // run yet). The controller stamps version='na' on managed entries; the
    // finder must not propagate that sentinel into the deploy stage's image
    // details, where downstream consumers expect either a real semver or no
    // version key at all (same convention used for uri/imageId on line 200).
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        account: "moderne-azure",
        regions: ["canadacentral"],
    ])
    def baseTags = [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"]

    when:
    def imageDetails = azureImageFinder.byTags(stage, "moderne",
        [moderne_base: "true", moderne_base_os: "ubuntu-arm64-24.04"], [])

    then: "clouddriver returns a managed-only result (bake region, pre-replication)"
    1 * oortService.findImage("azure", "moderne", "moderne-azure", null, _) >> Calls.response([
        managedImageWireShape("canadacentral",
            "moderne-1746961200000-noble-arm64", baseTags),
    ])
    0 * _

    and: "managed image is selected, but the 'na' sentinel is not exposed as a version"
    imageDetails.size() == 1
    imageDetails.first().imageName == "moderne-1746961200000-noble-arm64"
    imageDetails.first().get("version") == null
  }
}
